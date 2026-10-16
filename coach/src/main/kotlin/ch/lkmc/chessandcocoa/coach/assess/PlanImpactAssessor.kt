// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach.assess

import ch.lkmc.chessandcocoa.chess.Game
import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.chess.PieceType
import ch.lkmc.chessandcocoa.coach.analysis.AnalystBudgets
import ch.lkmc.chessandcocoa.coach.analysis.GameSearch
import ch.lkmc.chessandcocoa.coach.analysis.ScoreMath
import ch.lkmc.chessandcocoa.coach.analysis.Searcher
import ch.lkmc.chessandcocoa.coach.decide.DecisionRecord
import ch.lkmc.chessandcocoa.coach.facts.Facts
import ch.lkmc.chessandcocoa.coach.plan.GoalState
import ch.lkmc.chessandcocoa.coach.plan.PlanAction
import ch.lkmc.chessandcocoa.coach.plan.PlanInstance
import ch.lkmc.chessandcocoa.coach.plan.PlanPolicy
import ch.lkmc.chessandcocoa.coach.plan.PlanTemplate
import ch.lkmc.chessandcocoa.coach.plan.PolicyChoice

/**
 * Axis B of the human-move assessment (concept Sections 10.2, 10.4). The
 * played move is compared with a reference move from the same parent
 * position — the best move that does not address the plan — so the natural
 * passage of a turn is never mistaken for the move's effect.
 *
 * ```
 *              parent (human to move)
 *              /                    \
 *        played move h         reference r (ignores the plan)
 *             |                       |
 *       plan policy               plan policy
 *             \                       /
 *              compare: prevents / delays / … / allows
 * ```
 */
class PlanImpactAssessor(private val searcher: Searcher, private val budgets: AnalystBudgets) {

    private val policy = PlanPolicy(searcher, budgets)

    suspend fun assess(
        parent: Game,
        move: Move,
        plan: PlanInstance,
        prior: DecisionRecord?,
        root: GameSearch,
        windowCp: Int,
    ): PlanImpactAssessment {
        val template = PlanTemplate.of(plan.family)
        val threat = threat(parent, plan)
        val after = parent.play(move)

        val addressed = addresses(parent, move, plan)
        val before = template.dependencies(parent, plan).filter { it.holds }.map { it.kind }.toSet()
        val broken = template.dependencies(after, plan).filter { !it.holds && it.kind in before }.map { it.kind }

        val played = outcome(after, plan, windowCp)
        val reference = reference(parent, plan, move, root)
        val referenceOutcome = reference?.let { outcome(parent.play(it), plan, windowCp) }

        val impacts = linkedSetOf<ImpactKind>()
        when {
            !addressed && played.action != null -> impacts += ImpactKind.ALLOWS
            referenceOutcome == null -> impacts += ImpactKind.UNKNOWN
            else -> impacts += compare(played, referenceOutcome)
        }
        if (createsUrgency(after, reference?.let { parent.play(it) }, plan)) impacts += ImpactKind.CREATES_URGENCY

        val anticipated = prior?.planAssessment?.branchFor(move) != null

        return PlanImpactAssessment(
            episodeId = plan.episodeId,
            family = plan.family,
            targetSquare = template.locate(parent, plan) ?: plan.current.targetSquare,
            impacts = impacts.toList(),
            threat = threat,
            threatSan = threat?.let { parent.current.passTurn()?.san(it.move) },
            addressed = addressed,
            played = played,
            reference = reference,
            referenceSan = reference?.let { parent.current.san(it) },
            referenceOutcome = referenceOutcome,
            anticipated = anticipated,
            brokenDependencies = broken,
        )
    }

    /**
     * Whether [move] engages with the plan at all: it breaks one of the
     * plan's dependencies, or the plan's most direct next step no longer
     * exists afterwards. Rule-level, no engine.
     */
    fun addresses(parent: Game, move: Move, plan: PlanInstance): Boolean {
        val template = PlanTemplate.of(plan.family)
        val after = parent.play(move)
        val heldBefore = template.dependencies(parent, plan).filter { it.holds }.map { it.kind }.toSet()
        if (template.dependencies(after, plan).any { !it.holds && it.kind in heldBefore }) return true

        val threat = threat(parent, plan) ?: return false
        val stillThere = template.actions(after, plan).any {
            it.move == threat.move && PlanTemplate.advancement(it.subgoal) >= PlanTemplate.advancement(threat.subgoal)
        }
        return !stillThere
    }

