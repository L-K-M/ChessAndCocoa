// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach.game

import ch.lkmc.chessandcocoa.chess.Game
import ch.lkmc.chessandcocoa.chess.GameOutcome
import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.chess.OutcomeReason
import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.coach.assess.MoveAssessment
import ch.lkmc.chessandcocoa.coach.decide.DecisionRecord
import ch.lkmc.chessandcocoa.coach.decide.Difficulty
import ch.lkmc.chessandcocoa.coach.plan.PlanInstance
import ch.lkmc.chessandcocoa.coach.teach.HintLevel
import ch.lkmc.chessandcocoa.coach.teach.TeachingEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** A line given up by a takeback, kept so the history is never overwritten (concept Section 14.3). */
@Serializable
data class AbandonedBranch(
    val branchId: String,
    val fromPly: Int,
    val moves: List<Move>,
    val decisions: List<DecisionRecord>,
    val assessments: List<MoveAssessment>,
    val events: List<TeachingEvent>,
)

/** Hints shown for one human decision; any hint makes the move assisted (Section 4.3). */
@Serializable
data class HintUse(val ply: Int, val level: HintLevel)

/**
 * Everything a game is: moves, the frozen decision records, assessments, plan
 * episodes and teaching events of the live branch, plus abandoned branches.
 * Persisted together so a resumed game never reconstructs an intention from
 * the board (Section 14.3). Immutable; every change yields a new revision.
 */
@Serializable
data class GameRecord(
    val gameId: String,
    val branchId: String,
    /** Bumped on every change; results computed for an older revision are discarded (R11). */
    val revision: Int,
    val humanSide: Side,
    val difficulty: Difficulty,
    val seed: Long,
    val initialFen: String,
    val moves: List<Move>,
    val decisions: List<DecisionRecord> = emptyList(),
    val assessments: List<MoveAssessment> = emptyList(),
    val episodes: List<PlanInstance> = emptyList(),
    val events: List<TeachingEvent> = emptyList(),
    val hints: List<HintUse> = emptyList(),
    /** Episodes whose intention has been shown to the player. */
    val revealedEpisodes: Set<String> = emptySet(),
    val abandoned: List<AbandonedBranch> = emptyList(),
    val resignedBy: Side? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    @Transient
    val game: Game = Game.fromMoves(initialFen, moves)

    val ply: Int get() = moves.size

    val sideToMove: Side get() = game.current.sideToMove

    val isHumanTurn: Boolean get() = outcome == null && sideToMove == humanSide

    val outcome: GameOutcome?
        get() = resignedBy?.let { GameOutcome(it.opposite, OutcomeReason.RESIGNATION) } ?: game.outcome()

    /** The opponent decision that produced the current position, when it is the human's turn. */
    val lastDecision: DecisionRecord? get() = decisions.lastOrNull { it.ply == ply - 1 }

    /** The opponent's open plan episode, if any. */
    val openEpisode: PlanInstance? get() = episodes.lastOrNull { it.isOpen }

    fun assistedAt(ply: Int): Boolean = hints.any { it.ply == ply }

    fun hintLevelAt(ply: Int): HintLevel? = hints.filter { it.ply == ply }.maxByOrNull { it.level.ordinal }?.level
}
