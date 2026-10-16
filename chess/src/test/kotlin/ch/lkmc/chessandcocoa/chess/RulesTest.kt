// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RulesTest {

    private fun uci(pos: Position, move: String) = Move.parseUci(move)
    private fun legalUcis(pos: Position) = pos.legalMoves().map { it.uci }

    // ---------------------------------------------------------- castling

    @Test
    fun castlingMovesAndBoardAfter() {
        val pos = Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val legal = legalUcis(pos)
        assertTrue("e1g1" in legal && "e1c1" in legal)

        val kingSide = pos.play(uci(pos, "e1g1"))
        assertEquals(Piece(Side.WHITE, PieceType.KING), kingSide.pieceAt(Square.parse("g1")))
        assertEquals(Piece(Side.WHITE, PieceType.ROOK), kingSide.pieceAt(Square.parse("f1")))
        assertNull(kingSide.pieceAt(Square.parse("e1")))
        assertNull(kingSide.pieceAt(Square.parse("h1")))
        assertEquals(CastlingRights(false, false, true, true), kingSide.castling)

        val queenSide = pos.play(uci(pos, "e1c1"))
        assertEquals(Piece(Side.WHITE, PieceType.KING), queenSide.pieceAt(Square.parse("c1")))
        assertEquals(Piece(Side.WHITE, PieceType.ROOK), queenSide.pieceAt(Square.parse("d1")))

        assertTrue(pos.isCastling(uci(pos, "e1g1")))
        assertTrue("e8g8" in legalUcis(pos.play(uci(pos, "a1a2"))))
    }

    @Test
    fun castlingThroughCheck() {
        // Black rook on f2 covers f1: king may not pass through it.
        val through = Position.fromFen("r3k2r/8/8/8/8/8/5r2/R3K2R w KQ - 0 1")
        assertFalse("e1g1" in legalUcis(through))
        assertTrue("e1c1" in legalUcis(through))
        // Black rook on g2 covers the landing square g1.
        val into = Position.fromFen("r3k2r/8/8/8/8/8/6r1/R3K2R w KQ - 0 1")
        assertFalse("e1g1" in legalUcis(into))
        assertTrue("e1c1" in legalUcis(into))
    }

    @Test
    fun castlingOutOfCheck() {
        val pos = Position.fromFen("r3k2r/8/8/8/8/8/4r3/R3K2R w KQ - 0 1")
        assertTrue(pos.isCheck())
        assertFalse("e1g1" in legalUcis(pos))
        assertFalse("e1c1" in legalUcis(pos))
    }

    @Test
    fun castlingWithKingsOwnShadow() {
        // With the king removed from e1 the black rook on a1 would see g1
        // through the empty squares: the landing square is attacked even
        // though the final rook on f1 would block the ray.
        val pos = Position.fromFen("r3k2r/8/8/8/8/8/8/r3K2R w K - 0 1")
        assertFalse("e1g1" in legalUcis(pos))
        // An own piece on b1 blocks the ray instead: castling is legal.
        val blocked = Position.fromFen("r3k2r/8/8/8/8/8/8/rN2K2R w K - 0 1")
        assertTrue("e1g1" in legalUcis(blocked))
    }

    @Test
    fun castlingQueensideTransit() {
        // d1 covered: queenside illegal. b1 covered: legal (the king never
        // stands on b1).
        val blocked = Position.fromFen("r3k2r/8/8/8/8/8/3r4/R3K2R w Q - 0 1")
        assertFalse("e1c1" in legalUcis(blocked))
        val fine = Position.fromFen("r3k2r/8/8/8/8/8/1r6/R3K2R w Q - 0 1")
        assertTrue("e1c1" in legalUcis(fine))
    }

    @Test
    fun castlingRightsLosses() {
        val pos = Position.fromFen("r3k2r/8/6N1/8/8/8/8/R3K2R w KQkq - 0 1")
        // Capturing the h8 rook removes black's king-side right only.
        val captured = pos.play(uci(pos, "g6h8"))
        assertEquals(CastlingRights(true, true, false, true), captured.castling)
        // Moving a rook removes that side's right only.
        val moved = pos.play(uci(pos, "a1a2"))
        assertEquals(CastlingRights(true, false, true, true), moved.castling)
        // Moving the king removes both of its side's rights.
        val king = pos.play(uci(pos, "e1e2"))
        assertEquals(CastlingRights(false, false, true, true), king.castling)
        // A dead right does nothing once the rook is gone.
        val ghost = Position.fromFen("r3k3/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        assertFalse("e8g8" in legalUcis(ghost.play(uci(ghost, "a1a2"))))
    }

    // -------------------------------------------------------- en passant

    @Test
    fun enPassantBasics() {
        val pos = Position.fromFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2")
        val move = uci(pos, "e5d6")
        assertTrue(pos.isLegal(move))
        assertTrue(pos.isEnPassant(move))
        assertTrue(pos.isCapture(move))
        assertEquals(Piece(Side.BLACK, PieceType.PAWN), pos.capturedPiece(move))
        assertEquals(Square.parse("d5"), pos.capturedSquare(move))
        val next = pos.play(move)
        assertEquals(Piece(Side.WHITE, PieceType.PAWN), next.pieceAt(Square.parse("d6")))
        assertNull(next.pieceAt(Square.parse("d5")))
        assertNull(next.enPassantSquare)
        assertEquals(0, next.halfmoveClock)
    }

    @Test
    fun enPassantHorizontalPinIsIllegal() {
        // bxc6 would clear rank 5 between Ka5 and Rh5.
        val pos = Position.fromFen("8/8/8/KPp4r/8/8/8/7k w - c6 0 1")
        val move = uci(pos, "b5c6")
        assertTrue(pos.isEnPassant(move))
        assertFalse(pos.isLegal(move))
        assertFalse("b5c6" in legalUcis(pos))
        // fen() still reports the stored square; repetitionKey drops it.
        assertEquals("8/8/8/KPp4r/8/8/8/7k w - c6 0 1", pos.fen())
        assertEquals("-", pos.repetitionKey().substringAfterLast(' '))
    }

    @Test
    fun enPassantSetsAndClearsSquare() {
        var pos = Position.initial()
        pos = pos.play(uci(pos, "e2e4"))
        assertEquals(Square.parse("e3"), pos.enPassantSquare)
        // fen() keeps the stored square; repetitionKey drops it because no
        // black pawn can capture en passant.
        assertEquals("e3", pos.fen().split(' ')[3])
        assertEquals("-", pos.repetitionKey().substringAfterLast(' '))
        pos = pos.play(uci(pos, "d7d5"))
        assertEquals(Square.parse("d6"), pos.enPassantSquare)
        pos = pos.play(uci(pos, "e4d5"))
        assertNull(pos.enPassantSquare)
    }

    // --------------------------------------------------------- promotion

    @Test
    fun promotions() {
        val pos = Position.fromFen("4k3/P7/8/8/8/8/8/4K3 w - - 0 1")
        val legal = legalUcis(pos)
        for (suffix in listOf("q", "r", "b", "n")) {
            assertTrue("a7a8$suffix" in legal, "a7a8$suffix missing")
        }
        // A pawn move to the last rank without a promotion piece is rejected.
        assertFalse(pos.isLegal(uci(pos, "a7a8")))
        assertFailsWith<IllegalArgumentException> { pos.play(uci(pos, "a7a8")) }
        // Pawn and king are not valid promotions.
        assertFalse(pos.isLegal(Move(Square.parse("a7"), Square.parse("a8"), PieceType.KING)))

        val next = pos.play(uci(pos, "a7a8n"))
        assertEquals(Piece(Side.WHITE, PieceType.KNIGHT), next.pieceAt(Square.parse("a8")))
        assertEquals(Piece(Side.WHITE, PieceType.QUEEN), pos.play(uci(pos, "a7a8q")).pieceAt(Square.parse("a8")))
    }

    @Test
    fun promotionCapture() {
        val pos = Position.fromFen("1r2k3/P7/8/8/8/8/8/4K3 w - - 0 1")
        val move = uci(pos, "a7b8q")
        assertTrue(pos.isLegal(move))
        assertTrue(pos.isCapture(move))
        assertEquals(Piece(Side.BLACK, PieceType.ROOK), pos.capturedPiece(move))
        assertEquals(Piece(Side.WHITE, PieceType.QUEEN), pos.play(move).pieceAt(Square.parse("b8")))
    }

    // -------------------------------------------------------- query APIs

    @Test
    fun givesCheck() {
        val pos = Position.fromFen("4k3/8/8/8/8/8/4P3/4K3 w - - 0 1")
        assertFalse(pos.givesCheck(uci(pos, "e2e4")))
        val backRank = Position.fromFen("6k1/8/8/8/8/8/8/4K2R w - - 0 1")
        assertTrue(backRank.givesCheck(uci(backRank, "h1h8")))
        assertFalse(backRank.givesCheck(uci(backRank, "h1h2")))
    }

    @Test
    fun checkmateAndStalemate() {
        val mate = Position.fromFen("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3")
        assertTrue(mate.isCheckmate())
        assertTrue(mate.legalMoves().isEmpty())

        val stale = Position.fromFen("k7/8/1Q6/8/8/8/8/K7 b - - 0 1")
        assertTrue(stale.isStalemate())
        assertFalse(stale.isCheck())
        assertTrue(stale.legalMoves().isEmpty())
    }

    @Test
    fun insufficientMaterialCases() {
        val yes = listOf(
            "8/8/8/8/8/8/3k4/5K2 w - - 0 1",   // K v K
            "8/8/8/8/8/8/3k4/5KN1 w - - 0 1",  // K+N v K
            "8/8/8/8/8/8/3kb3/6K1 w - - 0 1",  // K+B v K
            "8/8/8/8/8/8/3kb3/5K1B w - - 0 1", // same-colour bishops both sides (e2, h1)
        )
        val no = listOf(
            "8/8/8/8/8/8/3k4/5KNN w - - 0 1",  // K+NN v K
            "8/8/8/8/8/8/3kb3/5KB1 w - - 0 1", // opposite-colour bishops (e2, g1)
            "8/8/8/8/8/8/3kb3/5KN1 w - - 0 1", // K+B v K+N
            "8/8/8/8/8/8/3k4/5K1R w - - 0 1",  // K+R v K
            "8/8/8/8/8/8/3k3P/5K2 w - - 0 1",  // K+P v K
        )
        for (fen in yes) assertTrue(Position.fromFen(fen).hasInsufficientMaterial(), fen)
        for (fen in no) assertFalse(Position.fromFen(fen).hasInsufficientMaterial(), fen)
    }

    @Test
    fun geometricAttackQueries() {
        val pos = Position.fromFen("4k3/8/8/4r3/4B3/8/8/4K3 w - - 0 1")
        // The bishop is pinned along the e-file but still "attacks".
        assertTrue(pos.attackedSquares(Square.parse("e4")).contains(Square.parse("d5")))
        // The ray stops at the first piece: the rook attacks the bishop,
        // and pins it through to the king.
        assertTrue(pos.attackers(Square.parse("e4"), Side.BLACK).contains(Square.parse("e5")))
        assertEquals(
            mapOf(Square.parse("e4") to Square.parse("e5")),
            pos.pinnedPieces(Side.WHITE),
        )
        // Pawns attack only diagonally, not forward.
        val pawns = Position.fromFen("4k3/8/8/8/8/3p4/4P3/4K3 w - - 0 1")
        assertEquals(setOf(Square.parse("d3"), Square.parse("f3")), pawns.attackedSquares(Square.parse("e2")))
        assertEquals(setOf(Square.parse("c2"), Square.parse("e2")), pawns.attackedSquares(Square.parse("d3")))
    }

    @Test
    fun materialCounting() {
        val pos = Position.initial()
        assertEquals(3900, pos.material(Side.WHITE)) // 8p + 2n + 2b + 2r + q
        assertEquals(3900, pos.material(Side.BLACK))
    }

    @Test
    fun passTurn() {
        val pos = Position.fromFen("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1")
        val passed = assertNotNull(pos.passTurn())
        assertEquals(Side.WHITE, passed.sideToMove)
        assertNull(passed.enPassantSquare)
        assertEquals(pos.castling, passed.castling)
        assertEquals(pos.halfmoveClock, passed.halfmoveClock)
        assertEquals(pos.fullmoveNumber, passed.fullmoveNumber)
        assertEquals(pos.pieces(), passed.pieces())

        val inCheck = Position.fromFen("4k3/8/8/8/8/8/4r3/4K3 w - - 0 1")
        assertNull(inCheck.passTurn())
    }
}
