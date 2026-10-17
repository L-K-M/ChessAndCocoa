// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ch.lkmc.chessandcocoa.R
import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.coach.decide.Difficulty
import ch.lkmc.chessandcocoa.coach.plan.PlanFamily
import ch.lkmc.chessandcocoa.coach.plan.PlanStatus
import ch.lkmc.chessandcocoa.coach.teach.EpisodeSummary
import ch.lkmc.chessandcocoa.coach.teach.GameReview
import ch.lkmc.chessandcocoa.coach.teach.ReviewMoment
import ch.lkmc.chessandcocoa.coach.teach.Tone
import ch.lkmc.chessandcocoa.ui.common.NotationChip
import ch.lkmc.chessandcocoa.ui.common.ScreenTopBar
import ch.lkmc.chessandcocoa.ui.common.SectionHeader
import ch.lkmc.chessandcocoa.ui.common.toneColors
import ch.lkmc.chessandcocoa.ui.game.difficultyLabel
import ch.lkmc.chessandcocoa.ui.game.reasonLabel
import ch.lkmc.chessandcocoa.ui.game.sideName
import ch.lkmc.chessandcocoa.ui.theme.Space

/** Who played and what comes next, alongside the coach's [GameReview]. */
data class ReviewContext(val humanSide: Side, val difficulty: Difficulty, val onNewGame: () -> Unit, val onBack: () -> Unit)

/**
 * A short account of the game (Section 4.1): the result, the moments worth
 * looking at, my plans with intentions fully revealed (Section 4.2), and
 * what to do next.
 */
@Composable
fun ReviewScreen(review: GameReview, context: ReviewContext) {
    Scaffold(
        topBar = { ScreenTopBar(title = stringResource(R.string.review_title), onBack = context.onBack) },
        bottomBar = { NextActions(review, context) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter, vertical = Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.l),
        ) {
            Column(Modifier.widthIn(max = Space.readingWidth), verticalArrangement = Arrangement.spacedBy(Space.l)) {
                Result(review, context)

                if (review.episodes.isEmpty() && review.moments.isEmpty()) {
                    Text(stringResource(R.string.review_nothing), style = MaterialTheme.typography.bodyLarge)
                }

                if (review.moments.isNotEmpty()) {
                    Column {
                        SectionHeader(stringResource(R.string.review_moments))
                        review.moments.forEachIndexed { index, moment -> Moment(moment, last = index == review.moments.lastIndex) }
                    }
                }

                if (review.episodes.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                        SectionHeader(stringResource(R.string.review_plans))
                        review.episodes.forEach { Episode(it) }
                    }
                }

                if (review.concessions.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                        SectionHeader(stringResource(R.string.review_concessions))
                        review.concessions.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
    }
}

/** The outcome first, in plain words, then who played and how much help was used. */
@Composable
private fun Result(review: GameReview, context: ReviewContext) {
    val outcome = review.outcome
    val headline = when {
        outcome == null -> R.string.review_result_ongoing
        outcome.winner == null -> R.string.review_result_draw
        outcome.winner == context.humanSide -> R.string.review_result_won
        else -> R.string.review_result_lost
    }
    Column(Modifier.padding(top = Space.s), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        outcome?.let {
            Text(stringResource(reasonLabel(it.reason)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        }
        Text(stringResource(headline), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        Text(
            stringResource(R.string.review_summary, stringResource(difficultyLabel(context.difficulty)), stringResource(sideName(context.humanSide))),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (outcome == null) {
            Text(stringResource(R.string.review_in_progress), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (review.humanMoves > 0) {
            Row(Modifier.padding(top = Space.s), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_lightbulb), contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(R.string.review_assisted, review.assistedMoves, review.humanMoves),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Space.s),
                )
            }
        }
    }
}

/** One moment on a quiet timeline: a dot on a rail, the move, what it meant. */
@Composable
private fun Moment(moment: ReviewMoment, last: Boolean) {
    val (container, content) = toneColors(moment.tone)
    Row(Modifier.height(IntrinsicSize.Min)) {
        Box(Modifier.width(RAIL_WIDTH).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            if (!last) Box(Modifier.padding(top = Space.l).width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
            Box(Modifier.padding(top = Space.s).size(10.dp).clip(CircleShape).background(dotColor(moment.tone)))
        }
        Column(Modifier.padding(start = Space.s, bottom = Space.l), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            NotationChip(moment.label, container, content)
            Text(moment.text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** A plan from start to end: its family, target and outcome, then each recorded step. */
@Composable
private fun Episode(episode: EpisodeSummary) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(familyLabel(episode.family)), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.plan_target, episode.target.name), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                NotationChip(stringResource(statusLabel(episode.status)), MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurface)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            episode.steps.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun NextActions(review: GameReview, context: ReviewContext) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = Space.gutter, vertical = Space.m),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                // A finished game points forward; one still going points back to the board.
                if (review.outcome != null) {
                    Button(onClick = context.onNewGame, modifier = Modifier.weight(1f).heightIn(min = Space.touchTarget)) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Text(stringResource(R.string.action_new_game), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
                    }
                    OutlinedButton(onClick = context.onBack, modifier = Modifier.weight(1f).heightIn(min = Space.touchTarget)) {
                        Text(stringResource(R.string.review_back_to_board))
                    }
                } else {
                    Button(onClick = context.onBack, modifier = Modifier.weight(1f).heightIn(min = Space.touchTarget)) {
                        Text(stringResource(R.string.review_back_to_board))
                    }
                }
            }
        }
    }
}

@Composable
private fun dotColor(tone: Tone): Color = when (tone) {
    Tone.SERIOUS -> MaterialTheme.colorScheme.error
    Tone.ENCOURAGING -> MaterialTheme.colorScheme.secondary
    Tone.CALM -> MaterialTheme.colorScheme.outline
}

private fun familyLabel(family: PlanFamily): Int = when (family) {
    PlanFamily.ATTACK_KING_SQUARE -> R.string.plan_attack_king_square
    PlanFamily.WIN_WEAK_UNIT -> R.string.plan_win_weak_unit
    PlanFamily.PROMOTE_PASSED_PAWN -> R.string.plan_promote_passed_pawn
    PlanFamily.INVADE_OPEN_FILE -> R.string.plan_invade_open_file
}

private fun statusLabel(status: PlanStatus): Int = when (status) {
    PlanStatus.PROPOSED -> R.string.plan_status_proposed
    PlanStatus.ACTIVE -> R.string.plan_status_active
    PlanStatus.SUSPENDED -> R.string.plan_status_suspended
    PlanStatus.COMPLETED -> R.string.plan_status_completed
    PlanStatus.ABANDONED -> R.string.plan_status_abandoned
    PlanStatus.SUPERSEDED -> R.string.plan_status_superseded
}

private val RAIL_WIDTH = 16.dp
