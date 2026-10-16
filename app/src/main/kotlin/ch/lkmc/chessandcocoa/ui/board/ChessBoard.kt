// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.ui.board

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.lkmc.chessandcocoa.R
import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.chess.Piece
import ch.lkmc.chessandcocoa.chess.PieceType
import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.chess.Square
import ch.lkmc.chessandcocoa.coach.evidence.MarkedSquare
import ch.lkmc.chessandcocoa.coach.evidence.SquareRole
import ch.lkmc.chessandcocoa.session.BoardSnapshot
import ch.lkmc.chessandcocoa.ui.theme.BoardDark
import ch.lkmc.chessandcocoa.ui.theme.BoardDarkContrast
import ch.lkmc.chessandcocoa.ui.theme.BoardLight
import ch.lkmc.chessandcocoa.ui.theme.BoardLightContrast
import ch.lkmc.chessandcocoa.ui.theme.Cocoa
import ch.lkmc.chessandcocoa.ui.theme.Honey
import ch.lkmc.chessandcocoa.ui.theme.Ink
import ch.lkmc.chessandcocoa.ui.theme.Plum
import ch.lkmc.chessandcocoa.ui.theme.Teal
import ch.lkmc.chessandcocoa.ui.theme.Terracotta

/** Board looks; readability first (Section 4.5). */
enum class BoardStyle { COCOA, HIGH_CONTRAST }

enum class Coordinates { SHOWN, HIDDEN }

/**
 * The chessboard: 64 accessible squares (each a labelled button for screen
 * readers), pieces, and highlight markers that pair colour with a distinct
 * shape so no meaning rests on colour alone (R14):
 *
 * ```
 *   selected ▢ outline     legal move • dot / ◯ ring on a capture
 *   target   ◯ plum ring   attacker ▲ corner    defender ■ corner
 *   threat   ✕ corner      last move: honey tint + corner brackets
 * ```
 */
@Composable
fun ChessBoard(
    board: BoardSnapshot,
    modifier: Modifier = Modifier,
    selected: Square? = null,
    marks: List<MarkedSquare> = emptyList(),
    arrows: List<Move> = emptyList(),
    coordinates: Coordinates = Coordinates.SHOWN,
    style: BoardStyle = BoardStyle.COCOA,
    onSquareTap: ((Square) -> Unit)? = null,
) {
    val targets = selected?.let { board.legalMoves[it] }.orEmpty().map { it.to }.toSet()
    val marksBySquare = marks.groupBy({ it.square }, { it.role })

    BoxWithConstraints(modifier.aspectRatio(1f)) {
        val cell = maxWidth / BOARD_SIZE
        Column(Modifier.fillMaxSize()) {
            for (row in 0 until BOARD_SIZE) {
                Row(Modifier.fillMaxWidth()) {
                    for (column in 0 until BOARD_SIZE) {
                        val square = squareAt(row, column, board.orientation)
                        SquareCell(
                            square = square,
                            piece = board.pieces[square],
                            size = cell,
                            style = style,
                            selected = square == selected,
                            target = square in targets,
                            lastMove = board.lastMove?.let { square == it.from || square == it.to } == true,
                            check = square == board.checkSquare,
                            roles = marksBySquare[square].orEmpty(),
                            fileLabel = if (coordinates == Coordinates.SHOWN && row == BOARD_SIZE - 1) "${'a' + square.file}" else null,
                            rankLabel = if (coordinates == Coordinates.SHOWN && column == 0) "${square.rank + 1}" else null,
                            onTap = onSquareTap,
                        )
                    }
                }
            }
        }
        if (arrows.isNotEmpty()) {
            Canvas(Modifier.fillMaxSize()) {
                arrows.forEach { drawArrow(it, board.orientation) }
            }
        }
    }
}

