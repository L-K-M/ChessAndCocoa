// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.chess

import kotlin.math.abs

/**
 * An immutable chess position with full rule state. This is the single
 * authority on legality (concept Section 14.3): no other module implements
 * move rules.
 *
 * Attack queries are geometric, like python-chess's: a pinned piece still
 * "attacks". Use [pinnedPieces] and [legalMoves] to tell geometry from what
 * can actually be played.
 */
class Position private constructor(
    private val board: Board,
    val sideToMove: Side,
    val castling: CastlingRights,
    /** The en-passant target square from the FEN, whether or not a capture is legal. */
    val enPassantSquare: Square?,
    val halfmoveClock: Int,
    val fullmoveNumber: Int,
    private val whiteKing: Int,
    private val blackKing: Int,
) {
    /** The piece array. Callers in this package must treat it as read-only. */
    internal val boardArray: Board get() = board

    private val epIndex: Int get() = enPassantSquare?.index ?: NO_EP

    private fun kingIndex(side: Side): Int = if (side == Side.WHITE) whiteKing else blackKing

    fun pieceAt(square: Square): Piece? = board[square.index]

    /** Every occupied square and its piece. */
    fun pieces(): Map<Square, Piece> {
        val out = LinkedHashMap<Square, Piece>(32)
        for (i in 0..63) board[i]?.let { out[Square(i)] = it }
        return out
    }

    fun pieces(side: Side): Map<Square, Piece> {
        val out = LinkedHashMap<Square, Piece>(16)
        for (i in 0..63) board[i]?.let { if (it.side == side) out[Square(i)] = it }
        return out
    }

    fun squaresOf(side: Side, type: PieceType): List<Square> {
        val out = ArrayList<Square>(8)
        for (i in 0..63) board[i]?.let { if (it.side == side && it.type == type) out += Square(i) }
        return out
    }

    fun kingSquare(side: Side): Square = Square(kingIndex(side))

    /**
     * Legal moves in packed form, computed once. The computation is the
     * engine's hot path, and every caller in this package shares the result;
     * it stays lazy because most positions built during a search are never
     * asked for their moves. Legality checks mutate a scratch copy, never
     * the shared board, so the position stays strictly immutable.
     */
    private val legalPacked: List<Int> by lazy {
        val scratch = board.copyOf()
        val pseudo = pseudoMoves(scratch, sideToMove, castling, epIndex)
        val kingSq = kingIndex(sideToMove)
        pseudo.filter { leavesKingSafe(scratch, sideToMove, kingSq, it, epIndex) }.sorted()
    }

    /** Legal moves for the side to move, in a deterministic order. */
    fun legalMoves(): List<Move> = legalPacked.map(::unpackMove)

    fun legalMovesFrom(square: Square): List<Move> =
        legalMoves().filter { it.from == square }

    fun isLegal(move: Move): Boolean {
        val piece = board[move.from.index] ?: return false
        return piece.side == sideToMove && legalPacked.contains(move.packed())
    }

    /** The position after [move]; throws [IllegalArgumentException] when it is illegal. */
    fun play(move: Move): Position {
        require(isLegal(move)) { "Illegal move ${move.uci} in $this" }
        val from = move.from.index
        val to = move.to.index
        val piece = board[from] ?: error("unreachable: legal move has a piece")
        val b = board.copyOf()
        applyMove(b, move.packed(), epIndex)

        var wk = castling.whiteKingSide
        var wq = castling.whiteQueenSide
        var bk = castling.blackKingSide
        var bq = castling.blackQueenSide
        if (piece.type == PieceType.KING) {
            if (piece.side == Side.WHITE) {
                wk = false; wq = false
            } else {
                bk = false; bq = false
            }
        }
        // A rook leaving its corner, or a rook captured on it, kills that
        // right. A piece merely landing on a corner is harmless: a live
        // right implies the corner's own rook still sits there.
        when (from) {
            0 -> wq = false; 7 -> wk = false; 56 -> bq = false; 63 -> bk = false
        }
        when (to) {
            0 -> wq = false; 7 -> wk = false; 56 -> bq = false; 63 -> bk = false
        }

        val newEp = if (piece.type == PieceType.PAWN && abs(to - from) == 16) (from + to) / 2 else NO_EP
        val newHalf = if (piece.type == PieceType.PAWN || capturedPiece(move) != null) 0 else halfmoveClock + 1
        val newFull = if (sideToMove == Side.BLACK) fullmoveNumber + 1 else fullmoveNumber
        val newWk = if (piece.side == Side.WHITE && piece.type == PieceType.KING) to else whiteKing
        val newBk = if (piece.side == Side.BLACK && piece.type == PieceType.KING) to else blackKing

        return Position(
            b, sideToMove.opposite, CastlingRights(wk, wq, bk, bq),
            if (newEp < 0) null else Square(newEp), newHalf, newFull, newWk, newBk,
        )
    }

    fun isCheck(): Boolean = isAttacked(board, kingIndex(sideToMove), sideToMove.opposite)

    fun isCheckmate(): Boolean = isCheck() && !hasAnyLegalMove()

    fun isStalemate(): Boolean = !isCheck() && !hasAnyLegalMove()

    private fun hasAnyLegalMove(): Boolean =
        anyLegal(board.copyOf(), sideToMove, castling, epIndex, kingIndex(sideToMove))

    /** Neither side can mate by any legal sequence (K v K, K+minor v K, same-colour bishops). */
    fun hasInsufficientMaterial(): Boolean {
        var knights = 0
        var bishopColors = 0
        for (i in 0..63) {
            val p = board[i] ?: continue
            when (p.type) {
                PieceType.PAWN, PieceType.ROOK, PieceType.QUEEN -> return false
                PieceType.KNIGHT -> knights++
                PieceType.BISHOP -> bishopColors = bishopColors or (1 shl ((i + (i shr 3)) and 1))
                PieceType.KING -> Unit
            }
        }
        if (knights == 0) return bishopColors != 3
        return knights == 1 && bishopColors == 0
    }

    fun isCapture(move: Move): Boolean = capturedPiece(move) != null

    /** The piece [move] captures, including en passant; null for a quiet move. */
    fun capturedPiece(move: Move): Piece? = capturedSquare(move)?.let { board[it.index] }

    /** Where the captured piece stood (differs from move.to for en passant). */
    fun capturedSquare(move: Move): Square? {
        if (isEnPassant(move)) return Square((move.from.index and 56) or (move.to.index and 7))
        val piece = board[move.from.index] ?: return null
        if (piece.side != sideToMove) return null
        return if (board[move.to.index] != null) move.to else null
    }

    fun isEnPassant(move: Move): Boolean {
        val piece = board[move.from.index] ?: return false
        return piece.type == PieceType.PAWN && move.to.index == epIndex &&
            move.from.file != move.to.file && board[move.to.index] == null
    }

    fun isCastling(move: Move): Boolean {
        val piece = board[move.from.index] ?: return false
        return piece.type == PieceType.KING && abs(move.to.file - move.from.file) == 2
    }

    fun givesCheck(move: Move): Boolean = play(move).isCheck()

    /** Squares the piece on [from] attacks geometrically (pawns: diagonal captures only). */
    fun attackedSquares(from: Square): Set<Square> =
        attackedFrom(board, from.index).mapTo(LinkedHashSet()) { Square(it) }

    /** Squares of [by]'s pieces that geometrically attack [target], pinned pieces included. */
    fun attackers(target: Square, by: Side): List<Square> =
        attackersTo(board, target.index, by).sorted().map { Square(it) }

    fun isAttacked(target: Square, by: Side): Boolean = isAttacked(board, target.index, by)

    /** [side]'s absolutely pinned pieces, mapped to the square of the pinning piece. */
    fun pinnedPieces(side: Side): Map<Square, Square> =
        pinnedMap(board, side, kingIndex(side))
            .mapKeys { Square(it.key) }
            .mapValues { Square(it.value) }

    /**
     * Static exchange estimate of [move] for the mover, in [PieceType.value]
     * units: the material balance of the best capture sequence on move.to,
     * with x-rays. A filter, not proof (concept Section 6.1).
     */
    fun see(move: Move): Int = See.evaluate(this, move)

    /** Sum of [PieceType.value] over [side]'s pieces (king counts 0). */
    fun material(side: Side): Int {
        var sum = 0
        for (i in 0..63) board[i]?.let { if (it.side == side) sum += it.type.value }
        return sum
    }

    /** Standard Algebraic Notation including "+" or "#", e.g. "Qxf7#", "O-O", "exd8=Q+". */
    fun san(move: Move): String = San.of(this, move)

    /** Parses SAN for this position (accepts with or without +/#/!/?); throws when illegal or ambiguous. */
    fun parseSan(san: String): Move = San.parse(this, san)

    fun fen(): String = Fen.write(this)

    /**
     * Identity for repetition: placement, side to move, castling rights, and
     * the en-passant square only when an en-passant capture is legal.
     */
    fun repetitionKey(): String = buildString(72) {
        append(Fen.placement(board))
        append(' ').append(if (sideToMove == Side.WHITE) 'w' else 'b')
        append(' ').append(Fen.castlingField(castling))
        append(' ').append(
            if (hasLegalEnPassant(board.copyOf(), sideToMove, epIndex, kingIndex(sideToMove))) {
                enPassantSquare?.name ?: "-"
            } else {
                "-"
            },
        )
    }

    /**
     * The same placement with the other side to move, or null when that is
     * not a legal position (the side to move is in check). For threat
     * discovery only: never present a "pass" as a legal continuation
     * (concept Section 6.3). Clears the en-passant square.
     */
    fun passTurn(): Position? {
        if (isCheck()) return null
        // The board array is shared: positions never mutate it.
        return Position(
            board, sideToMove.opposite, castling, null, halfmoveClock, fullmoveNumber, whiteKing, blackKing,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Position) return false
        return sideToMove == other.sideToMove && castling == other.castling &&
            enPassantSquare == other.enPassantSquare && halfmoveClock == other.halfmoveClock &&
            fullmoveNumber == other.fullmoveNumber && board.contentEquals(other.board)
    }

    override fun hashCode(): Int {
        var h = board.contentHashCode()
        h = 31 * h + sideToMove.hashCode()
        h = 31 * h + castling.hashCode()
        h = 31 * h + (enPassantSquare?.hashCode() ?: 0)
        h = 31 * h + halfmoveClock
        h = 31 * h + fullmoveNumber
        return h
    }

    override fun toString(): String = fen()

    companion object {
        const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        fun initial(): Position = fromFen(START_FEN)

        /** Parses a FEN; throws [IllegalArgumentException] for malformed or impossible input. */
        fun fromFen(fen: String): Position = Fen.parse(fen)

        /** Wraps a board and rule state; [wk]/[bk] are the king square indices. */
        internal fun of(
            board: Board,
            sideToMove: Side,
            castling: CastlingRights,
            epIndex: Int,
            halfmoveClock: Int,
            fullmoveNumber: Int,
            wk: Int,
            bk: Int,
        ): Position = Position(
            board, sideToMove, castling,
            if (epIndex < 0) null else Square(epIndex),
            halfmoveClock, fullmoveNumber, wk, bk,
        )
    }
}
