// SPDX-License-Identifier: GPL-3.0-or-later
//
// JNI bridge running Stockfish's UCI loop in-process for
// ch.lkmc.chessandcocoa.nativeengine.NativeStockfish.
//
// The engine reads commands from std::cin and writes UCI output to
// std::cout. This file installs custom std::streambuf objects on both (see
// upstream's own Logger in misc.cpp for the same rdbuf-swap trick), each
// backed by one end of a POSIX pipe:
//
//   Kotlin ──nativeSend──▶ cmdPipe ──std::cin──▶ engine thread (UCI loop)
//   Kotlin ◀─nativeReadLine─ outPipe ◀─std::cout─ engine + search threads
//
// readLine() blocks and returns null once the engine thread has exited and
// every buffered byte was drained — the engine closing its stdout end is
// what makes that happen after "quit".
//
// Constraint: ONE engine per process. Stockfish's loop owns the global
// std::cin/std::cout and the stream redirection is installed once, for the
// process's lifetime; a second engine could not get its own channel. The
// streambufs are likewise leaked intentionally at the end: other engine
// threads may still be finishing output while the UCI thread tears down.
//
// Threading: one writer thread (nativeSend) and one reader thread
// (nativeReadLine) plus the engine thread itself — matching UciEngine's
// single-reader discipline.
//
// COCOAFISH_NO_JNI hides the jni.h include and the JNI entry points so the
// bridge half (streambufs, EngineState, engineMain) can be exercised by the
// host-side test in app/src/test/cpp/ without a JNI toolchain.

#ifndef COCOAFISH_NO_JNI
#include <jni.h>
#endif

#include <atomic>
#include <cerrno>
#include <csignal>
#include <cstring>
#include <mutex>
#include <pthread.h>
#include <streambuf>
#include <string>
#include <thread>
#include <unistd.h>
#include <utility>

#include "attacks.h"
#include "misc.h"
#include "position.h"
#include "tune.h"
#include "uci.h"

namespace {

constexpr size_t kBufSize = 4096;

// std::cin replacement source: underflow() blocks in read(2) on the command
// pipe, so the UCI loop's getline() waits for nativeSend like a real stdin.
class PipeInBuf : public std::streambuf {
   public:
    explicit PipeInBuf(int fd) : fd_(fd) { setg(buf_, buf_, buf_); }

   protected:
    int underflow() override {
        if (gptr() < egptr())
            return traits_type::to_int_type(*gptr());
        for (;;)
        {
            const ssize_t n = ::read(fd_, buf_, sizeof(buf_));
            if (n > 0)
            {
                setg(buf_, buf_, buf_ + n);
                return traits_type::to_int_type(*gptr());
            }
            if (n == 0 || errno != EINTR)
                return traits_type::eof();  // EOF -> engine treats as "quit"
        }
    }

   private:
    const int fd_;
    char      buf_[kBufSize];
};

// std::cout replacement sink: keeps a put area so each sync_endl flush
// becomes one write(2) to the output pipe instead of one per character.
class PipeOutBuf : public std::streambuf {
   public:
    explicit PipeOutBuf(int fd) : fd_(fd) { setp(buf_, buf_ + sizeof(buf_) - 1); }
    ~PipeOutBuf() override { sync(); }

   protected:
    int overflow(int c) override {
        if (sync() != 0)
            return traits_type::eof();
        if (c != traits_type::eof())
        {
            *pptr() = traits_type::to_char_type(c);
            pbump(1);
        }
        return c;
    }

    int sync() override {
        for (char* p = pbase(); p < pptr();)
        {
            const ssize_t n = ::write(fd_, p, static_cast<size_t>(pptr() - p));
            if (n > 0)
            {
                p += n;
                continue;
            }
            if (n < 0 && errno == EINTR)
                continue;
            break;  // hard error (reader gone): drop the pending bytes
        }
        setp(buf_, buf_ + sizeof(buf_) - 1);
        return 0;
    }

   private:
    const int fd_;
    char      buf_[kBufSize];
};

struct EngineState {
    std::thread      thread;
    std::atomic<int> cmdWrite{-1};   // our end of cmdPipe (engine's stdin)
    std::atomic<int> outRead{-1};    // our end of outPipe (engine's stdout)
    std::atomic<bool> started{false};
    std::atomic<bool> exited{false};
    std::mutex       sendMu;         // serialize nativeSend writes
    std::string      pending;        // unterminated stdout tail, readMu-protected
    std::mutex       readMu;
};

EngineState g;

// Mirrors upstream main.cpp minus the version banner: init tables, build
// the UCI engine on argc=1 (pure interactive mode), tune, run the loop.
void engineMain(int cmdRead, int outWrite) {
#if defined(__ANDROID__)
    pthread_setname_np(pthread_self(), "stockfish-uci");
#endif
    PipeInBuf  inBuf(cmdRead);
    PipeOutBuf outBuf(outWrite);
    std::cin.rdbuf(&inBuf);
    std::cout.rdbuf(&outBuf);

    char  arg0[] = "cocoafish";
    char* argv[] = {arg0};

    Stockfish::Attacks::init();
    Stockfish::Position::init();
    Stockfish::CommandLine cli(1, argv);
    Stockfish::UCIEngine   uci(std::move(cli));
    Stockfish::Tune::init(uci.engine_options());
    uci.loop();

    std::cout.flush();
    // Closing the stdout end first guarantees blocked readers see EOF after
    // draining; the stdin end goes too so nothing reads stale commands.
    ::close(outWrite);
    ::close(cmdRead);
    g.exited = true;
}

}  // namespace

