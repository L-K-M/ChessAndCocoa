// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach

import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.chess.Square
import ch.lkmc.chessandcocoa.coach.plan.AttackKingSquareTemplate
import ch.lkmc.chessandcocoa.coach.plan.DependencyKind
import ch.lkmc.chessandcocoa.coach.plan.GoalState
import ch.lkmc.chessandcocoa.coach.plan.PlanFamily
import ch.lkmc.chessandcocoa.coach.plan.PlanInstance
import ch.lkmc.chessandcocoa.coach.plan.PlanStatus
import ch.lkmc.chessandcocoa.coach.plan.PlanTemplate
import ch.lkmc.chessandcocoa.coach.plan.Planner
import ch.lkmc.chessandcocoa.coach.plan.Subgoal
import ch.lkmc.chessandcocoa.coach.plan.WinWeakUnitTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Rule-level checks of the two beginner families (concept Section 7.3); no engine needed. */
class PlanTemplateTest {

    private val f7 = Square.parse("f7")

    @Test
    fun f7IsBoundOnceTheBishopAimsAtIt() {
        val game = gameOf("e4", "e5", "Bc4", "Nc6")
        val targets = AttackKingSquareTemplate.bind(game, Side.WHITE)
        assertEquals(listOf(f7), targets.map { it.square })
    }

    @Test
    fun queenMovesThatThreatenMateOnF7AreCreateThreatActions() {
        val game = gameOf("e4", "e5", "Bc4", "Nc6")
        val plan = Planner.propose(game, Side.WHITE, emptyList()).first { it.family == PlanFamily.ATTACK_KING_SQUARE }
        val actions = AttackKingSquareTemplate.actions(game, plan).associate { game.current.san(it.move) to it.subgoal }

        assertEquals(Subgoal.CREATE_THREAT, actions["Qh5"])
        assertEquals(Subgoal.CREATE_THREAT, actions["Qf3"])
    }

    @Test
    fun g6BlocksTheQueenButNotTheBishop() {
        val plan = scholarPlan()
        val afterG6 = gameOf("e4", "e5", "Bc4", "Nc6", "Qh5", "g6")
        val dependencies = AttackKingSquareTemplate.dependencies(afterG6, plan).associate { it.kind to it.holds }

        // The bishop still attacks f7, so the plan's dependencies hold; only the route changed.
        assertTrue(dependencies.getValue(DependencyKind.ATTACKER_PRESENT))
        assertTrue(dependencies.getValue(DependencyKind.SQUARE_WEAK))
        val sans = AttackKingSquareTemplate.actions(afterG6, plan).map { afterG6.current.san(it.move) }
        assertFalse("Qxf7#" in sans || "Qxf7+" in sans)
        assertTrue("Qf3" in sans)
    }

    @Test
    fun strikeOnF7IsFirstWhenItMates() {
        val plan = scholarPlan()
        val afterNf6 = gameOf("e4", "e5", "Bc4", "Nc6", "Qh5", "Nf6")
        val first = AttackKingSquareTemplate.actions(afterNf6, plan).first()
        assertEquals("Qxf7#", afterNf6.current.san(first.move))
        assertEquals(Subgoal.STRIKE, first.subgoal)
    }

    @Test
    fun nextStepUsesThePassedTurnAsADiagnostic() {
        val plan = scholarPlan()
        val beforeReply = gameOf("e4", "e5", "Bc4", "Nc6", "Qh5")
        val step = assertNotNull(PlanTemplate.nextStep(beforeReply, plan))
        assertEquals("h5f7", step.move.uci)
    }

    @Test
    fun weakUnitFollowsThePieceByIdentityAndCompletesOnlyWithMaterialWon() {
        // White knight f3 attacks e5, defended once by Nc6: a pressed unit, not yet winnable.
        val game = gameOf("e4", "e5", "Nf3", "Nc6")
        val target = WinWeakUnitTemplate.bind(game, Side.WHITE).first { it.square == Square.parse("e5") }
        val plan = Planner.propose(game, Side.WHITE, emptyList()).first { it.target == target }

        assertEquals(Square.parse("e5"), WinWeakUnitTemplate.locate(game, plan))
        assertEquals(GoalState.OPEN, WinWeakUnitTemplate.goal(game, plan))

        // An even trade on e5 removes the target without winning material: failed, not reached.
        val traded = gameOf("e4", "e5", "Nf3", "Nc6", "d4", "exd4", "Nxd4", "Nxd4", "Qxd4")
        val gone = plan.copy(target = plan.target)
        assertEquals(GoalState.FAILED, WinWeakUnitTemplate.goal(traded, gone))
    }

    @Test
    fun revalidationClosesAnEpisodeWhoseKingMovedAway() {
        val plan = scholarPlan()
        val castled = gameOf("e4", "e5", "Bc4", "Nc6", "Qh5", "g6", "Qf3", "Nf6", "Ne2", "Bc5", "d3", "O-O")
        val updated = Planner.revalidate(castled, plan)
        // After O-O the king on g8 still guards f7, but f7 is no longer "only the king's":
        // the rook on f8 defends it now, so the plan's weak-square dependency is broken.
        assertEquals(PlanStatus.ABANDONED, updated.status)
    }

    private fun scholarPlan(): PlanInstance {
        val game = gameOf("e4", "e5", "Bc4", "Nc6")
        val proposed = Planner.propose(game, Side.WHITE, emptyList()).first { it.family == PlanFamily.ATTACK_KING_SQUARE }
        val action = AttackKingSquareTemplate.actions(game, proposed).first { game.current.san(it.move) == "Qh5" }
        return proposed.revise(game.ply, PlanStatus.ACTIVE, ch.lkmc.chessandcocoa.coach.plan.TransitionReason.ADOPTED, action = action)
    }
}
