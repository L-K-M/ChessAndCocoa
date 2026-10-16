// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.session

import ch.lkmc.chessandcocoa.chess.Game
import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.chess.Piece
import ch.lkmc.chessandcocoa.chess.Position
import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.chess.Square
import ch.lkmc.chessandcocoa.coach.evidence.Continuation

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
data class EvidenceFrame(val board: BoardSnapshot, val san: String?)

/** Replays a claim's continuation from its own start position (R11): frame 0 is the start. */
fun Continuation.frames(orientation: Side, lastMove: Move?): List<EvidenceFrame> {
    var position = Position.fromFen(startFen)
    val frames = mutableListOf(EvidenceFrame(BoardSnapshot.of(position, orientation, lastMove, playable = false), null))
    moves.zip(sans).forEach { (move, san) ->
        position = position.play(move)
        frames += EvidenceFrame(BoardSnapshot.of(position, orientation, move, playable = false), san)
    }
    return frames
}
