// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach.teach

import ch.lkmc.chessandcocoa.coach.assess.ImpactKind
import ch.lkmc.chessandcocoa.coach.assess.QualityCategory
import ch.lkmc.chessandcocoa.coach.evidence.Claim
import ch.lkmc.chessandcocoa.coach.evidence.Continuation
import ch.lkmc.chessandcocoa.coach.evidence.Disclosure
import ch.lkmc.chessandcocoa.coach.evidence.MarkedSquare
import ch.lkmc.chessandcocoa.coach.render.NumberDetail
import ch.lkmc.chessandcocoa.coach.render.Renderer
import kotlinx.serialization.Serializable

/** How much the coach says (Section 4.3). Never changes the analysis behind it. */
@Serializable
enum class CommentaryLevel { QUIET, NORMAL, DETAILED }

/** When the opponent's intentions are revealed (Section 4.2). */
@Serializable
enum class DisclosureSetting {
    /** Default: reveal the part of the intention the player's move touched, after the move. */
    AFTER_CHOICE,

    /** Never reveal intentions during play; only in the review. */
    REVIEW_ONLY,

    /** Opt-in help: show the recorded aim with the opponent's move, before the reply. */
    WITH_MOVE,
}

/**
 * Experience preferences (Section 11.1), deliberately separate from opponent
 * difficulty and analyst configuration: changing them can never weaken play
 * or checking.
 */
