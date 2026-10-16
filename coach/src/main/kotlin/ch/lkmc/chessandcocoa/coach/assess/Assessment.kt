// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach.assess

import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.chess.Piece
import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.chess.Square
import ch.lkmc.chessandcocoa.coach.decide.CandidateValue
import ch.lkmc.chessandcocoa.coach.plan.DependencyKind
import ch.lkmc.chessandcocoa.coach.plan.PlanAction
import ch.lkmc.chessandcocoa.coach.plan.PlanFamily
import ch.lkmc.chessandcocoa.coach.plan.PolicyBlock
import ch.lkmc.chessandcocoa.engine.Score
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Overall move quality bands (concept Section 10.1). The centipawn bounds
 * behind them are uncalibrated starting points; they are never shown as
 * numbers by default.
 */
@Serializable
enum class QualityCategory { BEST, GOOD, INACCURACY, MISTAKE, BLUNDER }

/** FULL when the played move was compared in one search; REDUCED when separate searches disagreed. */
@Serializable
enum class Confidence { FULL, REDUCED }

/** Axis A: the played move against strong alternatives, independent of any plan (R3). */
@Serializable
data class QualityAssessment(
    val category: QualityCategory,
    /** Estimated loss in centipawns; null when mate scores make it meaningless. */
    val lossCp: Int?,
    /** Scores from the mover's point of view. */
    val playedScore: Score,
    val bestScore: Score,
    val bestMove: Move,
    val bestSan: String,
    val confidence: Confidence,
)

/** Typed tactical facts about the played move; each names its participants. */
@Serializable
sealed interface TacticalFinding {

    /** The move lets the opponent mate at once. Rule-verified. */
    @Serializable
    @SerialName("allows_mate_in_one")
    data class AllowsMateInOne(
        val mateMove: Move,
        val mateSan: String,
        val matingPiece: Piece,
        /** Pieces of the mating side that protect the mating square. */
        val supporters: List<Square>,
    ) : TacticalFinding

    /** Analysis shows a forced mate against the mover, longer than one move. */
    @Serializable
    @SerialName("allows_forced_mate")
    data class AllowsForcedMate(val mateIn: Int, val line: List<Move>, val lineSan: List<String>) : TacticalFinding

    /** The engine's expected reply wins the piece the move left exposed. */
    @Serializable
    @SerialName("leaves_piece_exposed")
    data class LeavesPieceExposed(
        val square: Square,
        val piece: Piece,
        val capture: Move,
        val captureSan: String,
        val undefended: Boolean,
        /** The same capture was already possible before the move, which only failed to prevent it. */
        val alreadyExposed: Boolean = false,
    ) : TacticalFinding

    /** A clearly better capture or mate was available. */
    @Serializable
    @SerialName("missed_win")
    data class MissedWin(
        val move: Move,
        val san: String,
        val mate: Boolean,
        val captured: Piece?,
        /** Evidence: the winning line, starting with [move]. */
        val line: List<Move>,
    ) : TacticalFinding

    /** The move captures material that the static exchange keeps. */
    @Serializable
    @SerialName("wins_material")
    data class WinsMaterial(val captured: Piece, val square: Square, val gain: Int) : TacticalFinding

    /** The opponent had a mate in one before the move and has none after it. Rule-verified. */
    @Serializable
    @SerialName("stops_mate_threat")
    data class StopsMateThreat(val threat: Move, val threatSan: String) : TacticalFinding

    /** The move gives checkmate. */
    @Serializable
    @SerialName("delivers_mate")
    data class DeliversMate(val san: String) : TacticalFinding

    /**
     * The opponent's previous move expected a much better result than the
     * analyst now sees: it did not see this reply. Backed by the frozen
     * decision record, so "I did not see that" is honest (Section 9.3).
     */
    @Serializable
    @SerialName("opponent_oversight")
    data class OpponentOversight(val decisionId: String, val expected: Score, val actual: Score) : TacticalFinding
}

/** Effects of a move on the opponent's recorded plan (concept Section 10.2). */
@Serializable
enum class ImpactKind {
    PREVENTS,
    DELAYS,
    REDUCES_BENEFIT,
    FORCES_ADAPTATION,
    CREATES_URGENCY,
    ENABLES_OR_ACCELERATES,

    /** The move does not address the plan; its next step stays available. */
    ALLOWS,
    NO_SUPPORTED_MATERIAL_EFFECT,
    UNKNOWN,
}

/** The plan policy's result in one counterfactual position, stored as values. */
@Serializable
data class PolicyOutcome(
    val action: PlanAction? = null,
    val actionSan: String? = null,
    val score: Score? = null,
    val block: PolicyBlock? = null,
    val goalReached: Boolean = false,
)

/** Axis B: what the move did to the plan recorded before it (R3: independent of Axis A). */
@Serializable
data class PlanImpactAssessment(
    val episodeId: String,
    val family: PlanFamily,
    val targetSquare: Square,
    val impacts: List<ImpactKind>,
    /** The plan's most direct next step before the move (from the board, owner to move). */
    val threat: PlanAction? = null,
    val threatSan: String? = null,
    /** Whether the move changed the plan's dependencies or its next step at all. */
    val addressed: Boolean,
    val played: PolicyOutcome,
    /** The best move that does not address the plan: the impact baseline (Section 10.4). */
    val reference: Move? = null,
    val referenceSan: String? = null,
    val referenceOutcome: PolicyOutcome? = null,
    /** The move, or its class, was tested before it was played; only then may "I expected" be said (Section 7.5). */
    val anticipated: Boolean,
    val brokenDependencies: List<DependencyKind> = emptyList(),
)

/** A full assessment of one human move. */
@Serializable
data class MoveAssessment(
    val id: String,
    /** Ply of the position in which the move was played. */
    val ply: Int,
    val side: Side,
    val move: Move,
    val san: String,
    val quality: QualityAssessment,
    val tactics: List<TacticalFinding>,
    val planImpact: PlanImpactAssessment? = null,
    /** Strong alternatives from the same parent position, mover's view. */
    val alternatives: List<CandidateValue>,
    /** A stronger move that also addresses the plan, when the played one addressed it at a cost. */
    val betterDefense: CandidateValue? = null,
    /** A hint was shown before this move (Section 4.3). */
    val assisted: Boolean,
    val searches: Int,
    val nodes: Long,
)
