// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach.teach

import ch.lkmc.chessandcocoa.chess.GameOutcome
import ch.lkmc.chessandcocoa.chess.Square
import ch.lkmc.chessandcocoa.coach.evidence.Claim
import ch.lkmc.chessandcocoa.coach.evidence.ClaimBuilder
import ch.lkmc.chessandcocoa.coach.game.GameRecord
import ch.lkmc.chessandcocoa.coach.plan.PlanFamily
import ch.lkmc.chessandcocoa.coach.plan.PlanInstance
import ch.lkmc.chessandcocoa.coach.plan.PlanStatus
import ch.lkmc.chessandcocoa.coach.plan.TransitionReason
import ch.lkmc.chessandcocoa.coach.render.Renderer
import kotlinx.serialization.Serializable

@Serializable
data class EpisodeSummary(
    val episodeId: String,
    val family: PlanFamily,
    val target: Square,
    val status: PlanStatus,
    val steps: List<String>,
)

@Serializable
data class ReviewMoment(val ply: Int, val san: String, val text: String, val tone: Tone)

/** The short post-game review (Section 4.1): a few plan episodes and key moments, spoilers allowed. */
@Serializable
data class GameReview(
    val outcome: GameOutcome?,
    val episodes: List<EpisodeSummary>,
    val moments: List<ReviewMoment>,
    val concessions: List<String>,
    val humanMoves: Int,
    val assistedMoves: Int,
)

object Reviews {

    private const val MAX_MOMENTS = 5
    private const val MAX_EPISODES = 4

    /** Builds the review with full disclosure (Section 4.2 "Review"). */
    fun review(record: GameRecord): GameReview {
        val game = record.game
        val reviewPreferences = ExperiencePreferences(commentary = CommentaryLevel.DETAILED, disclosure = DisclosureSetting.AFTER_CHOICE)

        // Key moments: the human moves whose primary point is serious or about a plan.
        val moments = record.assessments.mapNotNull { assessment ->
            val parent = game.truncate(assessment.ply)
            val prior = record.decisions.lastOrNull { it.ply == assessment.ply - 1 }
            val plan = prior?.plan
            val claims = ClaimBuilder.forHumanMove(parent, assessment, prior, plan, emptyList())
            val event = TeachingPolicy.select(claims, reviewPreferences, "review-${assessment.ply}", assessment.ply + 1, record.revision, assessment.san)
                ?: return@mapNotNull null
            if (TeachingPolicy.priority(event.claims.first()) < MOMENT_PRIORITY) return@mapNotNull null
            assessment.ply to ReviewMoment(assessment.ply, assessment.san, event.brief, event.tone)
        }
            .sortedByDescending { (_, moment) -> if (moment.tone == Tone.SERIOUS) 1 else 0 }
            .take(MAX_MOMENTS)
            .sortedBy { it.first }
            .map { it.second }

        val episodes = record.episodes
            .filter { it.actionsTaken > 0 }
            .takeLast(MAX_EPISODES)
            .map { summarize(record, it) }

        val concessions = ClaimBuilder.concessions(record.decisions).map { Renderer.render(it) }
        val humanMoves = record.assessments.size

        return GameReview(
            outcome = record.outcome,
            episodes = episodes,
            moments = moments,
            concessions = concessions,
            humanMoves = humanMoves,
            assistedMoves = record.assessments.count { it.assisted },
        )
    }

    private fun summarize(record: GameRecord, episode: PlanInstance): EpisodeSummary {
        val positions = record.game.positions
        val target = episode.current.targetSquare
        val steps = episode.revisions.mapNotNull { revision ->
            val move = "move ${Renderer.moveNumber(revision.ply)}"
            val san = revision.action?.let { action -> positions.getOrNull(revision.ply)?.takeIf { it.isLegal(action.move) }?.san(action.move) }
            when (revision.reason) {
                TransitionReason.PROPOSED -> null
                TransitionReason.ADOPTED -> "On $move I started a plan against $target" + (san?.let { " with $it." } ?: ".")
                TransitionReason.CONTINUED -> san?.let { "On $move I continued it with $it." }
                TransitionReason.ROUTE_CHANGED -> san?.let { "On $move I changed route: $it." }
                TransitionReason.RESUMED -> san?.let { "On $move I picked it up again with $it." }
                else -> Renderer.render(Claim.PlanTransitionClaim(episode.family, target, revision.reason)).let { "On $move: $it" }
            }
        }
        return EpisodeSummary(episode.episodeId, episode.family, target, episode.status, steps)
    }

    private const val MOMENT_PRIORITY = 40
}