@Serializable
data class ExperiencePreferences(
    val commentary: CommentaryLevel = CommentaryLevel.NORMAL,
    val disclosure: DisclosureSetting = DisclosureSetting.AFTER_CHOICE,
    val showNumbers: Boolean = false,
    val showCoordinates: Boolean = true,
    /** A plainer, higher-contrast board for readability (Section 4.5). */
    val highContrastBoard: Boolean = false,
    val schemaVersion: Int = SCHEMA_VERSION,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
enum class Tone { CALM, SERIOUS, ENCOURAGING }

/** One rendered teaching moment, tied to the game revision it describes (R11). */
@Serializable
data class TeachingEvent(
    val id: String,
    /** Game ply after the move this event explains. */
    val ply: Int,
    val revision: Int,
    val tone: Tone,
    /** The move the event is about, e.g. "3. Qf3" or "3... Nf6". */
    val moveLabel: String,
    val brief: String,
    val expanded: List<String>,
    val marks: List<MarkedSquare>,
    /** Evidence to replay in "Show me why"; starts at its own game state. */
    val evidence: Continuation?,
    val claims: List<Claim>,
    /** A verified counterfactual to compare with the consequence of the played move. */
    val alternative: Continuation? = null,
)

/** The latest opponent move's verified, recorded purpose, kept readable during the reply. */
data class OpponentIntent(val decisionId: String, val moveLabel: String, val text: String)

/**
 * Picks at most one primary teaching point per event (Section 4.3): serious
 * consequences first; plan impact is the main teaching moment otherwise and
 * becomes the expansion when there is a material loss.
 */
object TeachingPolicy {

    fun select(
        claims: List<Claim>,
        preferences: ExperiencePreferences,
        id: String,
        ply: Int,
        revision: Int,
        moveLabel: String = "",
    ): TeachingEvent? {
        val visible = claims.filter { visible(it, preferences) }
        val primary = visible.maxByOrNull { priority(it) } ?: return null
        if (!worthSaying(primary, visible, preferences.commentary)) return null

        val numbers = if (preferences.showNumbers) NumberDetail.SHOWN else NumberDetail.HIDDEN
        val revealed = visible.filterIsInstance<Claim.PlanRevealed>().firstOrNull()

        // A plan impact is introduced by the recorded intention it refers to.
        val brief = if (primary is Claim.PlanImpact && revealed != null) {
            Renderer.render(revealed, numbers) + " " + Renderer.render(primary, numbers)
        } else {
            Renderer.render(primary, numbers)
        }

        val secondary = visible
            .filter { it !== primary && !(primary is Claim.PlanImpact && it === revealed) }
            .filter { includeInExpansion(it, primary) }
            .sortedByDescending { priority(it) }

        return TeachingEvent(
            id = id,
            ply = ply,
            revision = revision,
            tone = tone(primary),
            moveLabel = moveLabel,
            brief = brief,
            expanded = secondary.map { Renderer.render(it, numbers) },
            marks = (primary.marks + (if (primary is Claim.PlanImpact) revealed?.marks.orEmpty() else emptyList())).distinct(),
            evidence = primary.continuation ?: secondary.firstNotNullOfOrNull { it.continuation },
            claims = listOf(primary) + secondary,
            alternative = (primary as? Claim.ExposesPiece)?.defense,
        )
    }

    private fun visible(claim: Claim, preferences: ExperiencePreferences): Boolean = when (claim.disclosure) {
        Disclosure.NOW -> true
        Disclosure.AFTER_CHOICE -> preferences.disclosure != DisclosureSetting.REVIEW_ONLY
        Disclosure.REVIEW_ONLY -> false
    }

    /** Higher is more important. */
    fun priority(claim: Claim): Int = when (claim) {
        is Claim.GameOver -> 100
        is Claim.AllowsMate -> 90
        is Claim.AllowsForcedMate -> 85
        is Claim.ExposesPiece -> 80
        is Claim.MissedWin -> 70
        is Claim.PlanImpact -> 60
        is Claim.OpponentOversight -> 55
        is Claim.StopsMateThreat -> 50
        is Claim.WinsMaterial -> 45
        is Claim.MoveQuality -> when (claim.category) {
            QualityCategory.BLUNDER -> 75
            QualityCategory.MISTAKE -> 65
            QualityCategory.INACCURACY -> 40
            QualityCategory.GOOD -> 15
            QualityCategory.BEST -> 10
        }
        is Claim.PlanTransitionClaim -> 30
        is Claim.BetterDefense -> 20
        is Claim.PlanRevealed -> 5
        is Claim.Concession -> 0
    }

    /** Selective feedback: routine moves get no comment unless the player asked for detail. */
    private fun worthSaying(primary: Claim, all: List<Claim>, level: CommentaryLevel): Boolean {
        val score = priority(primary)
        return when (level) {
            CommentaryLevel.QUIET -> score >= QUIET_THRESHOLD
            CommentaryLevel.NORMAL -> score >= NORMAL_THRESHOLD
            CommentaryLevel.DETAILED -> score >= DETAILED_THRESHOLD || all.any { it is Claim.PlanTransitionClaim }
        }
    }

    private fun includeInExpansion(claim: Claim, primary: Claim): Boolean = when (claim) {
        // A routine quality note adds nothing under a more specific point.
        is Claim.MoveQuality -> claim.category >= QualityCategory.INACCURACY || primary is Claim.PlanImpact
        is Claim.PlanImpact -> claim.impacts.any { it != ImpactKind.NO_SUPPORTED_MATERIAL_EFFECT }
        else -> true
    }

    private fun tone(primary: Claim): Tone = when (primary) {
        is Claim.AllowsMate, is Claim.AllowsForcedMate, is Claim.ExposesPiece, is Claim.MissedWin -> Tone.SERIOUS
        is Claim.MoveQuality -> if (primary.category >= QualityCategory.MISTAKE) Tone.SERIOUS else Tone.CALM
        is Claim.WinsMaterial, is Claim.StopsMateThreat, is Claim.OpponentOversight -> Tone.ENCOURAGING
        is Claim.GameOver -> Tone.CALM
        else -> Tone.CALM
    }

    private const val QUIET_THRESHOLD = 75
    private const val NORMAL_THRESHOLD = 40
    private const val DETAILED_THRESHOLD = 10
}
