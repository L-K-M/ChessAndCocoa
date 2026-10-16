// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach.plan

import ch.lkmc.chessandcocoa.chess.Game
import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.coach.analysis.AnalystBudgets
import ch.lkmc.chessandcocoa.coach.analysis.ScoreMath
import ch.lkmc.chessandcocoa.coach.analysis.Searcher
import ch.lkmc.chessandcocoa.engine.Score
import kotlinx.serialization.Serializable

/** How a tested reply was chosen (concept Section 8.3). */
@Serializable
enum class ReplySource { ENGINE_RANKED, TEMPLATE_CHALLENGE }

/** What the plan policy could do after a tested reply. */
@Serializable
enum class BranchOutcome(val refutes: Boolean) {
    CONTINUES(refutes = false),
    GOAL_REACHED(refutes = false),
    BLOCKED(refutes = true),
    /** The reply ends the game (mate against the plan's owner, or a draw): the plan cannot go on. */
    GAME_OVER(refutes = true),
}

/** One tested reply and the plan policy's answer to it. Values are stored, not references (Section 11.1). */
@Serializable
data class ProbeBranch(
    val reply: String,
    val replySan: String,
    val source: ReplySource,
    /** Engine rank among replies (1 = best), null for a template challenge outside the ranked set. */
    val rank: Int?,
    /** The reply's score from the opponent's point of view. */
    val replyScore: Score,
    /** Within [AnalystBudgets.strongReplyCp] of the best reply: a "strong" reply. */
    val strong: Boolean,
    val outcome: BranchOutcome,
    val followUp: PlanAction? = null,
    val followUpSan: String? = null,
    val followUpScore: Score? = null,
    val block: PolicyBlock? = null,
    val dependencies: List<DependencyState> = emptyList(),
)

/**
 * The inspectable result of testing a plan one reply deep (Section 8.4):
 * a vector of findings, not a single plan score.
 */
@Serializable
data class PlanAssessment(
    val planMove: PlanAction,
    val planMoveSan: String,
    /** Chess quality: the plan move's score and the best unrestricted score, owner's view. */
    val planMoveScore: Score,
    val referenceScore: Score,
    val referenceMove: String?,
    val branches: List<ProbeBranch>,
    /** True when no strong tested reply blocks the plan or ends the game. */
    val holds: Boolean,
    /** Horizon in opponent replies; the on-device probe tests one (Section 8.2). */
    val horizon: Int = 1,
    val selectionMethod: String,
) {
    /** Strong replies after which the plan cannot continue. */
    val refutations: List<ProbeBranch> get() = branches.filter { it.strong && it.outcome.refutes }

    /** Persistence cost in centipawns (best unrestricted minus plan move), null across mate scores. */
    val persistenceCostCp: Int? get() = ScoreMath.lossCp(referenceScore, planMoveScore)

    /** Distinct follow-up actions found across branches: a crude contingency measure. */
    val contingencyRoutes: Int get() = branches.mapNotNull { it.followUp?.move }.distinct().size

    fun branchFor(reply: Move): ProbeBranch? = branches.firstOrNull { it.reply == reply.uci }
}

/**
 * The one-reply plan probe (concept Section 8.2): after the plan's move,
 * test the strongest replies plus one template challenge, and apply the
 * plan policy once after each. Rejects plans an immediate reply refutes.
 *
 * ```
 *   plan move ──▶ reply 1 ──▶ policy ──▶ continues / blocked
 *             ├─▶ reply 2 ──▶ policy
 *             ├─▶ reply 3 ──▶ policy
 *             └─▶ challenge ─▶ policy
 * ```
 */
class PlanProbe(
    private val searcher: Searcher,
    private val policy: PlanPolicy,
    private val budgets: AnalystBudgets,
) {

    suspend fun probe(game: Game, plan: PlanInstance, choice: PolicyChoice.Act, windowCp: Int): PlanAssessment {
        val template = PlanTemplate.of(plan.family)
        val planMove = choice.action.move
        val afterPlan = game.play(planMove)
        val method = "top ${budgets.probeReplies} engine replies + 1 template challenge, policy once per reply"

        fun assessment(branches: List<ProbeBranch>, holds: Boolean) = PlanAssessment(
            planMove = choice.action,
            planMoveSan = game.current.san(planMove),
            planMoveScore = choice.score,
            referenceScore = choice.referenceScore,
            referenceMove = choice.referenceMove,
            branches = branches,
            holds = holds,
            selectionMethod = method,
        )

        // The plan move ends the game (mate): nothing to resist.
        if (afterPlan.outcome() != null) return assessment(emptyList(), holds = true)

        val replies = searcher.search(afterPlan, budgets.probeReplyNodes, multiPv = budgets.probeReplies)
        val opponent = plan.owner.opposite
        val bestReply = replies.bestScore(opponent) ?: return assessment(emptyList(), holds = true)

        val tested = replies.result.lines.mapIndexed { index, line ->
            TestedReply(Move.parseUci(line.move), ReplySource.ENGINE_RANKED, index + 1, replies.score(line, opponent))
        }.toMutableList()

        // One reply aimed at the plan's dependencies, if the engine's top replies include none.
        val challenges = template.challenges(afterPlan, plan).filter { c -> tested.none { it.move == c } }
        if (challenges.isNotEmpty()) {
            val strongest = searcher.search(afterPlan, budgets.probeReplyNodes, searchMoves = challenges)
            strongest.result.best?.let { line ->
                tested += TestedReply(Move.parseUci(line.move), ReplySource.TEMPLATE_CHALLENGE, null, strongest.score(line, opponent))
            }
        }

        val branches = tested.map { reply ->
            val strong = ScoreMath.withinWindow(reply.score, bestReply, budgets.strongReplyCp)
            branch(afterPlan, plan, reply, strong, windowCp)
        }

        val holds = branches.none { it.strong && it.outcome.refutes }
        return assessment(branches, holds)
    }

    private suspend fun branch(
        afterPlan: Game,
        plan: PlanInstance,
        reply: TestedReply,
        strong: Boolean,
        windowCp: Int,
    ): ProbeBranch {
        val template = PlanTemplate.of(plan.family)
        val afterReply = afterPlan.play(reply.move)
        val base = ProbeBranch(
            reply = reply.move.uci,
            replySan = afterPlan.current.san(reply.move),
            source = reply.source,
            rank = reply.rank,
            replyScore = reply.score,
            strong = strong,
            outcome = BranchOutcome.GAME_OVER,
            dependencies = template.dependencies(afterReply, plan),
        )
        if (afterReply.outcome() != null) return base
        if (template.goal(afterReply, plan) == GoalState.REACHED) return base.copy(outcome = BranchOutcome.GOAL_REACHED)

        return when (val choice = policy.choose(afterReply, plan, windowCp)) {
            is PolicyChoice.Act -> base.copy(
                outcome = BranchOutcome.CONTINUES,
                followUp = choice.action,
                followUpSan = afterReply.current.san(choice.action.move),
                followUpScore = choice.score,
            )
            is PolicyChoice.Blocked -> base.copy(outcome = BranchOutcome.BLOCKED, block = choice.reason)
        }
    }

    private data class TestedReply(val move: Move, val source: ReplySource, val rank: Int?, val score: Score)
}
