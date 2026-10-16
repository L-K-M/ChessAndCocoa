// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach.decide

import ch.lkmc.chessandcocoa.chess.Game
import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.chess.PieceType
import ch.lkmc.chessandcocoa.coach.analysis.AnalystBudgets
import ch.lkmc.chessandcocoa.coach.analysis.GameSearch
import ch.lkmc.chessandcocoa.coach.analysis.ScoreMath
import ch.lkmc.chessandcocoa.coach.analysis.Searcher
import ch.lkmc.chessandcocoa.coach.facts.Facts
import ch.lkmc.chessandcocoa.coach.plan.PlanAssessment
import ch.lkmc.chessandcocoa.coach.plan.PlanInstance
import ch.lkmc.chessandcocoa.coach.plan.PlanPolicy
import ch.lkmc.chessandcocoa.coach.plan.PlanProbe
import ch.lkmc.chessandcocoa.coach.plan.PlanStatus
import ch.lkmc.chessandcocoa.coach.plan.PlanTemplate
import ch.lkmc.chessandcocoa.coach.plan.Planner
import ch.lkmc.chessandcocoa.coach.plan.PolicyBlock
import ch.lkmc.chessandcocoa.coach.plan.PolicyChoice
import ch.lkmc.chessandcocoa.coach.plan.TransitionReason
import java.util.SplittableRandom

/** Everything a decision may depend on; nothing learned later can enter (R2). */
data class DecisionInput(
    val game: Game,
    val gameId: String,
    val branchId: String,
    val revision: Int,
    val difficulty: Difficulty,
    /** Every plan episode of this branch so far, open or closed. */
    val episodes: List<PlanInstance>,
    /** Per-game seed: concessions are reproducible for the same game and ply. */
    val seed: Long,
    /** Ablation switch for the plan-influence experiment: no plans considered at all. */
    val plansEnabled: PlansSwitch = PlansSwitch.ENABLED,
)

enum class PlansSwitch { ENABLED, DISABLED }

data class DecisionOutcome(val record: DecisionRecord, val episodes: List<PlanInstance>)

/**
 * Chooses the computer's move: quality first, then plan preferences
 * (concept Section 9.1).
 *
 * ```
 *   revalidate episodes ─▶ root search (difficulty budget)
 *        │                         │
 *        ▼                         ▼
 *   obligations? ── yes ─▶ best move, plans suspended
 *        │ no
 *        ▼
 *   for each plan: policy move ─▶ one-reply probe ─▶ holds?
 *        │
 *        ▼
 *   chosen plan move, or baseline (best / tactic / concession)
 *        │
 *        ▼
 *   frozen DecisionRecord + updated episodes
 * ```
 */
class MoveSelector(private val searcher: Searcher, private val budgets: AnalystBudgets) {

    private val policy = PlanPolicy(searcher, budgets)
    private val probe = PlanProbe(searcher, policy, budgets)

