// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach.facts

import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.chess.Piece
import ch.lkmc.chessandcocoa.chess.PieceType
import ch.lkmc.chessandcocoa.chess.Position
import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.chess.Square

/**
 * Checkable board relations (concept Section 6.1). Everything here is
 * geometry or a legal-move fact; none of it is, by itself, a teaching point.
 */
object Facts {

    /** Pieces of [by] that attack [target], minus pieces pinned to their king off the target's line. */
    fun effectiveAttackers(position: Position, target: Square, by: Side): List<Square> {
        val pinned = position.pinnedPieces(by)
        val king = position.kingSquare(by)
        return position.attackers(target, by).filter { from ->
            val pinner = pinned[from] ?: return@filter true
            onLine(king, pinner, target)
        }
    }

    /** Defenders of the piece on [target]: its own side's effective attackers of that square. */
    fun defenders(position: Position, target: Square): List<Square> {
        val owner = position.pieceAt(target)?.side ?: return emptyList()
        return effectiveAttackers(position, target, owner)
    }

    /** Legal moves for the side to move that give checkmate at once. */
    fun mateInOneMoves(position: Position): List<Move> =
        position.legalMoves().filter { position.play(it).isCheckmate() }

    /**
     * Mates [side] would have if it could move now. A diagnostic for threat
     * discovery only (Section 6.3): the "pass" is never shown as a line.
     */
    fun threatenedMates(position: Position, side: Side): List<Move> {
        val asMover = if (position.sideToMove == side) position else position.passTurn() ?: return emptyList()
        return mateInOneMoves(asMover)
    }

    /** The best static exchange [side] can make on [target] if it were to move, or null when none is legal. */
    fun bestCaptureSee(position: Position, target: Square, side: Side): Int? {
        val asMover = if (position.sideToMove == side) position else position.passTurn() ?: return null
        return asMover.legalMoves()
            .filter { it.to == target && asMover.isCapture(it) }
            .maxOfOrNull { asMover.see(it) }
    }

    /**
     * Captures [side] could make right now that win material by static
     * exchange: (move, gain). Diagnostic when [side] is not to move.
     */
    fun winningCaptures(position: Position, side: Side): List<Pair<Move, Int>> {
        val asMover = if (position.sideToMove == side) position else position.passTurn() ?: return emptyList()
        return asMover.legalMoves()
            .filter { asMover.isCapture(it) }
            .map { it to asMover.see(it) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
    }

    /** Squares adjacent to [side]'s king. */
    fun kingZone(position: Position, side: Side): List<Square> {
        val king = position.kingSquare(side)
        return KING_STEPS.mapNotNull { (df, dr) -> king.offset(df, dr) }
    }

    fun pieceValue(piece: Piece?): Int = piece?.type?.value ?: 0

    fun isMinorOrMore(piece: Piece?): Boolean = pieceValue(piece) >= PieceType.KNIGHT.value

    /** Whether [c] lies on the line through [a] and [b] (rank, file or diagonal) and between or beyond them. */
    private fun onLine(a: Square, b: Square, c: Square): Boolean {
        val df1 = b.file - a.file
        val dr1 = b.rank - a.rank
        val df2 = c.file - a.file
        val dr2 = c.rank - a.rank
        return df1 * dr2 == dr1 * df2 && (df1 * df2 >= 0) && (dr1 * dr2 >= 0)
    }

    private val KING_STEPS = listOf(-1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1)
}
