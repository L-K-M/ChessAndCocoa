// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ch.lkmc.chessandcocoa.R
import ch.lkmc.chessandcocoa.chess.OutcomeReason
import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.coach.evidence.MarkedSquare
import ch.lkmc.chessandcocoa.coach.evidence.SquareRole
import ch.lkmc.chessandcocoa.coach.teach.Hint
import ch.lkmc.chessandcocoa.coach.teach.OpponentIntent
import ch.lkmc.chessandcocoa.coach.teach.TeachingEvent
import ch.lkmc.chessandcocoa.session.EvidenceFrame
import ch.lkmc.chessandcocoa.session.EvidenceLine
import ch.lkmc.chessandcocoa.session.Phase
import ch.lkmc.chessandcocoa.session.SessionState
import ch.lkmc.chessandcocoa.ui.board.MarkGlyph
import ch.lkmc.chessandcocoa.ui.board.roleLabel
import ch.lkmc.chessandcocoa.ui.common.NotationChip
import ch.lkmc.chessandcocoa.ui.common.toneColors
import ch.lkmc.chessandcocoa.ui.theme.CocoaTheme
import ch.lkmc.chessandcocoa.ui.theme.Space
import ch.lkmc.chessandcocoa.ui.theme.TABULAR_FIGURES

/** Whose turn it is, in calm words; announced to screen readers when it changes. */
@Composable
fun StatusRow(state: SessionState, modifier: Modifier = Modifier) {
    val record = state.record ?: return
    val outcome = record.outcome
    val text = when (state.phase) {
        Phase.HUMAN_TURN -> stringResource(if (state.board?.checkSquare != null) R.string.status_your_move_check else R.string.status_your_move)
        Phase.ASSESSING -> stringResource(R.string.status_assessing)
        Phase.OPPONENT_THINKING -> stringResource(R.string.status_thinking)
        Phase.HINTING -> stringResource(R.string.status_hinting)
        Phase.GAME_OVER -> {
            val result = stringResource(
                when (outcome?.winner) {
                    null -> R.string.status_draw
                    record.humanSide -> R.string.status_you_won
                    else -> R.string.status_you_lost
                },
            )
            outcome?.let { stringResource(R.string.status_result, stringResource(reasonLabel(it.reason)), result) } ?: result
        }
        Phase.NO_GAME -> ""
    }
    val busy = state.phase == Phase.ASSESSING || state.phase == Phase.OPPONENT_THINKING || state.phase == Phase.HINTING

    Row(modifier.fillMaxWidth().heightIn(min = STATUS_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
        if (state.phase != Phase.GAME_OVER) {
            state.board?.let { TurnDot(it.sideToMove) }
            Spacer(Modifier.size(Space.m))
        }
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            modifier = Modifier.weight(1f, fill = false).semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (busy) {
            Spacer(Modifier.size(Space.m))
            CircularProgressIndicator(Modifier.size(PROGRESS_SIZE), color = MaterialTheme.colorScheme.secondary, strokeWidth = 2.dp)
        }
    }
}

/** A small disc in the colour of the side to move, described for screen readers. */
@Composable
private fun TurnDot(side: Side) {
    val description = stringResource(if (side == Side.WHITE) R.string.turn_white else R.string.turn_black)
    val fill = if (side == Side.WHITE) CocoaTheme.colors.turnWhite else CocoaTheme.colors.turnBlack
    val edge = MaterialTheme.colorScheme.outline
    Canvas(Modifier.size(TURN_DOT).semantics { contentDescription = description }) {
        drawCircle(fill)
        drawCircle(edge, style = Stroke(1.dp.toPx()))
    }
}

/** Replaces the status line while "Show me why" is on the board, so the two can never be confused. */
@Composable
fun EvidenceBanner(modifier: Modifier = Modifier) {
    val title = stringResource(R.string.evidence_title)
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        // A pane title makes screen readers announce the replay as it appears.
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) { paneTitle = title },
    ) {
        Row(Modifier.padding(horizontal = Space.m, vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_visibility), contentDescription = null)
            Column(Modifier.padding(start = Space.m)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.evidence_live_untouched), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * The coaching panel: the teaching points for this turn, any hint, and the
 * legend for the marks on the board. One point per event (Section 4.3).
 */
@Composable
fun CoachPanel(
    state: SessionState,
    marks: List<MarkedSquare>,
    feedbackHidden: Boolean,
    onToggleFeedback: () -> Unit,
    onShowWhy: (TeachingEvent) -> Unit,
    history: HistoryPlacement,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter, vertical = Space.s),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        state.opponentIntent?.let { IntentNote(it) }

        if (state.events.isEmpty() && state.hint == null && state.opponentIntent == null) {
            Text(stringResource(R.string.coach_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (state.events.isNotEmpty()) {
            Notes(state.events, feedbackHidden, onToggleFeedback, onShowWhy)
        }
        state.hint?.let { HintNote(it) }
        if (marks.isNotEmpty()) MarkLegend(marks.map { it.role }.toSet())

        if (history == HistoryPlacement.IN_PANEL) MoveHistory(state.moves, HistoryLayout.FLOW)
    }
}

@Composable
private fun IntentNote(intent: OpponentIntent) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.widthIn(max = Space.readingWidth),
    ) {
        Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.intent_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).semantics { heading() })
                Text(intent.moveLabel, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TABULAR_FIGURES), modifier = Modifier.padding(start = Space.s))
            }
            Text(intent.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Text(stringResource(R.string.intent_assisted_note), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Teaching events as one quiet surface: the move, then what it means, then the way to see why. */
@Composable
private fun Notes(events: List<TeachingEvent>, hidden: Boolean, onToggle: () -> Unit, onShowWhy: (TeachingEvent) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.widthIn(max = Space.readingWidth)) {
        Column(Modifier.padding(start = Space.l, end = Space.s, top = Space.s, bottom = Space.m)) {
            events.forEachIndexed { index, event ->
                if (index > 0) HorizontalDivider(Modifier.padding(vertical = Space.s, horizontal = Space.xs), color = MaterialTheme.colorScheme.outlineVariant)
                Note(event, hidden, onShowWhy = { onShowWhy(event) }, toggle = if (index == 0) onToggle else null)
            }
        }
    }
}

@Composable
private fun Note(event: TeachingEvent, hidden: Boolean, onShowWhy: () -> Unit, toggle: (() -> Unit)?) {
    val (container, content) = toneColors(event.tone)
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Row(Modifier.heightIn(min = Space.touchTarget), verticalAlignment = Alignment.CenterVertically) {
            if (event.moveLabel.isNotEmpty()) NotationChip(event.moveLabel, container, content)
            Spacer(Modifier.weight(1f))
            toggle?.let {
                TextButton(onClick = it) {
                    Icon(painterResource(if (hidden) R.drawable.ic_visibility else R.drawable.ic_visibility_off), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Text(stringResource(if (hidden) R.string.feedback_show else R.string.feedback_hide), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
                }
            }
        }
        if (hidden) return@Column

        val canShowWhy = event.expanded.isNotEmpty() || event.evidence != null || event.marks.isNotEmpty()
        val showWhyLabel = stringResource(R.string.feedback_show_why)
        Text(
            event.brief,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .heightIn(min = Space.touchTarget)
                .then(if (canShowWhy) Modifier.clickable(onClickLabel = showWhyLabel, role = Role.Button, onClick = onShowWhy) else Modifier)
                .padding(end = Space.s)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (canShowWhy) {
            FilledTonalButton(onClick = onShowWhy, modifier = Modifier.heightIn(min = Space.touchTarget)) {
                Text(stringResource(R.string.feedback_show_why))
                Icon(painterResource(R.drawable.ic_arrow_forward), contentDescription = null, modifier = Modifier.padding(start = ButtonDefaults.IconSpacing).size(ButtonDefaults.IconSize))
            }
        }
    }
}

@Composable
private fun HintNote(hint: Hint) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.widthIn(max = Space.readingWidth)) {
        Row(Modifier.padding(Space.l)) {
            Icon(painterResource(R.drawable.ic_lightbulb), contentDescription = null, modifier = Modifier.padding(top = Space.xxs))
            Column(Modifier.padding(start = Space.m), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(hint.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                Text(stringResource(R.string.hint_assisted_note), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** The marks on the board and what they mean, in the board's own shapes and colours. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MarkLegend(roles: Set<SquareRole>, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(Space.l), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        SquareRole.entries.filter { it in roles }.forEach { role ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                MarkGlyph(role)
                Text(
                    stringResource(roleLabel(role)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Space.xs),
                )
            }
        }
    }
}

/**
 * The words that go with "Show me why": the move, its point, the line as
 * steps to jump between, and the further explanation.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EvidencePanel(event: TeachingEvent, frames: List<EvidenceFrame>, step: Int, line: EvidenceLine, onStep: (Int) -> Unit, onLine: (EvidenceLine) -> Unit) {
    val (container, content) = toneColors(event.tone)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter, vertical = Space.s),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Column(Modifier.widthIn(max = Space.readingWidth), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            if (event.moveLabel.isNotEmpty()) NotationChip(event.moveLabel, container, content)
            Text(event.brief, style = MaterialTheme.typography.bodyLarge)
        }

        if (event.alternative != null) {
            FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                StepChip(stringResource(R.string.evidence_your_move), line == EvidenceLine.MAIN) { onLine(EvidenceLine.MAIN) }
                StepChip(stringResource(R.string.evidence_alternative), line == EvidenceLine.ALTERNATIVE) { onLine(EvidenceLine.ALTERNATIVE) }
            }
            if (line == EvidenceLine.ALTERNATIVE) Text(stringResource(R.string.evidence_recapture_example), style = MaterialTheme.typography.bodyMedium)
        }

        if (frames.size > 1) {
            val continuation = if (line == EvidenceLine.ALTERNATIVE) event.alternative else event.evidence
            val start = stringResource(if (continuation?.fromPly == event.ply) R.string.evidence_start_after else R.string.evidence_start_before)
            Text(
                stringResource(R.string.evidence_line_step, step + 1, frames.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
            FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                frames.forEachIndexed { index, frame ->
                    StepChip(label = frame.san ?: start, selected = index == step, onClick = { onStep(index) })
                }
            }
        }
        val next = frames.getOrNull(step + 1)
        next?.board?.lastMove?.let { move ->
            Text(
                stringResource(R.string.evidence_next_move, move.from.name, move.to.name, next.san.orEmpty()),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        val marks = frames.getOrNull(step)?.marks.orEmpty()
        if (marks.isNotEmpty()) MarkLegend(marks.map { it.role }.toSet())

        event.expanded.forEach { line ->
            Text(line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.widthIn(max = Space.readingWidth))
        }
    }
}

/** One position of the replayed line; selecting it shows that position on the board. */
@Composable
private fun StepChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (selected) colors.secondary else colors.surfaceContainerHigh,
        contentColor = if (selected) colors.onSecondary else colors.onSurface,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .heightIn(min = Space.touchTarget)
            .clip(MaterialTheme.shapes.small)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TABULAR_FIGURES),
            modifier = Modifier.padding(horizontal = Space.m, vertical = Space.m),
        )
    }
}

fun reasonLabel(reason: OutcomeReason): Int = when (reason) {
    OutcomeReason.CHECKMATE -> R.string.result_checkmate
    OutcomeReason.STALEMATE -> R.string.result_stalemate
    OutcomeReason.INSUFFICIENT_MATERIAL -> R.string.result_insufficient
    OutcomeReason.FIFTY_MOVE_RULE -> R.string.result_fifty_moves
    OutcomeReason.THREEFOLD_REPETITION -> R.string.result_repetition
    OutcomeReason.RESIGNATION -> R.string.result_resignation
}

private val STATUS_HEIGHT = 40.dp
private val PROGRESS_SIZE = 18.dp
private val TURN_DOT = 14.dp