    suspend fun decide(input: DecisionInput): DecisionOutcome {
        val game = input.game
        val position = game.current
        val side = position.sideToMove
        require(game.outcome() == null) { "No decision in a finished game" }

        val searchesBefore = searcher.searchCount
        val nodesBefore = searcher.nodeCount
        val transitions = mutableListOf<PlanTransition>()

        // 1. Lifecycle: completed, lost or broken episodes are closed first.
        var episodes = input.episodes.map { episode ->
            val updated = if (episode.isOpen) Planner.revalidate(game, episode) else episode
            if (updated !== episode) transitions += transitionOf(updated, episode.status)
            updated
        }

        // 2. The opponent's own root search, with its difficulty budget.
        val legal = position.legalMoves()
        val root = searcher.search(game, input.difficulty.selectionNodes, multiPv = minOf(input.difficulty.candidateCount, legal.size))
        val candidates = candidateValues(root)
        val obligations = obligations(game)
        val baseline = baseline(root, input, obligations)

        // 3. Plans, unless an immediate obligation leaves no room for them.
        val considered = mutableListOf<PlanConsideration>()
        val accepted = mutableListOf<Accepted>()
        val blocking = obligations.any { it == Obligation.FORCED_MOVE || it == Obligation.IN_CHECK }
        val open = episodes.filter { it.isOpen }

        if (input.plansEnabled == PlansSwitch.ENABLED && !blocking) {
            val pool = (open + Planner.propose(game, side, open)).take(input.difficulty.maxPlans)
            for (plan in pool) {
                evaluate(game, plan, input.difficulty, considered, accepted)
            }
        } else if (blocking) {
            open.forEach { considered += consideration(it, ConsiderationOutcome.SKIPPED_FOR_OBLIGATION) }
        }

        // 4. Choose: an open episode keeps priority (continuity), then the most advanced step.
        val chosen = accepted.sortedWith(
            compareByDescending<Accepted> { it.plan.status != PlanStatus.PROPOSED }
                .thenByDescending { PlanTemplate.advancement(it.choice.action.subgoal) }
                .thenByDescending { it.choice.score.rank() },
        ).firstOrNull()

        val move: Move
        val basis: SelectionBasis
        val role: PlanRole
        if (chosen != null) {
            move = chosen.choice.action.move
            basis = SelectionBasis.PLAN
            role = roleOf(move, baseline.first, root)
            considered.replaceAll { if (it.episodeId == chosen.plan.episodeId) it.copy(outcome = ConsiderationOutcome.CHOSEN) else it }
        } else {
            move = baseline.first
            basis = baseline.second
            role = PlanRole.NONE
        }

        // 5. Episode bookkeeping: one primary plan at a time (Section 7.4).
        val chosenEpisode = chosen?.let { adopt(game, it) }
        episodes = updateEpisodes(game, episodes, chosen, chosenEpisode, considered, blocking, transitions)

        val record = DecisionRecord(
            id = "d-${input.branchId}-${game.ply}",
            gameId = input.gameId,
            branchId = input.branchId,
            revision = input.revision,
            ply = game.ply,
            side = side,
            fen = position.fen(),
            history = game.uciMoves(),
            move = move,
            san = position.san(move),
            selectionBasis = basis,
            planRole = role,
            plan = chosenEpisode,
            planAssessment = chosen?.assessment,
            considered = considered.toList(),
            candidates = candidates,
            withoutPlanMove = baseline.first,
            obligations = obligations,
            transitions = transitions.toList(),
            difficulty = input.difficulty,
            engineName = root.result.engineName,
            engineProfile = searcher.engineProfile,
            budgets = budgets,
            searches = searcher.searchCount - searchesBefore,
            nodes = searcher.nodeCount - nodesBefore,
        )
        return DecisionOutcome(record, episodes)
    }

    private suspend fun evaluate(
        game: Game,
        plan: PlanInstance,
        difficulty: Difficulty,
        considered: MutableList<PlanConsideration>,
        accepted: MutableList<Accepted>,
    ) {
        when (val choice = policy.choose(game, plan, difficulty.planWindowCp)) {
            is PolicyChoice.Blocked -> {
                val cost = if (choice.bestActionScore != null && choice.referenceScore != null) {
                    ScoreMath.lossCp(choice.referenceScore, choice.bestActionScore)
                } else {
                    null
                }
                considered += consideration(plan, outcomeOf(choice.reason)).copy(costCp = cost)
            }
            is PolicyChoice.Act -> {
                val assessment = probe.probe(game, plan, choice, difficulty.planWindowCp)
                val san = game.current.san(choice.action.move)
                if (!assessment.holds) {
                    considered += consideration(plan, ConsiderationOutcome.REFUTED, choice.action.move, san, assessment)
                    return
                }
                considered += consideration(plan, ConsiderationOutcome.OUTRANKED, choice.action.move, san, assessment)
                accepted += Accepted(plan, choice, assessment)
            }
        }
    }

