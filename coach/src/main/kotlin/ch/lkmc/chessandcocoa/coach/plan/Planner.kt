// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach.plan

import ch.lkmc.chessandcocoa.chess.Game
import ch.lkmc.chessandcocoa.chess.Side

/**
 * Generates, binds and retires plan instances (concept Section 7.4). It
 * never chooses moves; the selector does, using [PlanPolicy] and [PlanProbe].
 */
object Planner {

    /** An episode that took this many plan actions without reaching its goal has stalled. */
    const val MAX_ACTIONS_PER_EPISODE = 6

    /**
     * Re-checks an open episode at the owner's turn: goal reached, target
     * lost, dependencies broken, or stalled. Returns the episode unchanged when
     * it is still worth testing.
     */
    fun revalidate(game: Game, plan: PlanInstance): PlanInstance {
        if (!plan.isOpen) return plan
        val template = PlanTemplate.of(plan.family)
        val ply = game.ply

        when (template.goal(game, plan)) {
            GoalState.REACHED -> return plan.revise(ply, PlanStatus.COMPLETED, TransitionReason.GOAL_REACHED)
            GoalState.FAILED -> return plan.revise(ply, PlanStatus.ABANDONED, TransitionReason.TARGET_LOST)
            GoalState.OPEN -> Unit
        }

        if (game.outcome() != null) return plan.revise(ply, PlanStatus.ABANDONED, TransitionReason.GAME_OVER)

        val square = template.locate(game, plan)
            ?: return plan.revise(ply, PlanStatus.ABANDONED, TransitionReason.TARGET_LOST)
        val dependencies = template.dependencies(game, plan)
        if (dependencies.any { !it.holds }) {
            return plan.revise(ply, PlanStatus.ABANDONED, TransitionReason.DEPENDENCY_BROKEN, square, dependencies)
        }
        if (plan.actionsTaken >= MAX_ACTIONS_PER_EPISODE) {
            return plan.revise(ply, PlanStatus.ABANDONED, TransitionReason.STALLED, square, dependencies)
        }
        return plan
    }

    /**
     * New episodes [owner] could start here, excluding targets an open
     * episode already pursues. Ids are deterministic so replays match.
     */
    fun propose(game: Game, owner: Side, open: List<PlanInstance>): List<PlanInstance> {
        val proposals = mutableListOf<PlanInstance>()
        for (template in PlanTemplate.all) {
            template.bind(game, owner)
                .filter { target -> open.none { it.family == template.family && sameTarget(it.target, target) } }
                // One idea per square: the more specific family (listed first) keeps it.
                .filter { target -> proposals.none { it.target.square == target.square } && open.none { it.current.targetSquare == target.square } }
                .map { target -> newEpisode(game, owner, template, target) }
                // Applicable only where the idea has at least one sound next step (Section 7.1).
                .filter { episode -> template.actions(game, episode).isNotEmpty() }
                .forEach { episode -> proposals += episode }
        }
        return proposals
    }

    private fun newEpisode(game: Game, owner: Side, template: PlanTemplate, target: PlanTarget): PlanInstance {
        val ply = game.ply
        val seed = PlanInstance(
            episodeId = "p$ply-${template.family.name.lowercase()}-${target.square.name}",
            family = template.family,
            templateVersion = template.version,
            owner = owner,
            target = target,
            startedPly = ply,
            startBalance = game.balance(owner),
            revisions = emptyList(),
        )
        return seed.revise(ply, PlanStatus.PROPOSED, TransitionReason.PROPOSED, target.square, template.dependencies(game, seed))
    }

    private fun sameTarget(a: PlanTarget, b: PlanTarget): Boolean =
        if (a.pieceId != null || b.pieceId != null) a.pieceId == b.pieceId else a.square == b.square
}
