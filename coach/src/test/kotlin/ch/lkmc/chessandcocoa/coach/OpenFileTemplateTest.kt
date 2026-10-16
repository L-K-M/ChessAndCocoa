// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach

import ch.lkmc.chessandcocoa.chess.Game
import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.chess.Square
import ch.lkmc.chessandcocoa.coach.plan.DependencyKind
import ch.lkmc.chessandcocoa.coach.plan.GoalState
import ch.lkmc.chessandcocoa.coach.plan.OpenFileTemplate
import ch.lkmc.chessandcocoa.coach.plan.PlanFamily
import ch.lkmc.chessandcocoa.coach.plan.PlanInstance
import ch.lkmc.chessandcocoa.coach.plan.Planner
import ch.lkmc.chessandcocoa.coach.plan.Subgoal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenFileTemplateTest {
    @Test
    fun theRookUsesAnOpenFileOnlyWhenItLeadsToAPawn() {
        val game = Game.new(ROUTE_FEN)
        val plan = plan(game)
        assertEquals(Square.parse("b7"), plan.target.square)
        assertEquals(mapOf("Rc1" to Subgoal.OCCUPY_FILE), actions(game, plan))
    }

    @Test
    fun occupyingTheFileAndInvadingArePreparationNotSuccess() {
        val game = Game.new(ROUTE_FEN)
        val plan = plan(game)
        val staged = play(game, "Rc1", "Kf8")
        val invaded = play(staged, "Rc7", "Kg8")

        assertEquals(Subgoal.ADD_ATTACKER, actions(staged, plan)["Rc7"])
        assertEquals(Subgoal.STRIKE, actions(invaded, plan)["Rxb7"])
        assertEquals(GoalState.OPEN, OpenFileTemplate.goal(staged, plan))
        assertEquals(GoalState.OPEN, OpenFileTemplate.goal(invaded, plan))
        assertTrue(OpenFileTemplate.progress(staged, plan) > OpenFileTemplate.progress(game, plan))
        assertTrue(OpenFileTemplate.progress(invaded, plan) > OpenFileTemplate.progress(staged, plan))

        val won = play(invaded, "Rxb7", "Kf8")
        assertEquals(GoalState.REACHED, OpenFileTemplate.goal(won, plan))
    }

    @Test
    fun closedBlockedAndPoisonedFilesAreRejected() {
        val positions = listOf(
            // No open file.
            "6kr/1p3ppp/8/8/8/8/PPPPPPPP/R5K1 w - - 0 1",
            // The only open file is blocked by a knight.
            "6kr/1p3ppp/8/8/2n5/8/PP1PPPPP/R5K1 w - - 0 1",
            // The entry square c7 loses the rook to Rc8.
            "2r3k1/1p3ppp/8/8/8/8/PP1PPPPP/R5K1 w - - 0 1",
            // The entry is safe, but the pawn is defended by Rb8.
            "1r4k1/1p3ppp/8/8/8/8/PP1PPPPP/R5K1 w - - 0 1",
        )
        for (fen in positions) {
            assertTrue(OpenFileTemplate.bind(Game.new(fen), Side.WHITE).isEmpty(), fen)
        }
    }

    @Test
    fun theTargetIsFollowedWhenItMoves() {
        val game = Game.new(ROUTE_FEN)
        val plan = plan(game)
        val escaped = play(game, "Rc1", "b6")

        assertEquals(Square.parse("b6"), OpenFileTemplate.locate(escaped, plan))
        assertEquals(Subgoal.ADD_ATTACKER, actions(escaped, plan)["Rc6"])
        assertEquals(GoalState.OPEN, OpenFileTemplate.goal(escaped, plan))
    }

    @Test
    fun capturingThePawnAtTheCostOfARookDoesNotCompleteThePlan() {
        val plan = plan(Game.new(ROUTE_FEN))
        val defended = Game.new("1r4k1/1pR2ppp/8/8/8/8/PP1PPPPP/6K1 w - - 0 1")
        val traded = play(defended, "Rxb7", "Rxb7")

        assertEquals(GoalState.FAILED, OpenFileTemplate.goal(traded, plan))
    }

    @Test
    fun blockingTheRouteBreaksItsNamedDependency() {
        val game = Game.new("6k1/1p2nppp/8/8/8/8/PP1PPPPP/R5K1 w - - 0 1")
        val plan = plan(game)
        val blocked = play(game, "Rc1", "Nc6")

        assertFalse(OpenFileTemplate.dependencies(blocked, plan).single { it.kind == DependencyKind.ROUTE_AVAILABLE }.holds)
        assertTrue(actions(blocked, plan).isEmpty())
    }

    @Test
    fun blackUsesTheSameRouteTowardTheSecondRank() {
        val game = Game.new("r5k1/pp1ppppp/8/8/8/8/1P3PPP/6KR b - - 0 1")
        val plan = plan(game)
        assertEquals(Square.parse("b2"), plan.target.square)
        assertEquals(mapOf("Rc8" to Subgoal.OCCUPY_FILE), actions(game, plan))
        assertEquals(Subgoal.ADD_ATTACKER, actions(play(game, "Rc8", "Kf1"), plan)["Rc2"])
    }

    private fun plan(game: Game): PlanInstance = Planner.propose(game, game.current.sideToMove, emptyList())
        .single { it.family == PlanFamily.INVADE_OPEN_FILE }

    private fun actions(game: Game, plan: PlanInstance): Map<String, Subgoal> = OpenFileTemplate.actions(game, plan)
        .associate { game.current.san(it.move) to it.subgoal }

    private fun play(game: Game, vararg sans: String): Game = sans.fold(game) { current, san -> current.play(current.current.parseSan(san)) }

    companion object {
        const val ROUTE_FEN = "6kr/1p3ppp/8/8/8/8/PP1PPPPP/R5K1 w - - 0 1"
    }
}