    /** The move this selection plays without plans: the best line, a tactic, or a disclosed concession. */
    private fun baseline(root: GameSearch, input: DecisionInput, obligations: List<Obligation>): Pair<Move, SelectionBasis> {
        val position = input.game.current
        val side = position.sideToMove
        val lines = root.result.lines
        val best = Move.parseUci(lines.first().move)
        val bestScore = root.score(lines.first(), side)

        if (obligations.isNotEmpty()) return best to SelectionBasis.IMMEDIATE_OBLIGATION

        if (input.difficulty.concessionPercent > 0 && !ScoreMath.isWinningMate(bestScore)) {
            val random = SplittableRandom(input.seed * SEED_MIX + input.game.ply)
            if (random.nextInt(PERCENT) < input.difficulty.concessionPercent) {
                val options = lines.drop(1).filter { line ->
                    val loss = ScoreMath.lossCp(bestScore, root.score(line, side))
                    loss != null && loss <= input.difficulty.concessionMaxLossCp
                }
                if (options.isNotEmpty()) {
                    return Move.parseUci(options[random.nextInt(options.size)].move) to SelectionBasis.DIFFICULTY_CONCESSION
                }
            }
        }

        val tactic = ScoreMath.isWinningMate(bestScore) || (position.isCapture(best) && position.see(best) > 0)
        return best to if (tactic) SelectionBasis.TACTICAL_OPPORTUNITY else SelectionBasis.FALLBACK
    }

    /** Role relative to what the selection would have played without plans (Section 16.3 ablation). */
    private fun roleOf(move: Move, withoutPlan: Move, root: GameSearch): PlanRole {
        if (move == withoutPlan) return PlanRole.CONCURRENT
        val side = root.rootSide
        val line = root.lineFor(move) ?: return PlanRole.INTRODUCED_CANDIDATE
        val reference = root.lineFor(withoutPlan) ?: return PlanRole.DECISIVE
        val loss = ScoreMath.lossCp(root.score(reference, side), root.score(line, side))
        return if (loss != null && loss <= TIE_CP) PlanRole.TIEBREAK else PlanRole.DECISIVE
    }

    private fun obligations(game: Game): List<Obligation> {
        val position = game.current
        val opponent = position.sideToMove.opposite
        return buildList {
            if (position.legalMoves().size == 1) add(Obligation.FORCED_MOVE)
            if (position.isCheck()) add(Obligation.IN_CHECK)
            if (!position.isCheck() && Facts.threatenedMates(position, opponent).isNotEmpty()) add(Obligation.MATE_THREAT)
            if (Facts.winningCaptures(position, opponent).any { it.second >= MATERIAL_THREAT_CP }) add(Obligation.MATERIAL_THREAT)
        }
    }

    private fun adopt(game: Game, accepted: Accepted): PlanInstance {
        val plan = accepted.plan
        val action = accepted.choice.action
        val square = PlanTemplate.of(plan.family).locate(game, plan) ?: plan.current.targetSquare
        val dependencies = PlanTemplate.of(plan.family).dependencies(game, plan)
        val reason = when (plan.status) {
            PlanStatus.PROPOSED -> TransitionReason.ADOPTED
            PlanStatus.SUSPENDED -> TransitionReason.RESUMED
            else -> {
                val previous = plan.revisions.lastOrNull { it.action != null }?.action
                val samePiece = previous != null && previous.move.to == action.move.from
                if (previous == null || samePiece || previous.subgoal == action.subgoal) TransitionReason.CONTINUED else TransitionReason.ROUTE_CHANGED
            }
        }
        return plan.revise(game.ply, PlanStatus.ACTIVE, reason, square, dependencies, action)
    }

