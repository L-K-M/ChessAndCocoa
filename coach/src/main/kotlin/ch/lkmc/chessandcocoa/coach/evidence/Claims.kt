// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach.evidence

import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.chess.OutcomeReason
import ch.lkmc.chessandcocoa.chess.Piece
import ch.lkmc.chessandcocoa.chess.PieceType
import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.chess.Square
import ch.lkmc.chessandcocoa.coach.assess.ImpactKind
import ch.lkmc.chessandcocoa.coach.assess.QualityCategory
import ch.lkmc.chessandcocoa.coach.plan.PlanFamily
import ch.lkmc.chessandcocoa.coach.plan.Subgoal
import ch.lkmc.chessandcocoa.coach.plan.TransitionReason
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Certainty classes (concept Section 12.1); wording strength must match them. */
@Serializable
enum class Certainty { RULE_VERIFIED, ANALYSIS_SUPPORTED, HEURISTIC, UNKNOWN }

/** When a claim may be shown (Section 4.2). */
@Serializable
enum class Disclosure {
    /** Feedback about a move already played. */
    NOW,

    /** Reveals the opponent's recorded intention; only after the human's choice touched it. */
    AFTER_CHOICE,

    /** Only in the post-game review. */
    REVIEW_ONLY,
}

/**
 * A legal continuation from an exact game state (R11): the game's history up
 * to [fromPly] plus [moves]. Board arrows and "show me why" replay this one
 * object, so text and visuals cannot drift apart (Section 12.1).
 */
@Serializable
data class Continuation(
    val fromPly: Int,
    val startFen: String,
    val moves: List<Move>,
    val sans: List<String>,
)

/** A square's role in a claim, for highlights paired with shapes or labels, never color alone (R14). */
@Serializable
enum class SquareRole { TARGET, ATTACKER, DEFENDER, MOVED, THREAT }

@Serializable
data class MarkedSquare(val square: Square, val role: SquareRole)

/**
 * An evidence-bearing statement, separate from its wording (Section 12.1).
 * Every subtype names its participants; the renderer turns it into text and
 * the board uses [marks] and [continuation] from the same object.
 */
@Serializable
sealed interface Claim {
    val certainty: Certainty
    val disclosure: Disclosure
    val marks: List<MarkedSquare>
    val continuation: Continuation?

    @Serializable
    @SerialName("game_over")
    data class GameOver(
        val winner: Side?,
        val reason: OutcomeReason,
        val humanSide: Side,
        override val marks: List<MarkedSquare> = emptyList(),
    ) : Claim {
        override val certainty get() = Certainty.RULE_VERIFIED
        override val disclosure get() = Disclosure.NOW
        override val continuation: Continuation? get() = null
    }

    @Serializable
    @SerialName("allows_mate")
    data class AllowsMate(
        val mateSan: String,
        val matingPiece: Piece,
        val supporters: List<Pair<Square, PieceType>>,
        override val marks: List<MarkedSquare>,
        override val continuation: Continuation,
    ) : Claim {
        override val certainty get() = Certainty.RULE_VERIFIED
        override val disclosure get() = Disclosure.NOW
    }

    @Serializable
    @SerialName("allows_forced_mate")
    data class AllowsForcedMate(
        val mateIn: Int,
        override val marks: List<MarkedSquare>,
        override val continuation: Continuation,
    ) : Claim {
        override val certainty get() = Certainty.ANALYSIS_SUPPORTED
        override val disclosure get() = Disclosure.NOW
    }

    @Serializable
    @SerialName("exposes_piece")
    data class ExposesPiece(
        val piece: PieceType,
        val square: Square,
        val undefended: Boolean,
        val captureSan: String,
        override val marks: List<MarkedSquare>,
        override val continuation: Continuation,
        /** Rule-checked: the capture was already possible before the move. */
        val alreadyExposed: Boolean = false,
    ) : Claim {
        override val certainty get() = Certainty.ANALYSIS_SUPPORTED
        override val disclosure get() = Disclosure.NOW
    }

    @Serializable
    @SerialName("missed_win")
    data class MissedWin(
        val san: String,
        val mate: Boolean,
        val captured: PieceType?,
        override val marks: List<MarkedSquare>,
        override val continuation: Continuation,
    ) : Claim {
        override val certainty get() = Certainty.ANALYSIS_SUPPORTED
        override val disclosure get() = Disclosure.NOW
    }

