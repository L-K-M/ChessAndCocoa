// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.engine

import org.junit.AfterClass
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Runs against the host Stockfish from scripts/build-stockfish-host.sh; skipped when it is absent. */
class UciEngineTest {

    @Test
    fun nodeLimitedSingleThreadSearchIsReproducible() {
        val request = request(nodes = 50_000, multiPv = 3)
        val first = engine.analyze(request)
        val second = engine.analyze(request)
        assertTrue(first.complete)
        assertEquals(first.lines, second.lines)
        assertEquals(3, first.lines.size)
    }

    @Test
    fun searchMovesRestrictsTheRoot() {
        val result = engine.analyze(request(nodes = 20_000, searchMoves = listOf("a2a3", "h2h3")))
        assertTrue(result.bestMove in setOf("a2a3", "h2h3"))
    }

    @Test
    fun reportsMateFromRootSideToMove() {
        // Scholar's fixture (concept Section 13.2) after 3...Nf6: White mates with Qxf7#.
        val result = engine.analyze(
            AnalysisRequest(
                initialFen = START,
                moves = listOf("e2e4", "e7e5", "f1c4", "b8c6", "d1h5", "g8f6"),
                limit = SearchLimit(nodes = 20_000),
            ),
        )
        assertEquals("h5f7", result.bestMove)
        assertEquals(Score.Mate(1), result.best?.score)
    }

    @Test
    fun cancellationStopsAndMarksIncomplete() {
        var polls = 0
        val result = engine.analyze(request(nodes = 50_000_000)) { ++polls > 5 }
        assertFalse(result.complete)
    }

    private fun request(nodes: Long, multiPv: Int = 1, searchMoves: List<String> = emptyList()) =
        AnalysisRequest(START, listOf("e2e4", "e7e5"), SearchLimit(nodes = nodes), multiPv, searchMoves)

    companion object {
        private const val START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        private lateinit var engine: UciEngine

        @BeforeClass
        @JvmStatic
        fun startEngine() {
            val binary = File(System.getProperty("stockfish.path") ?: "")
            assumeTrue("host Stockfish not built", binary.canExecute())
            engine = UciEngine(ProcessUciTransport(binary)).apply { start() }
        }

        @AfterClass
        @JvmStatic
        fun stopEngine() {
            if (::engine.isInitialized) engine.close()
        }
    }
}
