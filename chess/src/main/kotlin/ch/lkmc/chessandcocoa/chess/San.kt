// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.chess

/**
 * Standard Algebraic Notation. [of] disambiguates against legal moves only
 * (file, then rank, then both) and appends "+" or "#". [parse] matches the
 * input against the SAN of every legal move, so it tolerates trailing
 * "!?#+" and accepts "0-0" as well as "O-O".
 */
internal object San {

    fun of(pos: Position, move: Move): String {
        val piece = pos.pieceAt(move.from)
            ?: throw IllegalArgumentException("No piece on ${move.from}")
        val sb = StringBuilder(8)
        if (pos.isCastling(move)) {
            sb.append(if (move.to.file > move.from.file) "O-O" else "O-O-O")
        } else {
            val isPawn = piece.type == PieceType.PAWN
            val capture = pos.isCapture(move)
            if (!isPawn) {
                sb.append(piece.type.letter.uppercaseChar())
                sb.append(disambiguation(pos, move, piece.type))
            } else if (capture) {
                sb.append('a' + move.from.file)
            }
            if (capture) sb.append('x')
            sb.append(move.to.name)
            if (move.promotion != null) {
                sb.append('=').append(move.promotion.letter.uppercaseChar())
            }
        }
        val next = pos.play(move)
        if (next.isCheckmate()) {
            sb.append('#')
        } else if (next.isCheck()) {
            sb.append('+')
        }
        return sb.toString()
    }

    /**
     * The minimal disambiguation string: empty when no other legal move of
     * the same piece type reaches [move.to]; otherwise the from-file when
     * no rival shares it, else the from-rank when no rival shares it, else
     * both.
     */
    private fun disambiguation(pos: Position, move: Move, type: PieceType): String {
        val rivals = pos.legalMoves().filter {
            it != move && it.to == move.to && pos.pieceAt(it.from)?.type == type
        }
        if (rivals.isEmpty()) return ""
        val sameFile = rivals.any { it.from.file == move.from.file }
        val sameRank = rivals.any { it.from.rank == move.from.rank }
        val file = ('a' + move.from.file).toString()
        val rank = ('1' + move.from.rank).toString()
        return when {
            !sameFile -> file
            !sameRank -> rank
            else -> file + rank
        }
    }

    fun parse(pos: Position, san: String): Move {
        val wanted = normalize(san)
        for (move in pos.legalMoves()) {
            if (normalize(of(pos, move)) == wanted) return move
        }
        throw IllegalArgumentException("No legal move matches SAN '$san'")
    }

    /** Drops check/mate and annotation marks; maps zero castling to letter O. */
    private fun normalize(san: String): String =
        san.trim().trimEnd('!', '?', '+', '#').replace('0', 'O')
}