    @Serializable
    @SerialName("wins_material")
    data class WinsMaterial(
        val captured: PieceType,
        val square: Square,
        override val marks: List<MarkedSquare>,
    ) : Claim {
        override val certainty get() = Certainty.ANALYSIS_SUPPORTED
        override val disclosure get() = Disclosure.NOW
        override val continuation: Continuation? get() = null
    }

    @Serializable
    @SerialName("stops_mate_threat")
    data class StopsMateThreat(
        val threatSan: String,
        override val marks: List<MarkedSquare>,
    ) : Claim {
        override val certainty get() = Certainty.RULE_VERIFIED
        override val disclosure get() = Disclosure.NOW
        override val continuation: Continuation? get() = null
    }

    @Serializable
    @SerialName("move_quality")
    data class MoveQuality(
        val category: QualityCategory,
        val bestSan: String,
        val playedWasBest: Boolean,
        /** Only shown when the player opted into numbers (Section 4.4). */
        val lossCp: Int?,
        override val marks: List<MarkedSquare>,
        override val continuation: Continuation?,
    ) : Claim {
        override val certainty get() = Certainty.ANALYSIS_SUPPORTED
        override val disclosure get() = Disclosure.NOW
    }

    /** The opponent's intention as recorded before the human moved (R2). */
    @Serializable
    @SerialName("plan_revealed")
    data class PlanRevealed(
        val family: PlanFamily,
        val target: Square,
        val targetPiece: PieceType?,
        val episodeId: String,
        val decisionId: String,
        val intendedSan: String?,
        val intendedSubgoal: Subgoal?,
        /** Whether the plan may be called the reason for the opponent's last move (Section 9.1). */
        val planWasReason: Boolean,
        val recordedAtPly: Int,
        override val marks: List<MarkedSquare>,
    ) : Claim {
        override val certainty get() = Certainty.RULE_VERIFIED
        override val disclosure get() = Disclosure.AFTER_CHOICE
        override val continuation: Continuation? get() = null
    }

    @Serializable
    @SerialName("plan_impact")
    data class PlanImpact(
        val family: PlanFamily,
        val target: Square,
        val impacts: List<ImpactKind>,
        val threatSan: String?,
        val followUpSan: String?,
        val followUpSubgoal: Subgoal?,
        val anticipated: Boolean,
        override val marks: List<MarkedSquare>,
        override val continuation: Continuation?,
    ) : Claim {
        override val certainty get() = Certainty.ANALYSIS_SUPPORTED
        override val disclosure get() = Disclosure.AFTER_CHOICE
    }

    @Serializable
    @SerialName("better_defense")
    data class BetterDefense(
        val san: String,
        override val marks: List<MarkedSquare>,
    ) : Claim {
        override val certainty get() = Certainty.ANALYSIS_SUPPORTED
        override val disclosure get() = Disclosure.NOW
        override val continuation: Continuation? get() = null
    }

    @Serializable
    @SerialName("opponent_oversight")
    data class OpponentOversight(
        override val marks: List<MarkedSquare> = emptyList(),
    ) : Claim {
        override val certainty get() = Certainty.ANALYSIS_SUPPORTED
        override val disclosure get() = Disclosure.NOW
        override val continuation: Continuation? get() = null
    }

    /** A recorded plan ended or changed after the human's move. */
    @Serializable
    @SerialName("plan_transition")
    data class PlanTransitionClaim(
        val family: PlanFamily,
        val target: Square,
        val reason: TransitionReason,
        override val marks: List<MarkedSquare> = emptyList(),
    ) : Claim {
        override val certainty get() = Certainty.RULE_VERIFIED
        override val disclosure get() = Disclosure.AFTER_CHOICE
        override val continuation: Continuation? get() = null
    }

    /** A deliberately weaker opponent move, disclosed in the review (Section 9.3). */
    @Serializable
    @SerialName("concession")
    data class Concession(
        val ply: Int,
        /** The game's own move number, which need not start at 1. */
        val fullmoveNumber: Int,
        val san: String,
        val bestSan: String,
        override val marks: List<MarkedSquare> = emptyList(),
    ) : Claim {
        override val certainty get() = Certainty.RULE_VERIFIED
        override val disclosure get() = Disclosure.REVIEW_ONLY
        override val continuation: Continuation? get() = null
    }
}
