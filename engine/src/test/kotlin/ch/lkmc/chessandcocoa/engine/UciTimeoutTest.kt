// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.engine

import java.util.concurrent.LinkedBlockingQueue
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** An engine that stops answering must surface as an error, never as a wait that never ends. */
class UciTimeoutTest {

    @Test
    fun aSilentEngineFailsTheHandshake() {
        val engine = UciEngine(SilentTransport(answers = emptyMap()), SHORT)
        assertFailsWith<EngineException> { engine.start() }
        engine.close()
    }

    @Test
    fun anEngineThatIgnoresStopFailsTheSearch() {
        val engine = UciEngine(SilentTransport(answers = mapOf("uci" to "uciok", "isready" to "readyok")), SHORT)
        engine.start()
        val request = AnalysisRequest(START, emptyList(), SearchLimit(nodes = 1_000))
        assertFailsWith<EngineException> { engine.analyze(request) { true } }
        engine.close()
    }

    /** Answers only the listed commands; everything else, including "go" and "stop", gets silence. */
    private class SilentTransport(private val answers: Map<String, String>) : UciTransport {
        private val output = LinkedBlockingQueue<String>()

        override fun send(line: String) {
            answers[line]?.let(output::put)
        }

        override fun readLine(): String? = output.take().takeUnless { it == EOF }

        override fun close() = output.put(EOF)
    }

    private companion object {
        const val START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        const val EOF = "\u0000"
        val SHORT = UciTimeouts(responseMillis = 200, stopMillis = 200)
    }
}
