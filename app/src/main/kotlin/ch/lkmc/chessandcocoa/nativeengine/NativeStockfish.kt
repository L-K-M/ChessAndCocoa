// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.nativeengine

/**
 * JNI front door to the Stockfish build bundled as `libcocoafish.so`.
 *
 * The native side runs Stockfish's UCI loop on a dedicated thread with
 * std::cin/std::cout redirected onto pipes owned by the library. Because the
 * redirection is process-global and one-shot, at most one engine can run per
 * process lifetime: [nativeStart] may return true once, ever.
 *
 * Only [NativeStockfishTransport] may call this object.
 */
internal object NativeStockfish {

    init {
        System.loadLibrary("cocoafish")
    }

    /** Starts the engine thread. False when an engine already ran in this process. */
    external fun nativeStart(): Boolean

    /** Queues one command line for the engine's stdin; the newline is appended natively. */
    external fun nativeSend(line: String)

    /** Blocks for the next engine output line; null after the engine exited and output drained. */
    external fun nativeReadLine(): String?

    /** Closes stdin (a quit signal), waits for the engine thread, and reaps the pipes. */
    external fun nativeJoin()
}
