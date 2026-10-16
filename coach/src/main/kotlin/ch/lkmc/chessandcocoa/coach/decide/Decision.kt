// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach.decide

import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.chess.Square
import ch.lkmc.chessandcocoa.coach.analysis.AnalystBudgets
import ch.lkmc.chessandcocoa.coach.plan.PlanAssessment
import ch.lkmc.chessandcocoa.coach.plan.PlanFamily
import ch.lkmc.chessandcocoa.coach.plan.PlanInstance
import ch.lkmc.chessandcocoa.coach.plan.PlanStatus
import ch.lkmc.chessandcocoa.coach.plan.TransitionReason
import ch.lkmc.chessandcocoa.engine.EngineProfile
import ch.lkmc.chessandcocoa.engine.Score
import ch.lkmc.chessandcocoa.engine.ScoreBound
import kotlinx.serialization.Serializable

/** Whether a detected threat rules out a deliberate weaker move. */
internal enum class ConcessionScope { QUIET_ONLY, INCLUDING_DEFENSE }

/**
 * Opponent strength profiles (concept Section 9.3). They weaken the
 * opponent's own move selection only; the analyst that checks the human and
 * every explanation keeps full strength (R9). Values are starting
 * hypotheses awaiting calibration, not measured Elo.
 */
@Serializable
enum class Difficulty(
    /** Node budget of the opponent's own root search: a smaller budget really does see less. */
    val selectionNodes: Long,
    val candidateCount: Int,
    /** Plans tested per decision. */
    val maxPlans: Int,
    /** How much worse than the best move (centipawns) a plan move may be. */
    val planWindowCp: Int,
    /** Chance, in percent, of a deliberately weaker move when no plan move is chosen. */
    val concessionPercent: Int,
    val concessionMaxLossCp: Int,
    internal val concessionScope: ConcessionScope = ConcessionScope.QUIET_ONLY,
) {
    GENTLE(selectionNodes = 2_000, candidateCount = 12, maxPlans = 1, planWindowCp = 80, concessionPercent = 70, concessionMaxLossCp = 400,
        concessionScope = ConcessionScope.INCLUDING_DEFENSE),
    STEADY(selectionNodes = 80_000, candidateCount = 4, maxPlans = 2, planWindowCp = 50, concessionPercent = 10, concessionMaxLossCp = 120),
    KEEN(selectionNodes = 250_000, candidateCount = 4, maxPlans = 2, planWindowCp = 30, concessionPercent = 0, concessionMaxLossCp = 0),
}

/** Why the move was chosen (concept Section 9.1). */
@Serializable
enum class SelectionBasis { PLAN, IMMEDIATE_OBLIGATION, TACTICAL_OPPORTUNITY, DIFFICULTY_CONCESSION, TEACHING_PREFERENCE, FALLBACK }

/**
 * What role the plan played (Section 11.1). Only INTRODUCED_CANDIDATE,
 * DECISIVE and TIEBREAK allow saying the plan was the reason for the move;
 * CONCURRENT means the plan merely fits it.
 */
@Serializable
enum class PlanRole { INTRODUCED_CANDIDATE, DECISIVE, TIEBREAK, CONCURRENT, NONE }

@Serializable
enum class Obligation { FORCED_MOVE, IN_CHECK, MATE_THREAT, MATERIAL_THREAT }

/** A root candidate's evidence values, from the mover's point of view. */
@Serializable
data class CandidateValue(
    val move: Move,
    val san: String,
    val score: Score,
    val bound: ScoreBound,
    val depth: Int,
    val rank: Int,
)

@Serializable
enum class ConsiderationOutcome { CHOSEN, NO_ROUTE, TOO_COSTLY, GOAL_FAILED, REFUTED, OUTRANKED, SKIPPED_FOR_OBLIGATION }

/** A plan the selector tested, kept even when rejected. */
@Serializable
data class PlanConsideration(
    val episodeId: String,
    val family: PlanFamily,
    val target: Square,
    val outcome: ConsiderationOutcome,
    val policyMove: Move? = null,
    val policySan: String? = null,
    val assessment: PlanAssessment? = null,
    /** For TOO_COSTLY: how far (centipawns) the best plan action fell short of the best move. */
    val costCp: Int? = null,
)

/** A lifecycle change made during a decision. */
@Serializable
data class PlanTransition(
    val episodeId: String,
    val family: PlanFamily,
    val from: PlanStatus?,
    val to: PlanStatus,
    val reason: TransitionReason,
    val ply: Int,
)

/**
 * The frozen record of one computer decision (concept Section 11.1). It is
 * written before the human's response is known and never edited afterwards
 * (R2); evidence is stored as values, because a re-run can differ.
 */
@Serializable
data class DecisionRecord(
    val id: String,
    val gameId: String,
    val branchId: String,
    val revision: Int,
    /** Ply of the position the decision was made in. */
    val ply: Int,
    val side: Side,
    val fen: String,
    val history: List<String>,
    val move: Move,
    val san: String,
    val selectionBasis: SelectionBasis,
    val planRole: PlanRole,
    /** Snapshot of the chosen plan, including the revision this decision made. */
    val plan: PlanInstance? = null,
    val planAssessment: PlanAssessment? = null,
    val considered: List<PlanConsideration> = emptyList(),
    val candidates: List<CandidateValue>,
    /** What the same selection would have played with plans removed (the ablation of Section 16.3). */
    val withoutPlanMove: Move,
    val obligations: List<Obligation> = emptyList(),
    val transitions: List<PlanTransition> = emptyList(),
    val difficulty: Difficulty,
    val engineName: String,
    val engineProfile: EngineProfile,
    val budgets: AnalystBudgets,
    val searches: Int,
    val nodes: Long,
) {
    /** Whether the plan may be described as the reason for the move (Section 9.1). */
    val planIsReason: Boolean
        get() = selectionBasis == SelectionBasis.PLAN &&
            planRole in setOf(PlanRole.INTRODUCED_CANDIDATE, PlanRole.DECISIVE, PlanRole.TIEBREAK)
}