    private fun updateEpisodes(
        game: Game,
        episodes: List<PlanInstance>,
        chosen: Accepted?,
        chosenEpisode: PlanInstance?,
        considered: List<PlanConsideration>,
        blocking: Boolean,
        transitions: MutableList<PlanTransition>,
    ): List<PlanInstance> {
        val ply = game.ply
        val updated = episodes.map { episode ->
            if (!episode.isOpen) return@map episode
            if (chosenEpisode != null && episode.episodeId == chosenEpisode.episodeId) return@map chosenEpisode

            val outcome = considered.firstOrNull { it.episodeId == episode.episodeId }?.outcome
            val next = when {
                chosenEpisode != null -> episode.revise(ply, PlanStatus.SUPERSEDED, TransitionReason.BETTER_PLAN)
                blocking -> if (episode.status == PlanStatus.SUSPENDED) episode
                else episode.revise(ply, PlanStatus.SUSPENDED, TransitionReason.OBLIGATION_TOOK_PRIORITY)
                outcome == null -> episode
                episode.status == PlanStatus.SUSPENDED -> episode.revise(ply, PlanStatus.ABANDONED, reasonOf(outcome))
                else -> episode.revise(ply, PlanStatus.SUSPENDED, reasonOf(outcome))
            }
            if (next !== episode) transitions += transitionOf(next, episode.status)
            next
        }

        if (chosen == null || chosenEpisode == null) return updated

        // The chosen episode's own transition; a newly adopted one joins the history.
        val existed = episodes.any { it.episodeId == chosenEpisode.episodeId }
        transitions += transitionOf(chosenEpisode, chosen.plan.status)
        return if (existed) updated else updated + chosenEpisode
    }

    private fun candidateValues(root: GameSearch): List<CandidateValue> {
        val position = root.game.current
        return root.result.lines.mapIndexed { index, line ->
            val move = Move.parseUci(line.move)
            CandidateValue(move, position.san(move), root.score(line, root.rootSide), line.bound, line.depth, index + 1)
        }
    }

    private fun consideration(
        plan: PlanInstance,
        outcome: ConsiderationOutcome,
        move: Move? = null,
        san: String? = null,
        assessment: PlanAssessment? = null,
    ) = PlanConsideration(plan.episodeId, plan.family, plan.current.targetSquare, outcome, move, san, assessment)

    private fun outcomeOf(block: PolicyBlock): ConsiderationOutcome = when (block) {
        PolicyBlock.NO_ROUTE -> ConsiderationOutcome.NO_ROUTE
        PolicyBlock.TOO_COSTLY -> ConsiderationOutcome.TOO_COSTLY
        PolicyBlock.GOAL_FAILED -> ConsiderationOutcome.GOAL_FAILED
    }

    private fun reasonOf(outcome: ConsiderationOutcome): TransitionReason = when (outcome) {
        ConsiderationOutcome.REFUTED -> TransitionReason.REFUTED_IN_PROBE
        ConsiderationOutcome.SKIPPED_FOR_OBLIGATION -> TransitionReason.OBLIGATION_TOOK_PRIORITY
        ConsiderationOutcome.OUTRANKED -> TransitionReason.BETTER_PLAN
        else -> TransitionReason.NO_ACCEPTABLE_ACTION
    }

    private fun transitionOf(episode: PlanInstance, from: PlanStatus?) = PlanTransition(
        episodeId = episode.episodeId,
        family = episode.family,
        from = from,
        to = episode.status,
        reason = episode.current.reason,
        ply = episode.current.ply,
    )

    private data class Accepted(val plan: PlanInstance, val choice: PolicyChoice.Act, val assessment: PlanAssessment)

    private companion object {
        /** Score difference treated as a tie between two candidates (Section 9.1 "broke a tie"). */
        const val TIE_CP = 10
        /** A minor piece net of a pawn: big enough to demand an answer. */
        const val MATERIAL_THREAT_CP = 200
        const val PERCENT = 100
        const val SEED_MIX = 1_000_003L
    }
}
