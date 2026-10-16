// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach.plan

import ch.lkmc.chessandcocoa.chess.Game
import ch.lkmc.chessandcocoa.coach.analysis.AnalystBudgets
import ch.lkmc.chessandcocoa.coach.analysis.ScoreMath
import ch.lkmc.chessandcocoa.coach.analysis.Searcher
import ch.lkmc.chessandcocoa.engine.Score
import kotlinx.serialization.Serializable

/** Why the plan policy could not act. */
@Serializable
enum class PolicyBlock { NO_ROUTE, TOO_COSTLY, GOAL_FAILED }

/** The plan policy's decision in one position. */
@Serializable
sealed interface PolicyChoice {
    /** Engine's best unrestricted move here, for comparison. */
    val referenceMove: String?

    @Serializable
    data class Act(
        val action: PlanAction,
        /** Score of the action, owner's point of view. */
        val score: Score,
        /** Best unrestricted score, owner's point of view. */
        val referenceScore: Score,
        override val referenceMove: String?,
        /** Every action the template proposed. */
        val proposed: List<PlanAction>,
    ) : PolicyChoice

    @Serializable
    data class Blocked(
        val reason: PolicyBlock,
        val bestActionScore: Score? = null,
        val referenceScore: Score? = null,
        override val referenceMove: String? = null,
    ) : PolicyChoice
}

/**
 * The one deterministic plan policy per template (concept Section 8.2):
 * generate the template's legal actions, search them with root-restricted
 * analysis under a fixed node budget, and keep the best one if it is within
 * the quality window of the best unrestricted move. Live play and probes call
 * this same function, so the same position, window and engine profile give
 * the same move (policy identity, Section 16.3).
 */
class PlanPolicy(private val searcher: Searcher, private val budgets: AnalystBudgets) {

    suspend fun choose(game: Game, plan: PlanInstance, windowCp: Int): PolicyChoice {
        val template = PlanTemplate.of(plan.family)
        if (template.goal(game, plan) == GoalState.FAILED) return PolicyChoice.Blocked(PolicyBlock.GOAL_FAILED)

        val actions = template.actions(game, plan)
        if (actions.isEmpty()) return PolicyChoice.Blocked(PolicyBlock.NO_ROUTE)

        val owner = plan.owner
        val reference = searcher.search(game, budgets.policyNodes)
        val referenceLine = reference.result.best ?: return PolicyChoice.Blocked(PolicyBlock.NO_ROUTE)
        val referenceScore = reference.score(referenceLine, owner)

        // The engine's own first choice is a plan action: no restricted search needed.
        actions.firstOrNull { it.move.uci == referenceLine.move }?.let { action ->
            return PolicyChoice.Act(action, referenceScore, referenceScore, referenceLine.move, actions)
        }

        val restricted = searcher.search(game, budgets.policyNodes, searchMoves = actions.map { it.move })
        val bestLine = restricted.result.best ?: return PolicyChoice.Blocked(PolicyBlock.NO_ROUTE)
        val bestScore = restricted.score(bestLine, owner)

        if (!ScoreMath.withinWindow(bestScore, referenceScore, windowCp)) {
            return PolicyChoice.Blocked(PolicyBlock.TOO_COSTLY, bestScore, referenceScore, referenceLine.move)
        }

        val action = actions.first { it.move.uci == bestLine.move }
        return PolicyChoice.Act(action, bestScore, referenceScore, referenceLine.move, actions)
    }
}
