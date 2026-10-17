// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach.game

import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.chess.Position
import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.coach.analysis.AnalystBudgets
import ch.lkmc.chessandcocoa.coach.analysis.Searcher
import ch.lkmc.chessandcocoa.coach.assess.MoveAssessor
import ch.lkmc.chessandcocoa.coach.decide.DecisionInput
import ch.lkmc.chessandcocoa.coach.decide.DecisionRecord
import ch.lkmc.chessandcocoa.coach.decide.Difficulty
import ch.lkmc.chessandcocoa.coach.decide.MoveSelector
import ch.lkmc.chessandcocoa.coach.decide.PlanTransition
import ch.lkmc.chessandcocoa.coach.evidence.Claim
import ch.lkmc.chessandcocoa.coach.evidence.ClaimBuilder
import ch.lkmc.chessandcocoa.coach.evidence.ClaimVerifier
import ch.lkmc.chessandcocoa.coach.plan.PlanStatus
import ch.lkmc.chessandcocoa.coach.render.Renderer
import ch.lkmc.chessandcocoa.coach.teach.ExperiencePreferences
import ch.lkmc.chessandcocoa.coach.teach.GameReview
import ch.lkmc.chessandcocoa.coach.teach.Hint
import ch.lkmc.chessandcocoa.coach.teach.HintLevel
import ch.lkmc.chessandcocoa.coach.teach.Hints
import ch.lkmc.chessandcocoa.coach.teach.OpponentIntent
import ch.lkmc.chessandcocoa.coach.teach.Reviews
import ch.lkmc.chessandcocoa.coach.teach.TeachingEvent
import ch.lkmc.chessandcocoa.coach.teach.TeachingPolicy

/** A step's new record and the teaching event to show, if any. */
data class StepResult(val record: GameRecord, val event: TeachingEvent?)

/**
 * The coach's public face: the only API the app's session layer uses.
 * Stateless; every call takes a [GameRecord] and returns a new one with a
 * bumped revision, so the caller can drop results for stale revisions (R11).
 *
 * ```
 *   session ──▶ Coach ──▶ MoveSelector  (opponent decision + plans)
 *                    ├──▶ MoveAssessor  (two-axis human assessment)
 *                    ├──▶ ClaimBuilder ─▶ TeachingPolicy ─▶ Renderer
 *                    └──▶ Hints, Reviews
 * ```
 */
class Coach(searcher: Searcher, private val budgets: AnalystBudgets = AnalystBudgets()) {

    private val selector = MoveSelector(searcher, budgets)
    private val assessor = MoveAssessor(searcher, budgets)

    fun newGame(
        gameId: String,
        humanSide: Side,
        difficulty: Difficulty,
        seed: Long,
        nowMillis: Long,
        initialFen: String = Position.START_FEN,
    ): GameRecord = GameRecord(
        gameId = gameId,
        branchId = "b0",
        revision = 0,
        humanSide = humanSide,
        difficulty = difficulty,
        seed = seed,
        initialFen = initialFen,
        moves = emptyList(),
        createdAtMillis = nowMillis,
        updatedAtMillis = nowMillis,
    )

    /** Warms the analyst on the human's decision while they think (Section 14.2). Safe to cancel. */
    suspend fun prepareHumanTurn(record: GameRecord) {
        if (!record.isHumanTurn) return
        assessor.analyzeDecision(record.game)
    }

    /** Assesses and plays the human's [move]; the event explains it (Section 4.1). */
    suspend fun playHumanMove(
        record: GameRecord,
        move: Move,
        preferences: ExperiencePreferences,
        nowMillis: Long,
    ): StepResult {
        require(record.isHumanTurn) { "Not the human's turn" }
        val parent = record.game
        require(parent.current.isLegal(move)) { "Illegal move: ${move.uci}" }

        val prior = record.lastDecision
        val priorPlan = prior?.plan?.let { snapshot -> record.episodes.firstOrNull { it.episodeId == snapshot.episodeId } }
            ?.takeIf { it.status == PlanStatus.ACTIVE }
        val ply = parent.ply

        val assessment = assessor.assess(parent, move, prior, priorPlan, record.difficulty.planWindowCp, record.assistedAt(ply))
        val claims = ClaimVerifier.verified(ClaimBuilder.forHumanMove(parent, assessment, prior, priorPlan, emptyList()), parent.play(move))
        val label = moveLabel(parent.current, move)
        val event = TeachingPolicy.select(claims, preferences, "e-${record.branchId}-$ply", ply + 1, record.revision + 1, label)

        val revealed = event?.claims?.filterIsInstance<Claim.PlanRevealed>()?.map { it.episodeId }.orEmpty()
        val next = record.copy(
            revision = record.revision + 1,
            moves = record.moves + move,
            assessments = record.assessments + assessment,
            events = record.events + listOfNotNull(event),
            revealedEpisodes = record.revealedEpisodes + revealed,
            updatedAtMillis = nowMillis,
        )
        return StepResult(next, event)
    }

