// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.harness

import ch.lkmc.chessandcocoa.chess.Game
import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.coach.analysis.AnalystBudgets
import ch.lkmc.chessandcocoa.coach.analysis.ScoreMath
import ch.lkmc.chessandcocoa.coach.analysis.Searcher
import ch.lkmc.chessandcocoa.coach.assess.ImpactKind
import ch.lkmc.chessandcocoa.coach.decide.DecisionRecord
import ch.lkmc.chessandcocoa.coach.decide.PlanRole
import ch.lkmc.chessandcocoa.coach.decide.SelectionBasis
import ch.lkmc.chessandcocoa.coach.evidence.Claim
import ch.lkmc.chessandcocoa.coach.plan.PlanInstance
import ch.lkmc.chessandcocoa.coach.plan.PlanPolicy
import ch.lkmc.chessandcocoa.coach.plan.PlanStatus
import ch.lkmc.chessandcocoa.coach.plan.PlanTemplate
import ch.lkmc.chessandcocoa.coach.plan.PolicyChoice
import ch.lkmc.chessandcocoa.coach.plan.TransitionReason
import kotlin.random.Random

/** Per-game counts for E3 (coverage in ordinary games). */
data class Coverage(
    val decisions: Int,
    val templateApplies: Int,
    val planChosen: Int,
    val planDriven: Int,
    val axisB: Int,
    val planEventsShown: Int,
    val tacticalFindings: Int,
    val silentMoves: Int,
    val humanMoves: Int,
)

private val REASON_ROLES = setOf(PlanRole.INTRODUCED_CANDIDATE, PlanRole.DECISIVE, PlanRole.TIEBREAK)

fun coverage(game: SimulatedGame): Coverage {
    val record = game.record
    val decisions = record.decisions
    val shownPlies = game.events.map { it.ply }.toSet()
    return Coverage(
        decisions = decisions.size,
        templateApplies = decisions.count { it.considered.isNotEmpty() },
        planChosen = decisions.count { it.selectionBasis == SelectionBasis.PLAN },
        planDriven = decisions.count { it.planRole in REASON_ROLES },
        axisB = record.assessments.count { assessment ->
            val impacts = assessment.planImpact?.impacts.orEmpty()
            impacts.any { it != ImpactKind.UNKNOWN && it != ImpactKind.NO_SUPPORTED_MATERIAL_EFFECT }
        },
        planEventsShown = game.events.count { event -> event.claims.any { it is Claim.PlanImpact || it is Claim.PlanTransitionClaim } },
        tacticalFindings = record.assessments.count { it.tactics.isNotEmpty() },
        silentMoves = record.assessments.count { it.ply + 1 !in shownPlies },
        humanMoves = record.assessments.size,
    )
}

/** E1: does the plan change moves, and are changed moves specific to the plan? */
data class Influence(
    val eligible: Int,
    val roles: Map<PlanRole, Int>,
    val progressWins: Int,
    val progressTies: Int,
    val progressLosses: Int,
)

/**
 * Eligible decisions: a template applied and at least two candidates were
 * acceptable (within the difficulty's plan window of the best). For plan-
 * driven moves, the plan's progress features after the move are compared
 * with those after a seeded random other acceptable move.
 */
fun influence(games: List<SimulatedGame>, seed: Long): Influence {
    val random = Random(seed)
    val roles = mutableMapOf<PlanRole, Int>()
    var eligible = 0
    var wins = 0
    var ties = 0
    var losses = 0

    for (game in games) {
        val full = game.record.game
        for (decision in game.record.decisions) {
            if (decision.considered.isEmpty()) continue
            val best = decision.candidates.firstOrNull()?.score ?: continue
            val acceptable = decision.candidates.filter { ScoreMath.withinWindow(it.score, best, decision.difficulty.planWindowCp) }
            if (acceptable.size < 2) continue
            eligible++
            roles.merge(decision.planRole, 1, Int::plus)

            if (decision.planRole !in REASON_ROLES) continue
            val plan = decision.plan ?: continue
            val others = acceptable.map { it.move }.filter { it != decision.move }
            if (others.isEmpty()) continue
            val before = full.truncate(decision.ply)
            val template = PlanTemplate.of(plan.family)
            val planProgress = template.progress(before.play(decision.move), plan)
            val randomProgress = template.progress(before.play(others[random.nextInt(others.size)]), plan)
            when {
                planProgress > randomProgress -> wins++
                planProgress == randomProgress -> ties++
                else -> losses++
            }
        }
    }
    return Influence(eligible, roles, wins, ties, losses)
}

/** E2 (partial): policy identity, dead-on-arrival plans and early refutations. */
data class Continuity(
    val probedBranches: Int,
    val policyMismatches: Int,
    val adopted: Int,
    val deadOnArrival: Int,
    val refutedWithinTwo: Int,
    val completed: Int,
)

suspend fun continuity(games: List<SimulatedGame>, searcher: Searcher): Continuity {
    val policy = PlanPolicy(searcher, AnalystBudgets())
    var branches = 0
    var mismatches = 0

    for (game in games) {
        val full = game.record.game
        for (decision in game.record.decisions) {
            mismatches += identityMismatches(decision, full.truncate(decision.ply), policy).also { branches += it.second }.first
        }
    }

    val episodes = games.flatMap { it.record.episodes }.filter { episode -> episode.revisions.any { it.reason == TransitionReason.ADOPTED } }

    // Closed (suspended or abandoned) within this many own decisions after adoption.
    fun closedWithin(episode: PlanInstance, decisions: Int): Boolean {
        val adoptedAt = episode.revisions.first { it.reason == TransitionReason.ADOPTED }.ply
        val failure = episode.revisions.firstOrNull { it.status == PlanStatus.ABANDONED || it.status == PlanStatus.SUSPENDED } ?: return false
        val gap = failure.ply - adoptedAt
        return gap in 1..(decisions * 2)
    }
    return Continuity(
        probedBranches = branches,
        policyMismatches = mismatches,
        adopted = episodes.size,
        deadOnArrival = episodes.count { closedWithin(it, 1) },
        refutedWithinTwo = episodes.count { closedWithin(it, 2) },
        completed = episodes.count { it.status == PlanStatus.COMPLETED },
    )
}

/** Re-runs the plan policy live after every tested reply; returns (mismatches, branches checked). */
private suspend fun identityMismatches(decision: DecisionRecord, before: Game, policy: PlanPolicy): Pair<Int, Int> {
    val plan = decision.plan ?: return 0 to 0
    val assessment = decision.planAssessment ?: return 0 to 0
    var mismatches = 0
    var checked = 0
    for (branch in assessment.branches) {
        val expected = branch.followUp ?: continue
        val live = before.play(decision.move).play(Move.parseUci(branch.reply))
        val choice = policy.choose(live, plan, decision.difficulty.planWindowCp)
        checked++
        if ((choice as? PolicyChoice.Act)?.action != expected) mismatches++
    }
    return mismatches to checked
}

/** p-th percentile of [values] (nearest rank). */
fun percentile(values: List<Long>, p: Double): Long {
    if (values.isEmpty()) return 0
    val sorted = values.sorted()
    val rank = kotlin.math.ceil(p / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)
    return sorted[rank - 1]
}

fun median(values: List<Int>): Double {
    if (values.isEmpty()) return 0.0
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle].toDouble() else (sorted[middle - 1] + sorted[middle]) / 2.0
}
