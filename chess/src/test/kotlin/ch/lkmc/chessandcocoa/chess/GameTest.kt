// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameTest {

    private fun play(game: Game, vararg ucis: String): Game =
        ucis.fold(game) { g, u -> g.play(Move.parseUci(u)) }

    @Test
    fun constructionAndAccessors() {
        val game = play(Game.new(), "e2e4", "e7e5", "g1f3")
        assertEquals(3, game.ply)
        assertEquals(4, game.positions.size)
        assertEquals(Position.START_FEN, game.initialFen)
        assertEquals(listOf("e2e4", "e7e5", "g1f3"), game.uciMoves())
        assertEquals(listOf("e4", "e5", "Nf3"), game.sanMoves())
        assertEquals(game.positions.last(), game.current)
        assertEquals(game, Game.fromMoves(Position.START_FEN, game.moves))
    }

    @Test
    fun playRejectsIllegalAndFinished() {
        assertFailsWith<IllegalArgumentException> {
            Game.new().play(Move.parseUci("e2e5"))
        }
        val mate = play(Game.new(), "f2f3", "e7e5", "g2g4", "d8h4")
        assertEquals(OutcomeReason.CHECKMATE, mate.outcome()?.reason)
        assertFailsWith<IllegalArgumentException> { mate.play(Move.parseUci("a2a3")) }
    }

    @Test
    fun checkmateOutcomeWinner() {
        val mate = play(Game.new(), "f2f3", "e7e5", "g2g4", "d8h4")
        assertEquals(GameOutcome(Side.BLACK, OutcomeReason.CHECKMATE), mate.outcome())
    }

    @Test
    fun stalemateOutcome() {
        val game = play(Game.new("k7/8/8/1Q6/8/8/8/K7 w - - 0 1"), "b5b6")
        assertEquals(GameOutcome(null, OutcomeReason.STALEMATE), game.outcome())
    }

    @Test
    fun insufficientMaterialOutcome() {
        val game = play(Game.new("8/8/8/8/8/2k5/4p3/4K3 w - - 0 1"), "e1e2")
        assertEquals(GameOutcome(null, OutcomeReason.INSUFFICIENT_MATERIAL), game.outcome())
    }

    @Test
    fun threefoldRepetition() {
        var game = Game.new()
        val cycle = listOf("g1f3", "g8f6", "f3g1", "f6g8")
        // After the first cycle the start position has occurred twice.
        game = play(game, *cycle.toTypedArray())
        assertNull(game.outcome())
        // After the second, three times.
        game = play(game, *cycle.toTypedArray())
        assertEquals(GameOutcome(null, OutcomeReason.THREEFOLD_REPETITION), game.outcome())
    }

    @Test
    fun fiftyMoveRule() {
        var game = Game.new("r3k2r/8/8/8/8/8/8/R3K2R w - - 96 40")
        game = play(game, "a1a2", "a8a7", "a2a1", "a7a8")
        assertEquals(GameOutcome(null, OutcomeReason.FIFTY_MOVE_RULE), game.outcome())
    }

    @Test
    fun checkmateBeatsFifty() {
        // Clock hits 100 on the mating move: checkmate still wins.
        val game = play(Game.new("6k1/2Q5/5K2/8/8/8/8/8 w - - 99 50"), "c7g7")
        assertEquals(GameOutcome(Side.WHITE, OutcomeReason.CHECKMATE), game.outcome())
    }

    @Test
    fun undoAndTruncate() {
        val game = play(Game.new(), "e2e4", "e7e5", "g1f3", "b8c6")
        assertEquals(2, game.undo(2).ply)
        assertEquals(game.positions[2], game.undo(2).current)
        assertEquals(game.truncate(2), game.undo(2))
        assertEquals(Game.new(), game.truncate(0))
        assertFailsWith<IllegalArgumentException> { game.undo(5) }
        assertFailsWith<IllegalArgumentException> { game.truncate(5) }
        assertEquals(game, game.undo(0))
    }

    @Test
    fun pieceIdsTrackPieces() {
        var game = Game.new()
        assertEquals(PieceId("w-p-e2"), game.pieceIdAt(Square.parse("e2")))
        assertEquals(PieceId("b-n-g8"), game.pieceIdAt(Square.parse("g8")))
        game = play(game, "e2e4")
        assertEquals(PieceId("w-p-e2"), game.pieceIdAt(Square.parse("e4")))
        assertEquals(Square.parse("e4"), game.squareOf(PieceId("w-p-e2")))
        // The id at an earlier ply is unchanged.
        assertEquals(PieceId("w-p-e2"), game.pieceIdAt(Square.parse("e2"), ply = 0))
    }

    @Test
    fun pieceIdsThroughCaptureCastleEnPassantPromotion() {
        // Capture: knight takes a pawn; the pawn's id vanishes.
        var game = play(Game.new(), "e2e4", "d7d5", "e4d5")
        assertNull(game.squareOf(PieceId("b-p-d5")))
        assertEquals(PieceId("w-p-e2"), game.pieceIdAt(Square.parse("d5")))

        // Castling keeps king and rook ids.
        var castle = Game.new("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        castle = castle.play(Move.parseUci("e1g1"))
        assertEquals(PieceId("w-k-e1"), castle.pieceIdAt(Square.parse("g1")))
        assertEquals(PieceId("w-r-h1"), castle.pieceIdAt(Square.parse("f1")))

        // En passant removes the captured pawn's id.
        var ep = play(Game.new(), "e2e4", "a7a6", "e4e5", "d7d5", "e5d6")
        assertNull(ep.squareOf(PieceId("b-p-d5")))
        assertEquals(PieceId("w-p-e2"), ep.pieceIdAt(Square.parse("d6")))

        // Promotion keeps the pawn's id.
        var promo = Game.new("4k3/P7/8/8/8/8/8/4K3 w - - 0 1")
        promo = promo.play(Move.parseUci("a7a8q"))
        assertEquals(PieceId("w-p-a7"), promo.pieceIdAt(Square.parse("a8")))
        assertEquals(
            Piece(Side.WHITE, PieceType.QUEEN),
            promo.current.pieceAt(Square.parse("a8")),
        )
    }

    @Test
    fun pieceIdsFromCustomInitialFen() {
        val game = Game.new("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        assertEquals(PieceId("w-r-a1"), game.pieceIdAt(Square.parse("a1")))
        assertEquals(PieceId("b-r-h8"), game.pieceIdAt(Square.parse("h8")))
        assertNull(game.pieceIdAt(Square.parse("b1")))
    }

    @Test
    fun equality() {
        val a = play(Game.new(), "e2e4", "e7e5")
        val b = play(Game.new(), "e2e4", "e7e5")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, play(Game.new(), "e2e4"))
        assertNotEquals(a, play(Game.new("4k3/8/8/8/8/8/8/4K3 w - - 0 1")))
    }
}
