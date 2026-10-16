// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.chess

/**
 * An immutable chess position with full rule state. This is the single
 * authority on legality (concept Section 14.3): no other module implements
 * move rules.
 *
 * Attack queries are geometric, like python-chess's: a pinned piece still
 * "attacks". Use [pinnedPieces] and [legalMoves] to tell geometry from what
 * can actually be played.
 */
class Position private constructor() {

    val sideToMove: Side get() = TODO()
    val castling: CastlingRights get() = TODO()

    /** The en-passant target square from the FEN, whether or not a capture is legal. */
    val enPassantSquare: Square? get() = TODO()
    val halfmoveClock: Int get() = TODO()
    val fullmoveNumber: Int get() = TODO()

    fun pieceAt(square: Square): Piece? = TODO()

    /** Every occupied square and its piece. */
    fun pieces(): Map<Square, Piece> = TODO()

    fun pieces(side: Side): Map<Square, Piece> = TODO()

    fun squaresOf(side: Side, type: PieceType): List<Square> = TODO()

    fun kingSquare(side: Side): Square = TODO()

    /** Legal moves for the side to move, in a deterministic order. */
    fun legalMoves(): List<Move> = TODO()

    fun legalMovesFrom(square: Square): List<Move> = TODO()

    fun isLegal(move: Move): Boolean = TODO()

    /** The position after [move]; throws [IllegalArgumentException] when it is illegal. */
    fun play(move: Move): Position = TODO()

    fun isCheck(): Boolean = TODO()

    fun isCheckmate(): Boolean = TODO()

    fun isStalemate(): Boolean = TODO()

    /** Neither side can mate by any legal sequence (K v K, K+minor v K, same-colour bishops). */
    fun hasInsufficientMaterial(): Boolean = TODO()

    fun isCapture(move: Move): Boolean = TODO()

    /** The piece [move] captures, including en passant; null for a quiet move. */
    fun capturedPiece(move: Move): Piece? = TODO()

    /** Where the captured piece stood (differs from move.to for en passant). */
    fun capturedSquare(move: Move): Square? = TODO()

    fun isEnPassant(move: Move): Boolean = TODO()

    fun isCastling(move: Move): Boolean = TODO()

    fun givesCheck(move: Move): Boolean = TODO()

    /** Squares the piece on [from] attacks geometrically (pawns: diagonal captures only). */
    fun attackedSquares(from: Square): Set<Square> = TODO()

    /** Squares of [by]'s pieces that geometrically attack [target], pinned pieces included. */
    fun attackers(target: Square, by: Side): List<Square> = TODO()

    fun isAttacked(target: Square, by: Side): Boolean = TODO()

    /** [side]'s absolutely pinned pieces, mapped to the square of the pinning piece. */
    fun pinnedPieces(side: Side): Map<Square, Square> = TODO()

    /**
     * Static exchange estimate of [move] for the mover, in [PieceType.value]
     * units: the material balance of the best capture sequence on move.to,
     * with x-rays. A filter, not proof (concept Section 6.1).
     */
    fun see(move: Move): Int = TODO()

    /** Sum of [PieceType.value] over [side]'s pieces (king counts 0). */
    fun material(side: Side): Int = TODO()

    /** Standard Algebraic Notation including "+" or "#", e.g. "Qxf7#", "O-O", "exd8=Q+". */
    fun san(move: Move): String = TODO()

    /** Parses SAN for this position (accepts with or without +/#/!/?); throws when illegal or ambiguous. */
    fun parseSan(san: String): Move = TODO()

    fun fen(): String = TODO()

    /**
     * Identity for repetition: placement, side to move, castling rights, and
     * the en-passant square only when an en-passant capture is legal.
     */
    fun repetitionKey(): String = TODO()

    /**
     * The same placement with the other side to move, or null when that is
     * not a legal position (the side to move is in check). For threat
     * discovery only: never present a "pass" as a legal continuation
     * (concept Section 6.3). Clears the en-passant square.
     */
    fun passTurn(): Position? = TODO()

    override fun equals(other: Any?): Boolean = TODO()

    override fun hashCode(): Int = TODO()

    override fun toString(): String = fen()

    companion object {
        const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        fun initial(): Position = fromFen(START_FEN)

        /** Parses a FEN; throws [IllegalArgumentException] for malformed or impossible input. */
        fun fromFen(fen: String): Position = TODO()
    }
}
