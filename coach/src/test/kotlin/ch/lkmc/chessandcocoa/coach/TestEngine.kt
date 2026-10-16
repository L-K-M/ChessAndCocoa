// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach

import ch.lkmc.chessandcocoa.chess.Game
import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.coach.analysis.EngineAnalyst
import ch.lkmc.chessandcocoa.coach.analysis.Searcher
import ch.lkmc.chessandcocoa.engine.ProcessUciTransport
import ch.lkmc.chessandcocoa.engine.UciEngine
import org.junit.Assume.assumeTrue
import java.io.File

/**
 * One host Stockfish shared by the engine-backed tests (built by
 * scripts/build-stockfish-host.sh). Tests that need it skip when it is absent.
 */
object TestEngine {

    private val engine: UciEngine? by lazy {
        val binary = File(System.getProperty("stockfish.path") ?: "")
        if (!binary.canExecute()) return@lazy null
        UciEngine(ProcessUciTransport(binary)).apply {
            start()
            Runtime.getRuntime().addShutdownHook(Thread { close() })
        }
    }

    private val analyst by lazy { engine?.let { EngineAnalyst(it) } }

    /** A searcher over the shared engine; skips the calling test when there is none. */
    fun searcher(): Searcher {
        val shared = analyst
        assumeTrue("host Stockfish not built", shared != null)
        return Searcher(shared!!)
    }
}

/** Plays SAN moves from the start position. */
fun gameOf(vararg sans: String): Game = sans.fold(Game.new()) { game, san -> game.play(game.current.parseSan(san)) }

fun Game.move(san: String): Move = current.parseSan(san)
