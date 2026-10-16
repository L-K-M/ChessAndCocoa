// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach.plan

import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.chess.PieceId
import ch.lkmc.chessandcocoa.chess.PieceType
import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.chess.Square
import kotlinx.serialization.Serializable

/** The strategic families of the beginner band (concept Section 7.3). */
@Serializable
enum class PlanFamily {
    /** Add attackers to, or remove defenders of, an under-defended or pinned unit. Success: material won. */
    WIN_WEAK_UNIT,

    /** Build a mating or material threat against a square next to the king that only the king defends. */
    ATTACK_KING_SQUARE,

    /** Escort a passed pawn to promotion. Success: a promoted piece that keeps its material. */
    PROMOTE_PASSED_PAWN,
}

/** Lifecycle statuses (Section 7.4). */
@Serializable
enum class PlanStatus { PROPOSED, ACTIVE, SUSPENDED, COMPLETED, ABANDONED, SUPERSEDED }

/** What a plan is aimed at: a piece (followed by identity) and/or a square. */
@Serializable
data class PlanTarget(
    val square: Square,
    val pieceId: PieceId? = null,
    val pieceType: PieceType? = null,
)

/** The kind of step a plan action takes toward its goal. */
@Serializable
enum class Subgoal {
    /** Take the target (or strike on the target square). */
    STRIKE,
    REMOVE_DEFENDER,
    ADD_ATTACKER,
    PIN,
    ATTACK_DEFENDER,
    /** Create a direct mating threat through the target square. */
    CREATE_THREAT,

    /** Push the plan's own pawn one step closer to promotion. */
    ADVANCE,
}

/** A legal move the template proposes, labelled with the step it takes. */
@Serializable
data class PlanAction(val move: Move, val subgoal: Subgoal)

/** Named assumptions a plan depends on. */
@Serializable
enum class DependencyKind {
    TARGET_PRESENT,
    TARGET_UNDER_PRESSURE,
    KING_NEAR_SQUARE,
    SQUARE_WEAK,
    ATTACKER_PRESENT,
    ROUTE_AVAILABLE,
    PAWN_PASSED,
}

@Serializable
data class DependencyState(val kind: DependencyKind, val holds: Boolean, val pieces: List<Square> = emptyList())

/** Why a plan changed status or revision. */
@Serializable
enum class TransitionReason {
    PROPOSED,
    ADOPTED,
    CONTINUED,
    ROUTE_CHANGED,
    TARGET_LOST,
    DEPENDENCY_BROKEN,
    NO_ACCEPTABLE_ACTION,
    REFUTED_IN_PROBE,
    OBLIGATION_TOOK_PRIORITY,
    GOAL_REACHED,
    STALLED,
    BETTER_PLAN,
    GAME_OVER,
    RESUMED,
}

/**
 * One immutable revision of a plan episode. Later information creates a new
 * revision; earlier ones are never edited (concept Section 7.5, R2).
 */
@Serializable
data class PlanRevision(
    val number: Int,
    /** Game ply at which this revision was made. */
    val ply: Int,
    val status: PlanStatus,
    val reason: TransitionReason,
    val targetSquare: Square,
    val dependencies: List<DependencyState>,
    /** The plan move chosen at this revision, if a decision used it. */
    val action: PlanAction? = null,
)

/**
 * One concrete attempt to pursue a plan in a game (a plan episode,
 * Section 3.1): stable id, template version, bound target, revision history.
 */
@Serializable
data class PlanInstance(
    val episodeId: String,
    val family: PlanFamily,
    val templateVersion: Int,
    val owner: Side,
    val target: PlanTarget,
    val startedPly: Int,
    /** owner material minus opponent material when the episode started; the success check is relative to it. */
    val startBalance: Int,
    val revisions: List<PlanRevision>,
) {
    val current: PlanRevision get() = revisions.last()
    val status: PlanStatus get() = current.status
    val isOpen: Boolean get() = status == PlanStatus.ACTIVE || status == PlanStatus.SUSPENDED || status == PlanStatus.PROPOSED

    /** Decisions this episode actually drove (ACTIVE revisions carrying an action). */
    val actionsTaken: Int get() = revisions.count { it.action != null }

    fun revise(
        ply: Int,
        status: PlanStatus,
        reason: TransitionReason,
        targetSquare: Square = current.targetSquare,
        dependencies: List<DependencyState> = current.dependencies,
        action: PlanAction? = null,
    ): PlanInstance = copy(
        revisions = revisions + PlanRevision(
            number = revisions.size + 1,
            ply = ply,
            status = status,
            reason = reason,
            targetSquare = targetSquare,
            dependencies = dependencies,
            action = action,
        ),
    )

    /** The episode as it stood at its latest revision made at or before [ply]; null if it did not exist yet. */
    fun asOf(ply: Int): PlanInstance? {
        val kept = revisions.filter { it.ply <= ply }
        return if (kept.isEmpty()) null else copy(revisions = kept)
    }
}
