// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.ui.board

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import ch.lkmc.chessandcocoa.R
import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.chess.Piece
import ch.lkmc.chessandcocoa.chess.PieceType
import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.chess.Square
import ch.lkmc.chessandcocoa.coach.evidence.MarkedSquare
import ch.lkmc.chessandcocoa.coach.evidence.SquareRole
import ch.lkmc.chessandcocoa.session.BoardSnapshot
import ch.lkmc.chessandcocoa.ui.theme.BoardPalette
import ch.lkmc.chessandcocoa.ui.theme.CocoaTheme
import ch.lkmc.chessandcocoa.ui.theme.MarkColors

/** Board looks; readability first (Section 4.5). */
enum class BoardStyle { COCOA, HIGH_CONTRAST }

enum class Coordinates { SHOWN, HIDDEN }

/** The live game, or a replayed position ("Show me why") that must never be mistaken for it. */
enum class BoardFrame { GAME, EVIDENCE }

/** Whether the last move slides into place; only a move just played should. */
enum class MoveAnimation { SLIDE, NONE }

/** A full, accessible board, or a small picture of one (its caller describes it). */
enum class BoardDetail { FULL, THUMBNAIL }

/**
 * The chessboard. One canvas paints squares, pieces and marks; above it, 64
 * transparent squares are labelled buttons for screen readers and taps.
 * Every mark pairs colour with a distinct shape, so no meaning rests on
 * colour alone (R14):
 *
 * ```
 *   selected  tint + outline      legal move • dot / ◤ corners on a capture
 *   last move honey + brackets    check      ● disc under the king + ◯ ring
 *   target    ◯ mulberry ring     attacker   ▲ corner    defender ■ corner
 *   threat    ✕ corner            moved      ● corner
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
    frame: BoardFrame = BoardFrame.GAME,
    animation: MoveAnimation = MoveAnimation.NONE,
    detail: BoardDetail = BoardDetail.FULL,
    onSquareTap: ((Square) -> Unit)? = null,
) {
    val colors = CocoaTheme.colors
    val palette = if (style == BoardStyle.HIGH_CONTRAST) colors.highContrastBoard else colors.board
    val frameColor by animateColorAsState(if (frame == BoardFrame.EVIDENCE) colors.evidenceFrame else palette.frame, label = "frame")
    val frameWidth = if (detail == BoardDetail.FULL) FRAME_WIDTH else THUMBNAIL_FRAME_WIDTH

    val targets = selected?.let { board.legalMoves[it] }.orEmpty().map { it.to }.toSet()
    val roles = marks.groupBy({ it.square }, { it.role }).mapValues { (_, list) -> list.distinct() }
    val pieces = piecePainters()
    val measurer = rememberTextMeasurer(cacheSize = COORDINATE_LABELS)
    val coordinateStyle = MaterialTheme.typography.labelSmall

    val slides = rememberSlides(board, animation)
    val progress = remember(board.lastMove) { Animatable(if (animation == MoveAnimation.SLIDE && slides.isNotEmpty()) 0f else 1f) }
    LaunchedEffect(progress) {
        progress.animateTo(1f, tween(SLIDE_MILLIS, easing = FastOutSlowInEasing))
    }

    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(if (detail == BoardDetail.FULL) OUTER_RADIUS else THUMBNAIL_RADIUS))
            .background(frameColor)
            .padding(frameWidth)
            .clip(RoundedCornerShape(INNER_RADIUS)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cell = size.width / BOARD_SIZE
            forEachSquare(board.orientation) { square, row, column ->
                val topLeft = Offset(column * cell, row * cell)
                translate(topLeft.x, topLeft.y) {
                    drawSquare(square, cell, palette, colors.marks, board, selected)
                    if (coordinates == Coordinates.SHOWN && detail == BoardDetail.FULL) {
                        drawCoordinates(square, row, column, cell, palette, measurer, coordinateStyle, roles[square].orEmpty())
                    }
                }
            }

            // Pieces: resting ones first, then any piece still sliding into place on top.
            val moving = slides.keys
            board.pieces.forEach { (square, piece) ->
                if (square !in moving) drawPiece(pieces.getValue(piece), squareOrigin(square, board.orientation, cell), cell)
            }
            slides.forEach { (to, from) ->
                val piece = board.pieces[to] ?: return@forEach
                val start = squareOrigin(from, board.orientation, cell)
                val end = squareOrigin(to, board.orientation, cell)
                drawPiece(pieces.getValue(piece), start + (end - start) * progress.value, cell)
            }

            forEachSquare(board.orientation) { square, row, column ->
                translate(column * cell, row * cell) {
                    drawOverlays(cell, colors.marks, target = square in targets, occupied = square in board.pieces, check = square == board.checkSquare, roles = roles[square].orEmpty())
                }
            }
            arrows.forEach { drawArrow(it, board.orientation, cell, colors.marks) }
        }

        if (detail == BoardDetail.FULL) {
            SquareLayer(board, selected, targets, roles, onSquareTap)
        }
    }
}

/** One board mark drawn on its own, larger and centred: the key of a legend. */
@Composable
fun MarkGlyph(role: SquareRole, modifier: Modifier = Modifier) {
    val colors = CocoaTheme.colors
    Canvas(modifier.size(GLYPH_SIZE).clip(RoundedCornerShape(GLYPH_RADIUS))) {
        val unit = size.minDimension
        val center = Offset(unit / 2, unit / 2)
        drawRect(colors.board.light)
        when (role) {
            SquareRole.TARGET -> drawCircle(colors.marks.target, radius = unit * GLYPH_RING, center = center, style = Stroke(unit * GLYPH_STROKE))
            SquareRole.ATTACKER -> drawTriangle(colors.marks.attacker, center - Offset(unit * GLYPH_SHAPE / 2, unit * GLYPH_SHAPE / 2), unit * GLYPH_SHAPE, 0f)
            SquareRole.DEFENDER -> drawRect(colors.marks.defender, topLeft = center - Offset(unit * GLYPH_SHAPE / 2.4f, unit * GLYPH_SHAPE / 2.4f), size = Size(unit * GLYPH_SHAPE / 1.2f, unit * GLYPH_SHAPE / 1.2f))
            SquareRole.THREAT -> drawCross(colors.marks.threat, center, unit * GLYPH_SHAPE / 2.4f, unit * GLYPH_STROKE)
            SquareRole.MOVED -> drawCircle(colors.marks.moved, radius = unit * GLYPH_SHAPE / 2.6f, center = center)
        }
    }
}

