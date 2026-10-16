// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach.plan

import ch.lkmc.chessandcocoa.chess.Game
import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.chess.Position
import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.chess.Square

/** Whether a plan's goal predicate holds, judged by checks outside the template where possible. */
enum class GoalState { OPEN, REACHED, FAILED }

/**
 * A reusable strategic idea (concept Section 7.1). Everything here is
 * rule-level: legal moves, attack geometry, material. Whether an action is
 * good enough is decided by engine analysis in [PlanPolicy], never here.
 */
interface PlanTemplate {
    val family: PlanFamily
    val version: Int

    /** Applicability and target binding for [owner], who is to move. Empty when the idea does not apply. */
    fun bind(game: Game, owner: Side): List<PlanTarget>

    /** Where the target stands now (following piece identity), or null when it is gone. */
    fun locate(game: Game, plan: PlanInstance): Square?

    /** The plan's named assumptions, evaluated in the current position. */
    fun dependencies(game: Game, plan: PlanInstance): List<DependencyState>

    /** Legal next actions for the owner, who is to move, most direct first. */
    fun actions(game: Game, plan: PlanInstance): List<PlanAction>

    /** Opponent replies (opponent to move) that specifically test the plan's dependencies. */
    fun challenges(game: Game, plan: PlanInstance): List<Move>

    /** The goal predicate. REACHED requires material won or mate, checked against the game itself. */
    fun goal(game: Game, plan: PlanInstance): GoalState

    /**
     * Progress features (Section 7.1) folded into one comparable number for
     * experiments, e.g. attackers minus defenders on the target. Higher is
     * further along; never shown to players and never a probability.
     */
    fun progress(game: Game, plan: PlanInstance): Int

    companion object {
        /** Subgoal order for "how far along" comparisons and action ordering. */
        fun advancement(subgoal: Subgoal): Int = when (subgoal) {
            Subgoal.STRIKE -> 5
            Subgoal.CREATE_THREAT -> 4
            Subgoal.REMOVE_DEFENDER -> 3
            Subgoal.ADVANCE -> 3
            Subgoal.ADD_ATTACKER -> 2
            Subgoal.PIN -> 2
            Subgoal.ATTACK_DEFENDER -> 1
            Subgoal.OCCUPY_FILE -> 1
        }

        val all: List<PlanTemplate> = listOf(AttackKingSquareTemplate, PassedPawnTemplate, WinWeakUnitTemplate, OpenFileTemplate)

        fun of(family: PlanFamily): PlanTemplate = all.first { it.family == family }

        /**
         * Most direct first: further-advanced subgoals, then mates, then the
         * larger static exchange, then UCI order so the result is deterministic.
         */
        fun order(position: Position, actions: List<PlanAction>, limit: Int): List<PlanAction> = actions
            // A step that simply hands over the moving piece is no step: static exchange first,
            // the engine judges the rest.
            .filter { position.see(it.move) >= 0 }
            .sortedWith(
                compareByDescending<PlanAction> { advancement(it.subgoal) }
                    .thenByDescending { position.play(it.move).isCheckmate() }
                    .thenByDescending { if (position.isCapture(it.move)) position.see(it.move) else 0 }
                    .thenBy { it.move.uci },
            )
            .take(limit)

        /**
         * The plan's most direct next step if its owner could move now, found
         * with the opponent's turn passed. A diagnostic for hints and impact
         * baselines only, never presented as a line (Section 6.3).
         */
        fun nextStep(game: Game, plan: PlanInstance): PlanAction? {
            val template = of(plan.family)
            val passed = game.current.passTurn() ?: return null
            val square = template.locate(game, plan) ?: return null
            val asOwner = Game.new(passed.fen())
            val rebound = plan.copy(target = plan.target.copy(square = square, pieceId = plan.target.pieceId?.let { asOwner.pieceIdAt(square) }))
            return template.actions(asOwner, rebound).firstOrNull()
        }
    }
}

/** Material lead of [side] in the current position, in pawn units of 100. */
internal fun Game.balance(side: Side): Int = current.material(side) - current.material(side.opposite)