@Composable
private fun SquareCell(
    square: Square,
    piece: Piece?,
    size: Dp,
    style: BoardStyle,
    selected: Boolean,
    target: Boolean,
    lastMove: Boolean,
    check: Boolean,
    roles: List<SquareRole>,
    fileLabel: String?,
    rankLabel: String?,
    onTap: ((Square) -> Unit)?,
) {
    val light = (square.file + square.rank) % 2 == 1
    val base = when (style) {
        BoardStyle.COCOA -> if (light) BoardLight else BoardDark
        BoardStyle.HIGH_CONTRAST -> if (light) BoardLightContrast else BoardDarkContrast
    }
    val labelColor = if (light) BoardDark else BoardLight
    val description = squareDescription(square, piece)
    val state = squareState(selected, target, piece != null, check, lastMove, roles)

    val tap = onTap?.let { handler -> Modifier.clickable(role = Role.Button) { handler(square) } } ?: Modifier
    Box(
        Modifier
            .size(size)
            .background(base)
            .then(tap)
            .semantics {
                contentDescription = description
                if (state.isNotEmpty()) stateDescription = state
            }
            .drawWithContent {
                if (lastMove) drawLastMove()
                drawContent()
                drawMarkers(selected, target, piece != null, check, roles)
            },
    ) {
        piece?.let {
            Image(
                painter = painterResource(drawableFor(it)),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(size * PIECE_INSET),
            )
        }
        fileLabel?.let {
            Text(it, color = labelColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 2.dp))
        }
        rankLabel?.let {
            Text(it, color = labelColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopStart).padding(start = 2.dp))
        }
    }
}

@Composable
private fun squareDescription(square: Square, piece: Piece?): String {
    if (piece == null) return stringResource(R.string.square_empty, square.name)
    return stringResource(R.string.square_piece, square.name, pieceName(piece))
}

@Composable
fun pieceName(piece: Piece): String {
    val side = stringResource(if (piece.side == Side.WHITE) R.string.side_white else R.string.side_black)
    val type = stringResource(
        when (piece.type) {
            PieceType.PAWN -> R.string.piece_pawn
            PieceType.KNIGHT -> R.string.piece_knight
            PieceType.BISHOP -> R.string.piece_bishop
            PieceType.ROOK -> R.string.piece_rook
            PieceType.QUEEN -> R.string.piece_queen
            PieceType.KING -> R.string.piece_king
        },
    )
    return stringResource(R.string.piece_named, side, type)
}

@Composable
private fun squareState(selected: Boolean, target: Boolean, occupied: Boolean, check: Boolean, lastMove: Boolean, roles: List<SquareRole>): String {
    val parts = buildList {
        if (selected) add(stringResource(R.string.state_selected))
        if (target) add(stringResource(if (occupied) R.string.state_capture else R.string.state_move_here))
        if (check) add(stringResource(R.string.state_check))
        if (lastMove) add(stringResource(R.string.state_last_move))
        roles.distinct().forEach { role ->
            add(
                stringResource(
                    when (role) {
                        SquareRole.TARGET -> R.string.mark_target
                        SquareRole.ATTACKER -> R.string.mark_attacker
                        SquareRole.DEFENDER -> R.string.mark_defender
                        SquareRole.MOVED -> R.string.mark_moved
                        SquareRole.THREAT -> R.string.mark_threat
                    },
                ),
            )
        }
    }
    return parts.joinToString(", ")
}

@DrawableRes
fun drawableFor(piece: Piece): Int = when (piece.side) {
    Side.WHITE -> when (piece.type) {
        PieceType.PAWN -> R.drawable.piece_wp
        PieceType.KNIGHT -> R.drawable.piece_wn
        PieceType.BISHOP -> R.drawable.piece_wb
        PieceType.ROOK -> R.drawable.piece_wr
        PieceType.QUEEN -> R.drawable.piece_wq
        PieceType.KING -> R.drawable.piece_wk
    }
    Side.BLACK -> when (piece.type) {
        PieceType.PAWN -> R.drawable.piece_bp
        PieceType.KNIGHT -> R.drawable.piece_bn
        PieceType.BISHOP -> R.drawable.piece_bb
        PieceType.ROOK -> R.drawable.piece_br
        PieceType.QUEEN -> R.drawable.piece_bq
        PieceType.KING -> R.drawable.piece_bk
    }
}

/** Board row/column (top-left origin) to square, for the side shown at the bottom. */
fun squareAt(row: Int, column: Int, orientation: Side): Square =
    if (orientation == Side.WHITE) Square.of(column, BOARD_SIZE - 1 - row) else Square.of(BOARD_SIZE - 1 - column, row)

private fun DrawScope.drawLastMove() {
    drawRect(Honey.copy(alpha = LAST_MOVE_ALPHA))
    val length = size.minDimension * BRACKET
    val stroke = size.minDimension * THIN
    // Corner brackets: the last move is visible without relying on the tint.
    listOf(Offset(0f, 0f) to Offset(1f, 1f), Offset(size.width, 0f) to Offset(-1f, 1f), Offset(0f, size.height) to Offset(1f, -1f), Offset(size.width, size.height) to Offset(-1f, -1f))
        .forEach { (corner, direction) ->
            drawLine(Cocoa, corner, corner + Offset(direction.x * length, 0f), stroke)
            drawLine(Cocoa, corner, corner + Offset(0f, direction.y * length), stroke)
        }
}

