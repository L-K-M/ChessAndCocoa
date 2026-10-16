// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * An engine score from the point of view of the side to move at the root
 * of the search that produced it. Mate and centipawn scores are separate
 * kinds and are never subtracted from each other (concept Section 10.1).
 */
@Serializable
sealed interface Score {
    /** Centipawns, positive when the root side to move is better. */
    @Serializable
    @SerialName("cp")
    data class Centipawns(val cp: Int) : Score

    /**
     * Mate distance in moves: positive when the root side to move mates,
     * negative when it is mated, 0 when the root side to move is already
     * checkmated.
     */
    @Serializable
    @SerialName("mate")
    data class Mate(val moves: Int) : Score

    /** The same score seen by the other side. */
    fun negate(): Score = when (this) {
        is Centipawns -> Centipawns(-cp)
        is Mate -> Mate(-moves)
    }

    /**
     * A total order consistent with the engine's preferences: any winning
     * mate beats any centipawn score, a shorter win beats a longer one, and
     * a longer loss beats a shorter one. For ranking only, never arithmetic.
     */
    fun rank(): Long = when (this) {
        is Centipawns -> cp.toLong()
        is Mate -> if (moves > 0) MATE_RANK - moves else -MATE_RANK - moves
    }

    private companion object {
        const val MATE_RANK = 1_000_000L
    }
}

@Serializable
enum class ScoreBound { EXACT, LOWER, UPPER }

/** Win/draw/loss per mille from the root side to move's point of view. */
@Serializable
data class Wdl(val win: Int, val draw: Int, val loss: Int)

/** A fixed search budget. Node limits keep single-thread search reproducible (concept Section 14.2). */
@Serializable
data class SearchLimit(val nodes: Long? = null, val depth: Int? = null) {
    init {
        require(nodes != null || depth != null) { "A search needs a node or depth limit" }
    }
}

/**
 * Engine configuration sent explicitly before every search, so settings from
 * one role (analyst, opponent) can never leak into another (Section 14.2).
 */
@Serializable
data class EngineProfile(
    val threads: Int = 1,
    val hashMb: Int = 16,
    /** Clear the hash and histories before each search (ucinewgame) for reproducible results. */
    val clearBeforeSearch: Boolean = true,
)

/**
 * One search. [initialFen] plus the full [moves] history is sent, never just
 * the current FEN, because the engine needs history for repetitions (S1).
 */
@Serializable
data class AnalysisRequest(
    val initialFen: String,
    val moves: List<String>,
    val limit: SearchLimit,
    val multiPv: Int = 1,
    /** Restricts root candidates only; it imposes nothing on later plies (concept Section 8.1). */
    val searchMoves: List<String> = emptyList(),
    val profile: EngineProfile = EngineProfile(),
)

@Serializable
data class CandidateLine(
    val multiPv: Int,
    val move: String,
    val score: Score,
    val bound: ScoreBound,
    val depth: Int,
    val nodes: Long,
    val pv: List<String>,
    val wdl: Wdl? = null,
)

/**
 * Normalized result of one search. Scores are from the root side to move's
 * point of view. [complete] is false when the search was stopped early; such
 * results must not be presented as finished analysis.
 */
@Serializable
data class AnalysisResult(
    val request: AnalysisRequest,
    val engineName: String,
    val lines: List<CandidateLine>,
    val bestMove: String?,
    val complete: Boolean,
    val nodes: Long,
) {
    val best: CandidateLine? get() = lines.firstOrNull()

    fun lineFor(move: String): CandidateLine? = lines.firstOrNull { it.move == move }
}
