// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach

import ch.lkmc.chessandcocoa.chess.Game
import ch.lkmc.chessandcocoa.coach.analysis.AnalystBudgets
import ch.lkmc.chessandcocoa.coach.decide.DecisionInput
import ch.lkmc.chessandcocoa.coach.decide.DecisionRecord
import ch.lkmc.chessandcocoa.coach.decide.Difficulty
import ch.lkmc.chessandcocoa.coach.decide.MoveSelector
import ch.lkmc.chessandcocoa.coach.decide.PlanRole
import ch.lkmc.chessandcocoa.coach.decide.PlansSwitch
import ch.lkmc.chessandcocoa.coach.decide.SelectionBasis
import ch.lkmc.chessandcocoa.coach.plan.PlanPolicy
import ch.lkmc.chessandcocoa.coach.plan.PlanStatus
import ch.lkmc.chessandcocoa.coach.plan.PolicyChoice
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Decision faithfulness invariants (concept Section 16.3) on positions where plans apply. */
class DecisionTest {

    private val budgets = AnalystBudgets()

    private val positions = listOf(
        gameOf("e4", "e5", "Bc4", "Nc6"),
        gameOf("e4", "e5", "Nf3", "Nc6"),
        gameOf("e4", "e5", "Nf3", "d6", "d4", "Bg4"),
        gameOf("d4", "d5", "c4", "e6", "Nc3", "Nf6", "Bg5", "Nbd7"),
        Game.new(OpenFileTemplateTest.ROUTE_FEN),
    )

    @Test
    fun recordsAreSelfConsistent() = runBlocking {
        for (game in positions) {
            val record = decide(game, Difficulty.KEEN)
            assertTrue(game.current.isLegal(record.move))
            assertTrue(game.current.isLegal(record.withoutPlanMove))
            assertEquals(game.current.fen(), record.fen)
            assertEquals(game.uciMoves(), record.history)

            // Only a plan decision carries a plan, and its role follows the ablation.
            if (record.selectionBasis == SelectionBasis.PLAN) {
                val plan = assertNotNull(record.plan)
                assertEquals(PlanStatus.ACTIVE, plan.status)
                assertEquals(record.move, plan.current.action?.move)
                assertTrue(assertNotNull(record.planAssessment).holds)
                if (record.planRole == PlanRole.CONCURRENT) assertEquals(record.withoutPlanMove, record.move)
                else assertTrue(record.withoutPlanMove != record.move)
            } else {
                assertEquals(PlanRole.NONE, record.planRole)
                assertNull(record.plan)
            }
        }
    }

    @Test
    fun ablationMatchesTheRecordedCounterfactual() = runBlocking {
        for (game in positions) {
            val withPlans = decide(game, Difficulty.KEEN)
            val withoutPlans = decide(game, Difficulty.KEEN, PlansSwitch.DISABLED)
            assertEquals(withPlans.withoutPlanMove, withoutPlans.move)
            assertEquals(PlanRole.NONE, withoutPlans.planRole)
        }
    }

    @Test
    fun decisionsAreReproducible() = runBlocking {
        for (game in positions) {
            val first = decide(game, Difficulty.STEADY)
            val second = decide(game, Difficulty.STEADY)
            assertEquals(first.copy(searches = 0, nodes = 0), second.copy(searches = 0, nodes = 0))
        }
    }

    @Test
    fun liveAndSimulatedPolicyMovesAgree() = runBlocking {
        // Policy identity: the probe's follow-up after a tested reply is what the policy plays live there.
        for (game in positions) {
            val record = decide(game, Difficulty.KEEN)
            val plan = record.plan ?: continue
            val assessment = record.planAssessment ?: continue
            val policy = PlanPolicy(TestEngine.searcher(), budgets)
            for (branch in assessment.branches.filter { it.followUp != null }) {
                val live = game.play(record.move).play(ch.lkmc.chessandcocoa.chess.Move.parseUci(branch.reply))
                val choice = policy.choose(live, plan, Difficulty.KEEN.planWindowCp)
                assertEquals(branch.followUp, (choice as PolicyChoice.Act).action)
            }
        }
    }

    @Test
    fun concessionsCarryNoPlanClaim() = runBlocking {
        var concessions = 0
        for ((index, game) in positions.withIndex()) {
            for (seed in 0L until 6L) {
                val record = decide(game, Difficulty.GENTLE, seed = seed * 31 + index)
                if (record.selectionBasis != SelectionBasis.DIFFICULTY_CONCESSION) continue
                concessions++
                assertNull(record.plan)
                assertEquals(PlanRole.NONE, record.planRole)
                assertTrue(!record.planIsReason)
            }
        }
        assertTrue(concessions > 0, "expected the gentle profile to concede at least once")
    }

    private suspend fun decide(game: Game, difficulty: Difficulty, plans: PlansSwitch = PlansSwitch.ENABLED, seed: Long = 1): DecisionRecord {
        val selector = MoveSelector(TestEngine.searcher(), budgets)
        return selector.decide(DecisionInput(game, "g", "b0", 0, difficulty, emptyList(), seed, plans)).record
    }
}
