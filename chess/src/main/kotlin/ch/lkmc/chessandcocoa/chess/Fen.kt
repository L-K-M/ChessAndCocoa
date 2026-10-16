// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.chess

/**
 * FEN reading and writing. [parse] is strict about the shape of the input
 * and about impossible positions (no king, doubled kings, pawns on the back
 * ranks, the side not to move already in check); it is lenient about a
 * stored en-passant square, which is kept verbatim like python-chess's
 * `fen(en_passant="fen")`.
 */
internal object Fen {

    private val PIECE_CHARS = "pnbrqkPNBRQK".toSet()

    fun parse(fen: String): Position {
        val fields = fen.trim().split(Regex("\\s+"))
        failIf(fields.size != 4 && fields.size != 6) { "expected 4 or 6 fields, got ${fields.size}" }

        val board = newBoard()
        val ranks = fields[0].split('/')
        failIf(ranks.size != 8) { "expected 8 ranks, got ${ranks.size}" }
        var whiteKings = 0
        var blackKings = 0
        for (row in 0..7) {
            val rank = 7 - row // first FEN row is rank 8 (index 7)
            var file = 0
            for (c in ranks[row]) {
                when {
                    c in '1'..'8' -> file += c - '0'
                    c in PIECE_CHARS -> {
                        failIf(file > 7) { "rank ${rank + 1} overflows" }
                        val side = if (c.isUpperCase()) Side.WHITE else Side.BLACK
                        val type = PieceType.fromLetter(c)
                        failIf(type == PieceType.PAWN && (rank == 0 || rank == 7)) {
                            "pawn on rank ${rank + 1}"
                        }
                        if (type == PieceType.KING) {
                            if (side == Side.WHITE) whiteKings++ else blackKings++
                        }
                        board[rank * 8 + file] = Piece(side, type)
                        file++
                    }
                    else -> fail("bad piece letter '$c'")
                }
            }
            failIf(file != 8) { "rank ${rank + 1} holds $file squares" }
        }
        failIf(whiteKings != 1) { "expected exactly one white king, got $whiteKings" }
        failIf(blackKings != 1) { "expected exactly one black king, got $blackKings" }

        val side = when (fields[1]) {
            "w" -> Side.WHITE
            "b" -> Side.BLACK
            else -> fail("bad side field '${fields[1]}'")
        }
        val castling = parseCastling(fields[2])
        val ep = when (fields[3]) {
            "-" -> NO_EP
            else -> {
                failIf(fields[3].length != 2 || fields[3][0] !in 'a'..'h' || fields[3][1] !in '1'..'8') {
                    "bad en-passant field '${fields[3]}'"
                }
                val sq = Square.parse(fields[3])
                failIf(sq.rank != 2 && sq.rank != 5) { "en-passant square on rank ${sq.rank + 1}" }
                sq.index
            }
        }
        val halfmove: Int
        val fullmove: Int
        if (fields.size == 6) {
            halfmove = fields[4].toIntOrNull() ?: fail("bad halfmove clock '${fields[4]}'")
            fullmove = fields[5].toIntOrNull() ?: fail("bad fullmove number '${fields[5]}'")
            failIf(halfmove < 0) { "negative halfmove clock" }
            failIf(fullmove < 1) { "fullmove number < 1" }
        } else {
            halfmove = 0
            fullmove = 1
        }

        var wk = -1
        var bk = -1
        for (i in 0..63) {
            val p = board[i]
            if (p?.type == PieceType.KING) {
                if (p.side == Side.WHITE) wk = i else bk = i
            }
        }
        // The side that just moved cannot still be in check; this also
        // rejects adjacent kings and an absent enemy king's "capture".
        failIf(isAttacked(board, if (side == Side.WHITE) bk else wk, side)) {
            "side not to move is in check"
        }

        return Position.of(board, side, castling, ep, halfmove, fullmove, wk, bk)
    }

    private fun parseCastling(field: String): CastlingRights {
        if (field == "-") return CastlingRights.NONE
        failIf(field.isEmpty() || field.any { it !in "KQkq" } || field.toSet().size != field.length) {
            "bad castling field '$field'"
        }
        return CastlingRights(
            whiteKingSide = 'K' in field,
            whiteQueenSide = 'Q' in field,
            blackKingSide = 'k' in field,
            blackQueenSide = 'q' in field,
        )
    }

    fun write(pos: Position): String {
        val sb = StringBuilder(80)
        sb.append(placement(pos.boardArray))
        sb.append(' ').append(if (pos.sideToMove == Side.WHITE) 'w' else 'b')
        sb.append(' ').append(castlingField(pos.castling))
        sb.append(' ').append(pos.enPassantSquare?.name ?: "-")
        sb.append(' ').append(pos.halfmoveClock)
        sb.append(' ').append(pos.fullmoveNumber)
        return sb.toString()
    }

    fun placement(b: Board): String {
        val sb = StringBuilder(72)
        for (rank in 7 downTo 0) {
            var empty = 0
            for (file in 0..7) {
                val p = b[rank * 8 + file]
                if (p == null) {
                    empty++
                } else {
                    if (empty > 0) sb.append(empty)
                    empty = 0
                    sb.append(p.fenChar)
                }
            }
            if (empty > 0) sb.append(empty)
            if (rank > 0) sb.append('/')
        }
        return sb.toString()
    }

    fun castlingField(c: CastlingRights): String {
        val s = buildString {
            if (c.whiteKingSide) append('K')
            if (c.whiteQueenSide) append('Q')
            if (c.blackKingSide) append('k')
            if (c.blackQueenSide) append('q')
        }
        return s.ifEmpty { "-" }
    }

    private fun fail(message: String): Nothing = throw IllegalArgumentException("Bad FEN: $message")
    private inline fun failIf(condition: Boolean, message: () -> String) {
        if (condition) fail(message())
    }
}
