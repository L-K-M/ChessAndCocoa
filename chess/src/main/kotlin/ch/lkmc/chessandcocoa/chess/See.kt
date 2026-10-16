// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.chess

/**
 * Static exchange evaluation: the classic swap-list algorithm. Captures on
 * the target square alternate sides, always by the least valuable attacker,
 * and removing the capturer from its origin square reveals x-ray attackers
 * behind it. A king may join the sequence only as the very last capturer,
 * and only when the enemy has no attacker left to punish it.
 *
 * The score is from the mover's perspective. For a quiet move the first
 * "capture" is the opponent taking the moved piece, so the result is 0 or
 * negative. A promotion adds the mover's value gain on top.
 */
internal object See {

    // Ordering weight for choosing the least valuable attacker; the king's
    // PieceType.value is 0, which would sort first, so it gets its own.
    private fun worth(type: PieceType): Int = if (type == PieceType.KING) 20_000 else type.value

    fun evaluate(pos: Position, move: Move): Int {
        val b = pos.boardArray.copyOf()
        val ep = pos.enPassantSquare?.index ?: NO_EP
        val to = move.to.index
        val gains = IntArray(16)
        gains[0] = pos.capturedPiece(move)?.type?.value ?: 0
        applyMove(b, move.packed(), ep)

        var occupantValue = b[to]?.type?.value ?: 0
        var depth = 0
        var toMove = pos.sideToMove.opposite
        while (true) {
            val attacker = leastValuableAttacker(b, to, toMove) ?: break
            if (b[attacker]?.type == PieceType.KING && isAttacked(b, to, toMove.opposite)) {
                // The king could take, but the enemy still attacks the
                // square: the capture would be illegal.
                break
            }
            depth++
            if (depth == gains.size) break // unreachable in real positions
            gains[depth] = occupantValue - gains[depth - 1]
            occupantValue = b[attacker]?.type?.value ?: 0
            b[attacker] = null
            toMove = toMove.opposite
        }
        while (depth > 0) {
            gains[depth - 1] = -maxOf(-gains[depth - 1], gains[depth])
            depth--
        }
        var result = gains[0]
        if (move.promotion != null) {
            result += move.promotion.value - PieceType.PAWN.value
        }
        return result
    }

    private fun leastValuableAttacker(b: Board, target: Int, side: Side): Int? {
        var best = -1
        var bestWorth = Int.MAX_VALUE
        for (sq in attackersTo(b, target, side)) {
            val w = worth(b[sq]?.type ?: continue)
            if (w < bestWorth) {
                bestWorth = w
                best = sq
            }
        }
        return if (best < 0) null else best
    }
}
