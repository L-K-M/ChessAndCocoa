// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach.analysis

import ch.lkmc.chessandcocoa.chess.Game
import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.engine.AnalysisRequest
import ch.lkmc.chessandcocoa.engine.AnalysisResult
import ch.lkmc.chessandcocoa.engine.CandidateLine
import ch.lkmc.chessandcocoa.engine.EngineProfile
import ch.lkmc.chessandcocoa.engine.Score
import ch.lkmc.chessandcocoa.engine.SearchLimit
import kotlinx.serialization.Serializable

/**
 * Node budgets per search role. Starting hypotheses for benchmarking
 * (concept Section 14.2), not validated targets.
 *
 * The analyst budget belongs to the independent checker and never changes
 * with difficulty (R9); the opponent's selection budget lives in its
 * difficulty profile instead.
 */
@Serializable
data class AnalystBudgets(
    /** Root analysis of the human's decision: quality of the played move. */
    val assessNodes: Long = 250_000,
    /** Plan-policy searches, live and in probes alike (policy identity). */
    val policyNodes: Long = 50_000,
    /** The opponent-reply search inside a plan probe. */
    val probeReplyNodes: Long = 70_000,
    /** Candidate moves compared in an assessment. */
    val assessMultiPv: Int = 3,
    /** Strong replies tested per plan probe, before the template challenge. */
    val probeReplies: Int = 3,
    /**
     * A probe reply within this of the best reply is "strong" and may refute
     * a plan. Fixed, so a gentler profile never checks more replies than a
     * keener one (concept Section 9.4).
     */
    val strongReplyCp: Int = 50,
)

/** A search about a [Game] position, with the side to move recorded for score normalization. */
data class GameSearch(val game: Game, val result: AnalysisResult) {
    val rootSide: Side get() = game.current.sideToMove

    /** The line's score from [side]'s point of view. */
    fun score(line: CandidateLine, side: Side): Score = line.score.forSide(rootSide, side)

    fun bestScore(side: Side): Score? = result.best?.let { score(it, side) }

    fun lineFor(move: Move): CandidateLine? = result.lineFor(move.uci)
}

/** Builds and runs searches about game positions, always with full history. */
class Searcher(private val analyst: Analyst, private val profile: EngineProfile = EngineProfile()) {

    val searchCount: Int get() = analyst.searchCount
    val nodeCount: Long get() = analyst.nodeCount

    suspend fun search(
        game: Game,
        nodes: Long,
        multiPv: Int = 1,
        searchMoves: List<Move> = emptyList(),
    ): GameSearch {
        val request = AnalysisRequest(
            initialFen = game.initialFen,
            moves = game.uciMoves(),
            limit = SearchLimit(nodes = nodes),
            multiPv = multiPv.coerceAtLeast(1),
            searchMoves = searchMoves.map { it.uci }.sorted(),
            profile = profile,
        )
        return GameSearch(game, analyst.analyze(request))
    }

    val engineProfile: EngineProfile get() = profile
}

/** Converts a score seen by [rootSide] into [side]'s point of view. */
fun Score.forSide(rootSide: Side, side: Side): Score = if (rootSide == side) this else negate()
