// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ch.lkmc.chessandcocoa.R
import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.chess.Piece
import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.coach.teach.Hint
import ch.lkmc.chessandcocoa.coach.teach.TeachingEvent
import ch.lkmc.chessandcocoa.coach.teach.Tone
import ch.lkmc.chessandcocoa.session.GameSession
import ch.lkmc.chessandcocoa.ui.board.ChessBoard
import ch.lkmc.chessandcocoa.ui.board.drawableFor
import ch.lkmc.chessandcocoa.ui.board.pieceName
import ch.lkmc.chessandcocoa.ui.theme.Cocoa
import ch.lkmc.chessandcocoa.ui.theme.Teal
import ch.lkmc.chessandcocoa.ui.theme.Terracotta

/**
 * One brief, non-modal teaching point (Section 4.4). The tone bar is a
 * second channel only; the words themselves carry the meaning.
 */
@Composable
fun FeedbackCard(event: TeachingEvent, hidden: Boolean, onToggle: () -> Unit, onShowWhy: () -> Unit) {
    val accent = when (event.tone) {
        Tone.SERIOUS -> Terracotta
        Tone.ENCOURAGING -> Teal
        Tone.CALM -> Cocoa
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(accent))
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!hidden) {
                    Text(
                        event.brief,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val hasMore = event.expanded.isNotEmpty() || event.evidence != null
                    if (hasMore && !hidden) {
                        OutlinedButton(onClick = onShowWhy) { Text(stringResource(R.string.feedback_show_why)) }
                    }
                    TextButton(onClick = onToggle) {
                        Text(stringResource(if (hidden) R.string.feedback_show_why else R.string.feedback_hide))
                    }
                }
            }
        }
    }
}

@Composable
fun HintCard(hint: Hint) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(hint.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Text(stringResource(R.string.hint_assisted_note), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun PromotionDialog(moves: List<Move>, side: Side, onChoose: (Move) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.promotion_title)) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                moves.sortedByDescending { it.promotion?.value ?: 0 }.forEach { move ->
                    val piece = Piece(side, move.promotion ?: return@forEach)
                    val name = pieceName(piece)
                    Image(
                        painter = painterResource(drawableFor(piece)),
                        contentDescription = name,
                        modifier = Modifier.size(56.dp).clickable { onChoose(move) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * "Show me why": the claim's own continuation on a separate board, step by
 * step, with the expanded explanation. It never touches the live game.
 */
@Composable
fun EvidenceSheet(event: TeachingEvent, session: GameSession, onDismiss: () -> Unit) {
    val frames = remember(event.id) { event.evidence?.let(session::evidence).orEmpty() }
    var step by remember(event.id) { mutableIntStateOf(0) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.evidence_title), style = MaterialTheme.typography.titleLarge)
            Text(event.brief, style = MaterialTheme.typography.bodyLarge)

            if (frames.isNotEmpty()) {
                val frame = frames[step.coerceIn(0, frames.lastIndex)]
                ChessBoard(
                    board = frame.board,
                    modifier = Modifier.fillMaxWidth(0.8f).align(Alignment.CenterHorizontally),
                    marks = if (step == 0) event.marks else emptyList(),
                    arrows = frames.getOrNull(step + 1)?.board?.lastMove?.let(::listOf).orEmpty(),
                )
                val back = stringResource(R.string.evidence_step_back)
                val forward = stringResource(R.string.evidence_step_forward)
                val start = stringResource(if (event.evidence?.fromPly == event.ply) R.string.evidence_start_after else R.string.evidence_start_before)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { step-- }, enabled = step > 0, modifier = Modifier.semantics { contentDescription = back }) { Text("◀") }
                    Text(frame.san ?: start, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { step++ }, enabled = step < frames.lastIndex, modifier = Modifier.semantics { contentDescription = forward }) { Text("▶") }
                }
                Text(stringResource(R.string.evidence_legend), style = MaterialTheme.typography.bodySmall)
            }

            event.expanded.forEach { line -> Text(line, style = MaterialTheme.typography.bodyMedium) }
            Text(stringResource(R.string.evidence_live_untouched), style = MaterialTheme.typography.bodySmall)
        }
    }
}
