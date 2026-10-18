// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ch.lkmc.chessandcocoa.R
import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.chess.Piece
import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.session.SessionError
import ch.lkmc.chessandcocoa.ui.board.PieceArtwork
import ch.lkmc.chessandcocoa.ui.board.drawableFor
import ch.lkmc.chessandcocoa.ui.board.pieceName
import ch.lkmc.chessandcocoa.ui.board.pieceTypeName
import ch.lkmc.chessandcocoa.ui.theme.CocoaTheme
import ch.lkmc.chessandcocoa.ui.theme.Space

/** The four promotion pieces as large tiles on a light square, each named. */
@Composable
fun PromotionDialog(
    moves: List<Move>,
    side: Side,
    onChoose: (Move) -> Unit,
    onDismiss: () -> Unit,
    artwork: PieceArtwork,
) {
    val title = stringResource(R.string.promotion_title)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.padding(horizontal = Space.xl).widthIn(max = DIALOG_WIDTH).semantics { paneTitle = title },
        ) {
            Column(Modifier.padding(Space.xl), verticalArrangement = Arrangement.spacedBy(Space.l)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    moves.sortedByDescending { it.promotion?.value ?: 0 }.forEach { move ->
                        val type = move.promotion ?: return@forEach
                        PromotionTile(Piece(side, type), artwork, onClick = { onChoose(move) }, modifier = Modifier.weight(1f))
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    }
}

@Composable
private fun PromotionTile(piece: Piece, artwork: PieceArtwork, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val name = pieceName(piece)
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = name
                role = Role.Button
            }
            .padding(Space.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Box(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(CocoaTheme.colors.board.light).padding(Space.s),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(drawableFor(piece, artwork)), contentDescription = null, modifier = Modifier.size(TILE_PIECE))
        }
        Text(stringResource(pieceTypeName(piece.type)).replaceFirstChar(Char::titlecase), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ResignDialog(onResign: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_flag), contentDescription = null) },
        title = { Text(stringResource(R.string.resign_title)) },
        text = { Text(stringResource(R.string.resign_text), style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onResign, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text(stringResource(R.string.action_resign))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.resign_keep_playing)) } },
    )
}

@Composable
fun ErrorDialog(error: SessionError, onDismiss: () -> Unit) {
    val saveFailed = error == SessionError.SAVE_FAILED
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_info), contentDescription = null) },
        title = { Text(stringResource(if (saveFailed) R.string.error_save_title else R.string.error_engine_title)) },
        text = { Text(stringResource(if (saveFailed) R.string.error_save else R.string.error_engine), style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}

private val TILE_PIECE = 52.dp
private val DIALOG_WIDTH = 420.dp
