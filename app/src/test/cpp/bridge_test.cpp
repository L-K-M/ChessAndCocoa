// SPDX-License-Identifier: GPL-3.0-or-later
//
// Host-side smoke test for the streambuf bridge in
// app/src/main/cpp/stockfish_jni.cpp. It compiles the bridge with
// COCOAFISH_NO_JNI (dropping the jni.h dependency), runs engineMain — the
// same function the JNI layer starts — on a thread, and drives the real
// UCIEngine over the same POSIX pipes: uci/uciok, isready/readyok, a bounded
// search to bestmove, then quit to EOF. This proves everything in the bridge
// except the JNI string plumbing itself.

#include <cerrno>
#include <csignal>
#include <cstdio>
#include <cstring>
#include <string>
#include <thread>
#include <unistd.h>

#define COCOAFISH_NO_JNI
#include "../../main/cpp/stockfish_jni.cpp"

namespace {

// Blocks for one '\n'-terminated line like nativeReadLine does; returns
// false at EOF once the engine closed its stdout end.
bool nextLine(int fd, std::string& pending, std::string& line) {
    char buf[4096];
    for (;;)
    {
        const size_t nl = pending.find('\n');
        if (nl != std::string::npos)
        {
            line = pending.substr(0, nl);
            pending.erase(0, nl + 1);
            return true;
        }
        const ssize_t n = ::read(fd, buf, sizeof(buf));
        if (n > 0)
        {
            pending.append(buf, static_cast<size_t>(n));
            continue;
        }
        if (n < 0 && errno == EINTR)
            continue;
        line = pending;
        pending.clear();
        return !line.empty();
    }
}

void sendLine(int fd, const char* s) {
    const std::string line = std::string(s) + "\n";
    if (::write(fd, line.data(), line.size()) != static_cast<ssize_t>(line.size()))
    {
        std::fprintf(stderr, "FAIL: write(%s) -> %s\n", s, std::strerror(errno));
        _exit(2);
    }
}

// Reads until a line satisfies pred; false when EOF comes first.
template<typename Pred>
bool readUntil(int fd, std::string& pending, Pred pred) {
    std::string line;
    while (nextLine(fd, pending, line))
        if (pred(line))
            return true;
    return false;
}

int fail(const char* what) {
    std::fprintf(stderr, "FAIL: %s\n", what);
    return 1;
}

}  // namespace

int main() {
    std::signal(SIGPIPE, SIG_IGN);

    int cmdPipe[2], outPipe[2];
    if (::pipe(cmdPipe) != 0 || ::pipe(outPipe) != 0)
        return fail("pipe()");

    std::thread engine(engineMain, cmdPipe[0], outPipe[1]);
    std::string pending;

    sendLine(cmdPipe[1], "uci");
    if (!readUntil(outPipe[0], pending, [](const std::string& l) { return l == "uciok"; }))
        return fail("no uciok from engine");

    sendLine(cmdPipe[1], "isready");
    if (!readUntil(outPipe[0], pending, [](const std::string& l) { return l == "readyok"; }))
        return fail("no readyok from engine");

    sendLine(cmdPipe[1], "position startpos");
    sendLine(cmdPipe[1], "go depth 5");
    if (!readUntil(outPipe[0], pending,
                   [](const std::string& l) { return l.rfind("bestmove ", 0) == 0; }))
        return fail("no bestmove from engine");

    sendLine(cmdPipe[1], "quit");
    // After quit the engine must close stdout: drain everything to EOF.
    std::string line;
    while (nextLine(outPipe[0], pending, line))
        ;
    engine.join();
    if (!g.exited)
        return fail("engine thread did not exit");

    ::close(cmdPipe[1]);
    ::close(outPipe[0]);
    std::puts("PASS: uciok, readyok, bestmove, clean EOF after quit");
    return 0;
}
