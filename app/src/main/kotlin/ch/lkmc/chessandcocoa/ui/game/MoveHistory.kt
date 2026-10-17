// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import ch.lkmc.chessandcocoa.R
import ch.lkmc.chessandcocoa.session.MoveRow
import ch.lkmc.chessandcocoa.ui.theme.Space
import ch.lkmc.chessandcocoa.ui.theme.TABULAR_FIGURES

/** A single scrolling line under the board, or wrapped lines inside a panel. */
enum class HistoryLayout { STRIP, FLOW }

/**
 * The game's moves as numbered pairs, "12. Nf3 Nc6", with the latest move
 * picked out so the eye finds where the game is.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoveHistory(rows: List<MoveRow>, layout: HistoryLayout, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // Read as one sentence ("Moves: 1. e4 e5, 2. Nf3"), not one stop per move.
    val spoken = stringResource(R.string.moves_spoken, rows.joinToString(", ") { row -> listOfNotNull("${row.number}.", row.white ?: ELLIPSIS, row.black).joinToString(" ") })
    val readAsOne = Modifier.clearAndSetSemantics { contentDescription = spoken }
    when (layout) {
        HistoryLayout.STRIP -> {
            val listState = rememberLazyListState()
            LaunchedEffect(rows.size, rows.lastOrNull()) {
                if (rows.isNotEmpty()) listState.animateScrollToItem(rows.lastIndex)
            }
            if (rows.isEmpty()) {
                Row(modifier.fillMaxWidth().height(STRIP_HEIGHT).padding(horizontal = Space.gutter), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.moves_empty), style = MaterialTheme.typography.bodyMedium, color = muted)
                }
                return
            }
            LazyRow(
                modifier.fillMaxWidth().height(STRIP_HEIGHT).then(readAsOne),
                state = listState,
                contentPadding = PaddingValues(horizontal = Space.gutter),
                horizontalArrangement = Arrangement.spacedBy(Space.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(rows) { index, row -> MovePair(row, latest = index == rows.lastIndex) }
            }
        }
        HistoryLayout.FLOW -> Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(
                stringResource(R.string.moves_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.semantics { heading() },
            )
            if (rows.isEmpty()) {
                Text(stringResource(R.string.moves_empty), style = MaterialTheme.typography.bodyMedium, color = muted)
                return@Column
            }
            FlowRow(readAsOne, horizontalArrangement = Arrangement.spacedBy(Space.m), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                rows.forEachIndexed { index, row -> MovePair(row, latest = index == rows.lastIndex) }
            }
        }
    }
}

@Composable
private fun MovePair(row: MoveRow, latest: Boolean) {
    val style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TABULAR_FIGURES)
    // The latest move is the last one in the last row: Black's if present, else White's.
    val lastIsBlack = latest && row.black != null
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text("${row.number}.", style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
        San(row.white ?: ELLIPSIS, style, highlighted = latest && !lastIsBlack && row.white != null)
        row.black?.let { San(it, style, highlighted = lastIsBlack) }
    }
}

@Composable
private fun San(text: String, style: TextStyle, highlighted: Boolean) {
    if (!highlighted) {
        Text(text, style = style, modifier = Modifier.padding(horizontal = Space.xs))
        return
    }
    Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = MaterialTheme.shapes.extraSmall) {
        Text(text, style = style.copy(fontWeight = MaterialTheme.typography.labelLarge.fontWeight), modifier = Modifier.padding(horizontal = Space.xs, vertical = Space.xxs))
    }
}

private const val ELLIPSIS = "…"
private val STRIP_HEIGHT = 40.dp