    /** Decides and plays the opponent's move. Disclosure is handled separately from selection. */
    suspend fun playComputerMove(record: GameRecord, preferences: ExperiencePreferences, nowMillis: Long): StepResult {
        require(record.outcome == null && !record.isHumanTurn) { "Not the opponent's turn" }
        val game = record.game

        val outcome = selector.decide(
            DecisionInput(
                game = game,
                gameId = record.gameId,
                branchId = record.branchId,
                revision = record.revision,
                difficulty = record.difficulty,
                episodes = record.episodes,
                seed = record.seed,
            ),
        )
        val decision = outcome.record
        val after = game.play(decision.move)

        // Only plans the player has already seen may be narrated as pausing or ending (no spoilers).
        val claims = buildList {
            after.outcome()?.let { add(Claim.GameOver(it.winner, it.reason, record.humanSide)) }
            decision.transitions
                .filter { it.episodeId in record.revealedEpisodes && isNarrated(it) }
                .forEach { transition ->
                    val episode = outcome.episodes.first { it.episodeId == transition.episodeId }
                    add(Claim.PlanTransitionClaim(episode.family, episode.current.targetSquare, transition.reason))
                }
        }
        val label = moveLabel(game.current, decision.move)
        val event = TeachingPolicy.select(claims, preferences, "e-${record.branchId}-${game.ply}", game.ply + 1, record.revision + 1, label)

        val next = record.copy(
            revision = record.revision + 1,
            moves = record.moves + decision.move,
            decisions = record.decisions + decision,
            episodes = outcome.episodes,
            events = record.events + listOfNotNull(event),
            updatedAtMillis = nowMillis,
        )
        return StepResult(next, event)
    }

    /** Opt-in early disclosure; the next reply stays assisted even if the player hides the aim. */
    fun revealOpponentIntent(record: GameRecord, nowMillis: Long): Pair<GameRecord, OpponentIntent?> {
        if (!record.isHumanTurn) return record to null
        val decision = record.lastDecision ?: return record to null
        if (!ClaimVerifier.verifyIntent(decision, record.game)) return record to null

        val before = record.game.positions[decision.ply]
        val intent = OpponentIntent(decision.id, moveLabel(before, decision.move), Renderer.intent(decision))
        if (record.ply in record.intentReveals) return record to intent

        val revealed = record.copy(
            revision = record.revision + 1,
            intentReveals = record.intentReveals + record.ply,
            revealedEpisodes = record.revealedEpisodes + listOfNotNull(decision.plan?.episodeId),
            updatedAtMillis = nowMillis,
        )
        return revealed to intent
    }

    /** A progressive hint for the human's current decision; the move becomes assisted. */
    suspend fun hint(record: GameRecord, level: HintLevel, nowMillis: Long): Pair<GameRecord, Hint> {
        require(record.isHumanTurn) { "Hints are for the human's turn" }
        val analysis = assessor.analyzeDecision(record.game)
        val plan = record.openEpisode?.takeIf { it.status == PlanStatus.ACTIVE }
        val hint = Hints.hint(record.game, level, plan, analysis)
        val next = record.copy(hints = record.hints + HintUse(record.ply, level), updatedAtMillis = nowMillis)
        return next to hint
    }

    /**
     * Takes back to the human's previous decision. The given-up line is kept
     * as an abandoned branch; plan episodes return to their state at that
     * point; new play starts a new branch (Section 14.3).
     */
    fun takeBack(record: GameRecord, nowMillis: Long): GameRecord {
        val targetPly = previousHumanPly(record) ?: return record
        val branchNumber = record.abandoned.size + 1

        val abandoned = AbandonedBranch(
            branchId = record.branchId,
            fromPly = targetPly,
            moves = record.moves.drop(targetPly),
            decisions = record.decisions.filter { it.ply >= targetPly },
            assessments = record.assessments.filter { it.ply >= targetPly },
            events = record.events.filter { it.ply > targetPly },
            hints = record.hints.filter { it.ply > targetPly },
            intentReveals = record.intentReveals.filter { it > targetPly }.toSet(),
        )
        return record.copy(
            branchId = "b$branchNumber",
            revision = record.revision + 1,
            moves = record.moves.take(targetPly),
            decisions = record.decisions.filter { it.ply < targetPly },
            assessments = record.assessments.filter { it.ply < targetPly },
            episodes = record.episodes.mapNotNull { it.asOf(targetPly - 1) },
            events = record.events.filter { it.ply <= targetPly },
            // The player is back at a decision whose hints they have seen: it stays assisted.
            hints = record.hints.filter { it.ply <= targetPly },
            intentReveals = record.intentReveals.filter { it <= targetPly }.toSet(),
            abandoned = record.abandoned + abandoned,
            resignedBy = null,
            updatedAtMillis = nowMillis,
        )
    }

    fun resign(record: GameRecord, nowMillis: Long): GameRecord {
        if (record.outcome != null) return record
        return record.copy(revision = record.revision + 1, resignedBy = record.humanSide, updatedAtMillis = nowMillis)
    }

    fun review(record: GameRecord): GameReview = Reviews.review(record)

    /** The last decision recorded before the current position, for evidence views. */
    fun decisionBefore(record: GameRecord): DecisionRecord? = record.lastDecision

    /** An active plan pausing or ending, or a suspended one ending; resuming speaks through the next move. */
    private fun isNarrated(transition: PlanTransition): Boolean = when (transition.from) {
        PlanStatus.ACTIVE -> transition.to != PlanStatus.ACTIVE
        PlanStatus.SUSPENDED -> transition.to != PlanStatus.ACTIVE && transition.to != PlanStatus.SUSPENDED
        else -> false
    }

    private fun moveLabel(position: Position, move: Move): String =
        Renderer.moveLabel(position.fullmoveNumber, position.sideToMove == Side.WHITE, position.san(move))

    /** The ply of the human's most recent decision point strictly before now, or null when there is none. */
    private fun previousHumanPly(record: GameRecord): Int? {
        val humanPlies = record.assessments.map { it.ply }
        return humanPlies.lastOrNull { it < record.ply }
    }
}
