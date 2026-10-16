// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.nativeengine

import ch.lkmc.chessandcocoa.engine.UciTransport

/**
 * [UciTransport] backed by Stockfish running inside this process; the
 * Android replacement for the harness's `ProcessUciTransport`.
 *
 * The native engine supports a single engine per process (it owns the
 * process-wide std::cin/std::cout), so constructing a second transport while
 * one runs — or after one ran — throws [IllegalStateException].
 */
class NativeStockfishTransport : UciTransport {

    init {
        if (!NativeStockfish.nativeStart()) {
            throw IllegalStateException(
                "Stockfish already ran in this process; at most one in-process engine is supported",
            )
        }
    }

    override fun send(line: String) = NativeStockfish.nativeSend(line)

    override fun readLine(): String? = NativeStockfish.nativeReadLine()

    override fun close() {
        // "quit" ends the UCI loop; the engine's stdout then reaches EOF,
        // which is what makes pending readLine calls return null.
        runCatching { send("quit") }
        NativeStockfish.nativeJoin()
    }
}