    private fun threat(parent: Game, plan: PlanInstance): PlanAction? = PlanTemplate.nextStep(parent, plan)

    /** The engine's best human move among those that do not address the plan (Section 10.4 baseline). */
    private suspend fun reference(parent: Game, plan: PlanInstance, played: Move, root: GameSearch): Move? {
        root.result.lines.map { Move.parseUci(it.move) }
            .firstOrNull { it != played && !addresses(parent, it, plan) }
            ?.let { return it }

        val ignoring = parent.current.legalMoves().filter { it != played && !addresses(parent, it, plan) }
        if (ignoring.isEmpty()) return null
        val search = searcher.search(parent, budgets.assessNodes, searchMoves = ignoring)
        return search.result.best?.let { Move.parseUci(it.move) }
    }

    private suspend fun outcome(game: Game, plan: PlanInstance, windowCp: Int): PolicyOutcome {
        val template = PlanTemplate.of(plan.family)
        if (game.outcome() != null) return PolicyOutcome(goalReached = template.goal(game, plan) == GoalState.REACHED)
        if (template.goal(game, plan) == GoalState.REACHED) return PolicyOutcome(goalReached = true)

        return when (val choice = policy.choose(game, plan, windowCp)) {
            is PolicyChoice.Act -> PolicyOutcome(choice.action, game.current.san(choice.action.move), choice.score)
            is PolicyChoice.Blocked -> PolicyOutcome(block = choice.reason)
        }
    }

    /** Impact labels for a move that addressed the plan, against the reference (Section 10.2). */
    private fun compare(played: PolicyOutcome, reference: PolicyOutcome): Set<ImpactKind> {
        val playedAct = played.action
        val referenceAct = reference.action

        return when {
            played.goalReached && !reference.goalReached -> setOf(ImpactKind.ENABLES_OR_ACCELERATES)
            playedAct == null && referenceAct == null -> setOf(ImpactKind.NO_SUPPORTED_MATERIAL_EFFECT)
            playedAct == null -> setOf(ImpactKind.PREVENTS)
            referenceAct == null -> setOf(ImpactKind.ENABLES_OR_ACCELERATES)
            else -> compareActions(playedAct, played, referenceAct, reference)
        }
    }

    private fun compareActions(playedAct: PlanAction, played: PolicyOutcome, referenceAct: PlanAction, reference: PolicyOutcome): Set<ImpactKind> {
        val playedLevel = PlanTemplate.advancement(playedAct.subgoal)
        val referenceLevel = PlanTemplate.advancement(referenceAct.subgoal)
        val impacts = mutableSetOf<ImpactKind>()

        if (playedLevel > referenceLevel) impacts += ImpactKind.ENABLES_OR_ACCELERATES
        if (playedLevel < referenceLevel) impacts += ImpactKind.DELAYS
        if (playedAct.move.from != referenceAct.move.from) impacts += ImpactKind.FORCES_ADAPTATION

        val loss = if (played.score != null && reference.score != null) ScoreMath.lossCp(reference.score, played.score) else null
        if (loss != null && loss >= REDUCED_BENEFIT_CP) impacts += ImpactKind.REDUCES_BENEFIT

        return impacts.ifEmpty { setOf(ImpactKind.NO_SUPPORTED_MATERIAL_EFFECT) }
    }

    /** A new obligation for the plan's owner that the reference move would not have created. */
    private fun createsUrgency(after: Game, afterReference: Game?, plan: PlanInstance): Boolean {
        fun urgent(game: Game): Boolean {
            val position = game.current
            val human = plan.owner.opposite
            return position.isCheck() ||
                Facts.threatenedMates(position, human).isNotEmpty() ||
                Facts.winningCaptures(position, human).any { it.second >= URGENT_CAPTURE_CP }
        }
        return urgent(after) && (afterReference == null || !urgent(afterReference))
    }

    private companion object {
        const val REDUCED_BENEFIT_CP = 50
        /** A minor piece net of a pawn: big enough to demand an answer. */
        const val URGENT_CAPTURE_CP = 200
    }
}