/** 64 labelled squares in reading order: what TalkBack and taps use. */
@Composable
private fun SquareLayer(
    board: BoardSnapshot,
    selected: Square?,
    targets: Set<Square>,
    roles: Map<Square, List<SquareRole>>,
    onTap: ((Square) -> Unit)?,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cell = maxWidth / BOARD_SIZE
        Column {
            for (row in 0 until BOARD_SIZE) {
                Row {
                    for (column in 0 until BOARD_SIZE) {
                        val square = squareAt(row, column, board.orientation)
                        val piece = board.pieces[square]
                        val description = squareDescription(square, piece)
                        val state = squareState(
                            selected = square == selected,
                            target = square in targets,
                            occupied = piece != null,
                            check = square == board.checkSquare,
                            lastMove = board.lastMove?.let { square == it.from || square == it.to } == true,
                            roles = roles[square].orEmpty(),
                        )
                        val tap = onTap?.let { handler -> Modifier.clickable(role = Role.Button) { handler(square) } } ?: Modifier
                        Box(
                            Modifier
                                .size(cell)
                                .then(tap)
                                .semantics {
                                    contentDescription = description
                                    if (state.isNotEmpty()) stateDescription = state
                                },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Which pieces slide, as destination to origin. Found by comparing the board
 * with the previous one, so a castling rook travels with its king and no
 * chess rule runs here; a capture's victim simply leaves.
 */
@Composable
private fun rememberSlides(board: BoardSnapshot, animation: MoveAnimation): Map<Square, Square> {
    val previous = remember { PreviousBoard() }
    val slides = remember(board.lastMove) {
        val before = previous.board
        val move = board.lastMove
        if (animation != MoveAnimation.SLIDE || before == null || move == null) return@remember emptyMap()

        val left = before.pieces.keys.filter { it !in board.pieces && it != move.from }
        val arrived = board.pieces.filter { (square, piece) -> square != move.to && before.pieces[square] != piece }
        val companion = arrived.entries.singleOrNull()?.let { (to, piece) ->
            left.singleOrNull { before.pieces[it] == piece }?.let { from -> to to from }
        }
        listOfNotNull(move.to to move.from, companion).toMap()
    }
    SideEffect { previous.board = board }
    return slides
}

private class PreviousBoard {
    var board: BoardSnapshot? = null
}

@Composable
private fun piecePainters(): Map<Piece, Painter> =
    Side.entries.flatMap { side -> PieceType.entries.map { Piece(side, it) } }.associateWith { painterResource(drawableFor(it)) }

@Composable
private fun squareDescription(square: Square, piece: Piece?): String {
    if (piece == null) return stringResource(R.string.square_empty, square.name)
    return stringResource(R.string.square_piece, square.name, pieceName(piece))
}

@Composable
fun pieceName(piece: Piece): String {
    val side = stringResource(if (piece.side == Side.WHITE) R.string.color_white else R.string.color_black)
    return stringResource(R.string.piece_named, side, stringResource(pieceTypeName(piece.type)))
}

fun pieceTypeName(type: PieceType): Int = when (type) {
    PieceType.PAWN -> R.string.piece_pawn
    PieceType.KNIGHT -> R.string.piece_knight
    PieceType.BISHOP -> R.string.piece_bishop
    PieceType.ROOK -> R.string.piece_rook
    PieceType.QUEEN -> R.string.piece_queen
    PieceType.KING -> R.string.piece_king
}

@Composable
private fun squareState(selected: Boolean, target: Boolean, occupied: Boolean, check: Boolean, lastMove: Boolean, roles: List<SquareRole>): String {
    val parts = buildList {
        if (selected) add(stringResource(R.string.state_selected))
        if (target) add(stringResource(if (occupied) R.string.state_capture else R.string.state_move_here))
        if (check) add(stringResource(R.string.state_check))
        if (lastMove) add(stringResource(R.string.state_last_move))
        roles.forEach { add(stringResource(roleLabel(it))) }
    }
    return parts.joinToString(", ")
}

fun roleLabel(role: SquareRole): Int = when (role) {
    SquareRole.TARGET -> R.string.mark_target
    SquareRole.ATTACKER -> R.string.mark_attacker
    SquareRole.DEFENDER -> R.string.mark_defender
    SquareRole.MOVED -> R.string.mark_moved
    SquareRole.THREAT -> R.string.mark_threat
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

private inline fun forEachSquare(orientation: Side, action: (Square, Int, Int) -> Unit) {
    for (row in 0 until BOARD_SIZE) {
        for (column in 0 until BOARD_SIZE) action(squareAt(row, column, orientation), row, column)
    }
}

private fun squareOrigin(square: Square, orientation: Side, cell: Float): Offset {
    val column = if (orientation == Side.WHITE) square.file else BOARD_SIZE - 1 - square.file
    val row = if (orientation == Side.WHITE) BOARD_SIZE - 1 - square.rank else square.rank
    return Offset(column * cell, row * cell)
}

private fun isLight(square: Square) = (square.file + square.rank) % 2 == 1

/** Square colour, then what sits under the piece: last move, selection. */
private fun DrawScope.drawSquare(square: Square, cell: Float, palette: BoardPalette, marks: MarkColors, board: BoardSnapshot, selected: Square?) {
    drawRect(if (isLight(square)) palette.light else palette.dark, size = Size(cell, cell))

    val lastMove = board.lastMove
    if (lastMove != null && (square == lastMove.from || square == lastMove.to)) {
        drawRect(marks.lastMove.copy(alpha = LAST_MOVE_ALPHA), size = Size(cell, cell))
        drawBrackets(cell, marks.lastMoveEdge.copy(alpha = BRACKET_ALPHA))
    }
    if (square == board.checkSquare) {
        drawCircle(marks.check.copy(alpha = CHECK_DISC_ALPHA), radius = cell * RING_RADIUS, center = Offset(cell / 2, cell / 2))
    }
    if (square == selected) {
        drawRect(marks.guide.copy(alpha = SELECTED_ALPHA), size = Size(cell, cell))
        val stroke = cell * THICK
        drawRect(marks.guide, topLeft = Offset(stroke / 2, stroke / 2), size = Size(cell - stroke, cell - stroke), style = Stroke(stroke))
    }
}

/** Corner brackets: the last move reads without its tint. */
private fun DrawScope.drawBrackets(cell: Float, color: Color) {
    val length = cell * BRACKET
    val stroke = cell * THIN
    val inset = stroke / 2
    listOf(Offset(inset, inset) to Offset(1f, 1f), Offset(cell - inset, inset) to Offset(-1f, 1f), Offset(inset, cell - inset) to Offset(1f, -1f), Offset(cell - inset, cell - inset) to Offset(-1f, -1f))
        .forEach { (corner, direction) ->
            drawLine(color, corner, corner + Offset(direction.x * length, 0f), stroke, StrokeCap.Round)
            drawLine(color, corner, corner + Offset(0f, direction.y * length), stroke, StrokeCap.Round)
        }
}

/**
 * Rank numbers down the left edge, file letters along the bottom, in the
 * opposite square colour. A label gives way to a teaching mark in its corner.
 */
private fun DrawScope.drawCoordinates(
    square: Square,
    row: Int,
    column: Int,
    cell: Float,
    palette: BoardPalette,
    measurer: TextMeasurer,
    base: TextStyle,
    roles: List<SquareRole>,
) {
    val color = if (isLight(square)) palette.coordinateOnLight else palette.coordinateOnDark
    // Sized by the square, not the font scale: squares are already described in words.
    val style = base.copy(color = color, fontSize = (cell * COORDINATE_SIZE).toSp(), lineHeight = (cell * COORDINATE_SIZE).toSp())
    val pad = cell * COORDINATE_PAD
    if (column == 0 && SquareRole.ATTACKER !in roles) {
        drawText(measurer, "${square.rank + 1}", topLeft = Offset(pad, pad * 0.6f), style = style)
    }
    if (row == BOARD_SIZE - 1 && SquareRole.THREAT !in roles) {
        val label = measurer.measure("${'a' + square.file}", style)
        drawText(label, topLeft = Offset(cell - label.size.width - pad, cell - label.size.height - pad * 0.4f))
    }
}

private fun DrawScope.drawPiece(painter: Painter, origin: Offset, cell: Float) {
    val inset = cell * PIECE_INSET
    translate(origin.x + inset, origin.y + inset) {
        with(painter) { draw(Size(cell - 2 * inset, cell - 2 * inset)) }
    }
}

/** What sits above the piece: legal moves, check, and the coach's marks, each with a halo. */
private fun DrawScope.drawOverlays(cell: Float, marks: MarkColors, target: Boolean, occupied: Boolean, check: Boolean, roles: List<SquareRole>) {
    val center = Offset(cell / 2, cell / 2)
    if (target) {
        if (occupied) drawCaptureCorners(cell, marks.guide.copy(alpha = CAPTURE_ALPHA))
        else drawCircle(marks.guide.copy(alpha = DOT_ALPHA), radius = cell * DOT_RADIUS, center = center)
    }
    if (check) haloed(marks.halo) { color -> drawCircle(color ?: marks.check, radius = cell * RING_RADIUS, center = center, style = Stroke(cell * THICK + halo(color))) }

    roles.forEach { role ->
        when (role) {
            SquareRole.TARGET -> haloed(marks.halo) { color -> drawCircle(color ?: marks.target, radius = cell * RING_RADIUS, center = center, style = Stroke(cell * THICK + halo(color))) }
            SquareRole.ATTACKER -> haloed(marks.halo) { color -> drawTriangle(color ?: marks.attacker, Offset(cell * CORNER, cell * CORNER), cell * MARKER, halo(color)) }
            SquareRole.DEFENDER -> haloed(marks.halo) { color ->
                val grow = halo(color) / 2
                drawRect(color ?: marks.defender, topLeft = Offset(cell - cell * (CORNER + MARKER) - grow, cell * CORNER - grow), size = Size(cell * MARKER + 2 * grow, cell * MARKER + 2 * grow))
            }
            SquareRole.THREAT -> haloed(marks.halo) { color ->
                drawCross(color ?: marks.threat, Offset(cell - cell * (CORNER + MARKER / 2), cell - cell * (CORNER + MARKER / 2)), cell * MARKER / 2, cell * THIN * 2 + halo(color))
            }
            SquareRole.MOVED -> haloed(marks.halo) { color ->
                drawCircle(color ?: marks.moved, radius = cell * MOVED_RADIUS + halo(color) / 2, center = Offset(cell * (CORNER + MOVED_RADIUS), cell - cell * (CORNER + MOVED_RADIUS)))
            }
        }
    }
}

/** A capture: four wedges filling the square's corners, clear of the piece in the middle. */
private fun DrawScope.drawCaptureCorners(cell: Float, color: Color) {
    val wedge = cell * CAPTURE_WEDGE
    listOf(Offset(0f, 0f) to Offset(1f, 1f), Offset(cell, 0f) to Offset(-1f, 1f), Offset(0f, cell) to Offset(1f, -1f), Offset(cell, cell) to Offset(-1f, -1f))
        .forEach { (corner, direction) ->
            val path = Path().apply {
                moveTo(corner.x, corner.y)
                lineTo(corner.x + direction.x * wedge, corner.y)
                lineTo(corner.x, corner.y + direction.y * wedge)
                close()
            }
            drawPath(path, color)
        }
}

/** Draws a mark twice: a light halo (colour given) under the mark itself (colour null). */
private inline fun DrawScope.haloed(halo: Color, draw: DrawScope.(Color?) -> Unit) {
    draw(halo.copy(alpha = HALO_ALPHA))
    draw(null)
}

private fun DrawScope.halo(color: Color?): Float = if (color == null) 0f else HALO_WIDTH.toPx()

private fun DrawScope.drawTriangle(color: Color, topLeft: Offset, side: Float, grow: Float) {
    val path = Path().apply {
        moveTo(topLeft.x - grow / 2, topLeft.y + side + grow / 3)
        lineTo(topLeft.x + side / 2, topLeft.y - grow / 2)
        lineTo(topLeft.x + side + grow / 2, topLeft.y + side + grow / 3)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawCross(color: Color, center: Offset, half: Float, stroke: Float) {
    drawLine(color, center + Offset(-half, -half), center + Offset(half, half), stroke, StrokeCap.Round)
    drawLine(color, center + Offset(-half, half), center + Offset(half, -half), stroke, StrokeCap.Round)
}

private fun DrawScope.drawArrow(move: Move, orientation: Side, cell: Float, marks: MarkColors) {
    val half = Offset(cell / 2, cell / 2)
    val start = squareOrigin(move.from, orientation, cell) + half
    val end = squareOrigin(move.to, orientation, cell) + half
    val direction = (end - start).let { it / it.getDistance() }
    val headBase = end - direction * (cell * ARROW_HEAD)
    val normal = Offset(-direction.y, direction.x) * (cell * ARROW_HEAD * ARROW_HEAD_SPREAD)
    val color = marks.arrow.copy(alpha = ARROW_ALPHA)

    drawLine(color, start + direction * (cell * ARROW_START), headBase, cell * ARROW_WIDTH, StrokeCap.Round)
    val head = Path().apply {
        moveTo(end.x, end.y)
        lineTo(headBase.x + normal.x, headBase.y + normal.y)
        lineTo(headBase.x - normal.x, headBase.y - normal.y)
        close()
    }
    drawPath(head, color)
}

private const val BOARD_SIZE = 8
private const val COORDINATE_LABELS = 16
private const val SLIDE_MILLIS = 220
private val FRAME_WIDTH = 6.dp
private val THUMBNAIL_FRAME_WIDTH = 3.dp
private val OUTER_RADIUS = 12.dp
private val THUMBNAIL_RADIUS = 8.dp
private val INNER_RADIUS = 6.dp
private val HALO_WIDTH = 2.dp
private const val HALO_ALPHA = 0.9f
private const val PIECE_INSET = 0.05f
private const val COORDINATE_SIZE = 0.2f
private const val COORDINATE_PAD = 0.06f
private const val LAST_MOVE_ALPHA = 0.5f
private const val BRACKET_ALPHA = 0.7f
private const val SELECTED_ALPHA = 0.28f
private const val DOT_ALPHA = 0.55f
private const val CAPTURE_ALPHA = 0.6f
private const val CAPTURE_WEDGE = 0.26f
private const val CHECK_DISC_ALPHA = 0.3f
private const val DOT_RADIUS = 0.15f
private const val RING_RADIUS = 0.44f
private const val THICK = 0.07f
private const val THIN = 0.035f
private const val BRACKET = 0.2f
private const val CORNER = 0.07f
private const val MARKER = 0.2f
private const val MOVED_RADIUS = 0.08f
private const val ARROW_WIDTH = 0.15f
private const val ARROW_HEAD = 0.4f
private const val ARROW_HEAD_SPREAD = 0.6f
private const val ARROW_START = 0.15f
private const val ARROW_ALPHA = 0.78f
private val GLYPH_SIZE = 20.dp
private val GLYPH_RADIUS = 4.dp
private const val GLYPH_RING = 0.34f
private const val GLYPH_STROKE = 0.13f
private const val GLYPH_SHAPE = 0.6f
