// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.session

import ch.lkmc.chessandcocoa.chess.Game
import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.coach.analysis.Analyst
import ch.lkmc.chessandcocoa.engine.AnalysisRequest
import ch.lkmc.chessandcocoa.engine.AnalysisResult
import ch.lkmc.chessandcocoa.engine.CandidateLine
import ch.lkmc.chessandcocoa.engine.EngineException
import ch.lkmc.chessandcocoa.engine.Score
import ch.lkmc.chessandcocoa.engine.ScoreBound
import kotlinx.coroutines.CompletableDeferred

/**
 * An engine-free analyst for session tests: every legal (or searchmoves)
 * candidate scores 0, in move order. [gate] lets a test hold searches open to
 * reproduce slow analysis and stale results.
 */
class FakeAnalyst : Analyst {
    @Volatile
    var gate: CompletableDeferred<Unit>? = null

    /** The next this many searches fail, as an engine that stopped working would. */
    @Volatile
    var failures = 0

    override var searchCount = 0
        private set
    override var nodeCount = 0L
        private set

    override suspend fun analyze(request: AnalysisRequest): AnalysisResult {
        gate?.await()
        if (failures > 0) {
            failures--
            throw EngineException("fake engine failure")
        }
        searchCount++
        val game = Game.fromMoves(request.initialFen, request.moves.map(Move::parseUci))
        val legal = game.current.legalMoves().map { it.uci }
        val candidates = if (request.searchMoves.isEmpty()) legal else legal.filter { it in request.searchMoves }
        val lines = candidates.take(request.multiPv).mapIndexed { index, move ->
            CandidateLine(index + 1, move, Score.Centipawns(0), ScoreBound.EXACT, depth = 1, nodes = 1, pv = listOf(move))
        }
        return AnalysisResult(request, "fake", lines, lines.firstOrNull()?.move, complete = true, nodes = 1)
    }
}