#ifndef COCOAFISH_NO_JNI

// Engine output always ends with '\n', but if the stream dies mid-line the
// last bytes are still delivered once before null.
static jstring drainTail(JNIEnv* env) {
    if (g.pending.empty())
        return nullptr;
    const std::string tail = std::move(g.pending);
    g.pending.clear();
    return env->NewStringUTF(tail.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_ch_lkmc_chessandcocoa_nativeengine_NativeStockfish_nativeStart(JNIEnv*, jobject) {
    bool expected = false;
    if (!g.started.compare_exchange_strong(expected, true))
        return JNI_FALSE;  // one engine per process, ever

    int cmdPipe[2] = {-1, -1}, outPipe[2];
    if (::pipe(cmdPipe) != 0 || ::pipe(outPipe) != 0)
    {
        // Only close the ends that were actually opened.
        if (cmdPipe[0] >= 0)
        {
            ::close(cmdPipe[0]);
            ::close(cmdPipe[1]);
        }
        g.started = false;
        return JNI_FALSE;
    }

    // Writing to the pipe after the engine is gone must yield EPIPE, not a
    // SIGPIPE that kills the app process.
    std::signal(SIGPIPE, SIG_IGN);

    g.cmdWrite = cmdPipe[1];
    g.outRead  = outPipe[0];
    g.thread   = std::thread(engineMain, cmdPipe[0], outPipe[1]);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL Java_ch_lkmc_chessandcocoa_nativeengine_NativeStockfish_nativeSend(
  JNIEnv* env, jobject, jstring jline) {
    if (jline == nullptr)
        return;

    const char* chars = env->GetStringUTFChars(jline, nullptr);
    if (chars == nullptr)
        return;
    std::string line(chars, static_cast<size_t>(env->GetStringUTFLength(jline)));
    env->ReleaseStringUTFChars(jline, chars);
    line.push_back('\n');

    std::lock_guard<std::mutex> lock(g.sendMu);
    const int                   fd = g.cmdWrite.load();
    if (fd < 0)
        return;  // already joined

    for (const char* p = line.data(); p < line.data() + line.size();)
    {
        const ssize_t n = ::write(fd, p, static_cast<size_t>(line.data() + line.size() - p));
        if (n > 0)
        {
            p += n;
            continue;
        }
        if (n < 0 && errno == EINTR)
            continue;
        break;  // EPIPE/EBADF: engine already exited, drop the command
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_ch_lkmc_chessandcocoa_nativeengine_NativeStockfish_nativeReadLine(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(g.readMu);
    char                        buf[kBufSize];
    for (;;)
    {
        const size_t nl = g.pending.find('\n');
        if (nl != std::string::npos)
        {
            std::string line = g.pending.substr(0, nl);
            g.pending.erase(0, nl + 1);
            if (!line.empty() && line.back() == '\r')
                line.pop_back();
            return env->NewStringUTF(line.c_str());
        }

        const int fd = g.outRead.load();
        if (fd < 0)
            return drainTail(env);  // joined already

        const ssize_t n = ::read(fd, buf, sizeof(buf));
        if (n > 0)
        {
            g.pending.append(buf, static_cast<size_t>(n));
            continue;
        }
        if (n == 0 || errno != EINTR)
        {
            // EOF (engine closed stdout) or a dead fd: claim the fd before
            // closing so nativeJoin can't close the same number twice.
            if (g.outRead.exchange(-1) == fd)
                ::close(fd);
            return drainTail(env);
        }
    }
}

// nativeJoin must only run after "quit" (or stdin EOF) was delivered; it
// closes our stdin end as a belt-and-braces quit signal, waits for the
// engine thread, then reaps the pipe fds. The mutexes keep the closes from
// racing an in-flight write/read on the same fd number (an fd closed while
// another thread uses it can be reassigned to a different file). The stream
// redirection itself stays: it is process-global and this process gets one
// engine lifetime.
extern "C" JNIEXPORT void JNICALL
Java_ch_lkmc_chessandcocoa_nativeengine_NativeStockfish_nativeJoin(JNIEnv*, jobject) {
    {
        std::lock_guard<std::mutex> lock(g.sendMu);
        const int                   cmdFd = g.cmdWrite.exchange(-1);
        if (cmdFd >= 0)
            ::close(cmdFd);  // engine sees stdin EOF -> treats it as "quit"
    }

    if (g.thread.joinable())
        g.thread.join();

    {
        // After engine exit its write end is closed, so a reader's read()
        // can only be draining to EOF — it never blocks here for long.
        std::lock_guard<std::mutex> lock(g.readMu);
        const int                   outFd = g.outRead.exchange(-1);
        if (outFd >= 0)
            ::close(outFd);
    }
    g.exited = true;
}

#endif  // COCOAFISH_NO_JNI
