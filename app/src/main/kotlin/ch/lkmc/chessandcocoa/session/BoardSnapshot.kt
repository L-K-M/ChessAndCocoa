// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.session

import ch.lkmc.chessandcocoa.chess.Game
import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.chess.Piece
import ch.lkmc.chessandcocoa.chess.PieceType
import ch.lkmc.chessandcocoa.chess.Position
import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.chess.Square
import ch.lkmc.chessandcocoa.coach.evidence.Continuation
import ch.lkmc.chessandcocoa.coach.evidence.MarkedSquare

/**
 * Everything the board needs to draw one position, computed by the session
 * so the UI never runs chess rules itself.
 */
data class BoardSnapshot(
    val pieces: Map<Square, Piece>,
    /** The side shown at the bottom. */
    val orientation: Side,
    val sideToMove: Side,
    val lastMove: Move?,
    val checkSquare: Square?,
    /** Legal moves by origin square; empty when the player may not move. */
    val legalMoves: Map<Square, List<Move>>,
) {
    companion object {
        fun of(position: Position, orientation: Side, lastMove: Move?, playable: Boolean): BoardSnapshot = BoardSnapshot(
            pieces = position.pieces(),
            orientation = orientation,
            sideToMove = position.sideToMove,
            lastMove = lastMove,
            checkSquare = if (position.isCheck()) position.kingSquare(position.sideToMove) else null,
            legalMoves = if (playable) position.legalMoves().groupBy { it.from } else emptyMap(),
        )

        fun of(game: Game, orientation: Side, playable: Boolean): BoardSnapshot =
            of(game.current, orientation, game.moves.lastOrNull(), playable)
    }
}

/** One step of a "Show me why" replay. */
data class EvidenceFrame(val board: BoardSnapshot, val san: String?, val marks: List<MarkedSquare> = emptyList())

enum class EvidenceLine { MAIN, ALTERNATIVE }

/** Replays a claim's continuation from its own start position (R11): frame 0 is the start. */
fun Continuation.frames(orientation: Side, lastMove: Move?, marks: List<MarkedSquare> = emptyList()): List<EvidenceFrame> {
    var position = Position.fromFen(startFen)
    // Piece marks follow their participants; an empty-square target stays anchored to its square.
    val anchored = marks.filter { position.pieceAt(it.square) == null }
    var participants = marks - anchored.toSet()
    val frames = mutableListOf(EvidenceFrame(BoardSnapshot.of(position, orientation, lastMove, playable = false), null, marks))
    moves.zip(sans).forEach { (move, san) ->
        val captured = position.capturedSquare(move)
        val next = position.play(move)
        val destinations = mutableMapOf(move.from to move.to)
        if (position.isCastling(move)) {
            val beforeRooks = position.pieces(position.sideToMove).filterValues { it.type == PieceType.ROOK }.keys
            val afterRooks = next.pieces(position.sideToMove).filterValues { it.type == PieceType.ROOK }.keys
            destinations[(beforeRooks - afterRooks).single()] = (afterRooks - beforeRooks).single()
        }
        participants = participants.filterNot { it.square == captured }.map {
            it.copy(square = destinations[it.square] ?: it.square)
        }
        position = next
        frames += EvidenceFrame(BoardSnapshot.of(position, orientation, move, playable = false), san, (anchored + participants).distinct())
    }
    return frames
}