private fun DrawScope.drawMarkers(selected: Boolean, target: Boolean, occupied: Boolean, check: Boolean, roles: List<SquareRole>) {
    val unit = size.minDimension
    if (selected) drawRect(Teal, style = Stroke(unit * THICK))
    if (target) {
        if (occupied) drawCircle(Teal, radius = unit * RING_RADIUS, style = Stroke(unit * THIN * 2))
        else drawCircle(Teal.copy(alpha = DOT_ALPHA), radius = unit * DOT_RADIUS)
    }
    if (check) drawCircle(Terracotta, radius = unit * RING_RADIUS, style = Stroke(unit * THICK))

    roles.distinct().forEach { role ->
        when (role) {
            SquareRole.TARGET -> drawCircle(Plum, radius = unit * RING_RADIUS, style = Stroke(unit * THICK))
            SquareRole.ATTACKER -> drawTriangle(Teal, Offset(unit * CORNER, unit * CORNER), unit * MARKER)
            SquareRole.DEFENDER -> drawRect(Cocoa, topLeft = Offset(size.width - unit * (CORNER + MARKER), unit * CORNER), size = Size(unit * MARKER, unit * MARKER))
            SquareRole.THREAT -> drawCross(Plum, Offset(size.width - unit * (CORNER + MARKER / 2), size.height - unit * (CORNER + MARKER / 2)), unit * MARKER / 2)
            SquareRole.MOVED -> drawCircle(Honey, radius = unit * MOVED_RADIUS, center = Offset(unit * (CORNER + MOVED_RADIUS), size.height - unit * (CORNER + MOVED_RADIUS)))
        }
    }
}

private fun DrawScope.drawTriangle(color: Color, topLeft: Offset, side: Float) {
    val path = Path().apply {
        moveTo(topLeft.x, topLeft.y + side)
        lineTo(topLeft.x + side / 2, topLeft.y)
        lineTo(topLeft.x + side, topLeft.y + side)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawCross(color: Color, center: Offset, half: Float) {
    val stroke = size.minDimension * THIN * 2
    drawLine(color, center + Offset(-half, -half), center + Offset(half, half), stroke, StrokeCap.Round)
    drawLine(color, center + Offset(-half, half), center + Offset(half, -half), stroke, StrokeCap.Round)
}

private fun DrawScope.drawArrow(move: Move, orientation: Side) {
    val cell = size.width / BOARD_SIZE
    fun center(square: Square): Offset {
        val column = if (orientation == Side.WHITE) square.file else BOARD_SIZE - 1 - square.file
        val row = if (orientation == Side.WHITE) BOARD_SIZE - 1 - square.rank else square.rank
        return Offset((column + 0.5f) * cell, (row + 0.5f) * cell)
    }
    val start = center(move.from)
    val end = center(move.to)
    val stroke = cell * ARROW_WIDTH
    val color = Ink.copy(alpha = ARROW_ALPHA)
    val direction = (end - start).let { it / it.getDistance() }
    val headBase = end - direction * (cell * ARROW_HEAD)
    drawLine(color, start, headBase, stroke, StrokeCap.Round)
    val normal = Offset(-direction.y, direction.x) * (cell * ARROW_HEAD * 0.6f)
    val head = Path().apply {
        moveTo(end.x, end.y)
        lineTo(headBase.x + normal.x, headBase.y + normal.y)
        lineTo(headBase.x - normal.x, headBase.y - normal.y)
        close()
    }
    drawPath(head, color)
}

private const val BOARD_SIZE = 8
private const val PIECE_INSET = 0.06f
private const val LAST_MOVE_ALPHA = 0.45f
private const val DOT_ALPHA = 0.75f
private const val DOT_RADIUS = 0.14f
private const val RING_RADIUS = 0.44f
private const val THICK = 0.07f
private const val THIN = 0.03f
private const val BRACKET = 0.22f
private const val CORNER = 0.06f
private const val MARKER = 0.2f
private const val MOVED_RADIUS = 0.08f
private const val ARROW_WIDTH = 0.16f
private const val ARROW_HEAD = 0.38f
private const val ARROW_ALPHA = 0.55f
