// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach.plan

import ch.lkmc.chessandcocoa.chess.Game
import ch.lkmc.chessandcocoa.chess.Move
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

    companion object {
        /** Subgoal order for "how far along" comparisons and action ordering. */
        fun advancement(subgoal: Subgoal): Int = when (subgoal) {
            Subgoal.STRIKE -> 5
            Subgoal.CREATE_THREAT -> 4
            Subgoal.REMOVE_DEFENDER -> 3
            Subgoal.ADD_ATTACKER -> 2
            Subgoal.PIN -> 2
            Subgoal.ATTACK_DEFENDER -> 1
        }

        val all: List<PlanTemplate> = listOf(AttackKingSquareTemplate, WinWeakUnitTemplate)

        fun of(family: PlanFamily): PlanTemplate = all.first { it.family == family }
    }
}

/** Material lead of [side] in the current position, in pawn units of 100. */
internal fun Game.balance(side: Side): Int = current.material(side) - current.material(side.opposite)
