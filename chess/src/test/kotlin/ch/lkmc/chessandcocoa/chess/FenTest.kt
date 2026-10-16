// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class FenTest {

    private fun roundTrip(fen: String) {
        assertEquals(fen, Position.fromFen(fen).fen())
    }

    @Test
    fun roundTrip() {
        roundTrip(Position.START_FEN)
        roundTrip("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1")
        // The stored en-passant square is written back even when no
        // capture is legal.
        roundTrip("rnbqkbnr/ppp1pppp/8/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 3")
        roundTrip("8/8/8/KPp4r/8/8/8/7k w - c6 0 1")
        roundTrip("4k3/8/8/8/8/8/8/4K3 b - - 17 42")
    }

    @Test
    fun fourFieldFenDefaultsClocks() {
        val pos = Position.fromFen("4k3/8/8/8/8/8/8/4K3 w - -")
        assertEquals(0, pos.halfmoveClock)
        assertEquals(1, pos.fullmoveNumber)
        assertEquals("4k3/8/8/8/8/8/8/4K3 w - - 0 1", pos.fen())
    }

    @Test
    fun rejectsMalformedShapes() {
        val bad = listOf(
            "8/8/8/8/8/8/8/K6k w - - 0",                    // 5 fields
            "8/8/8/8/8/8/8/K6k w - - 0 1 extra",
            "8/8/8/8/8/8/8 w - - 0 1",                     // 7 ranks
            "8/8/8/8/8/8/8/8/K6k w - - 0 1",               // 9 ranks
            "9/8/8/8/8/8/8/K6k w - - 0 1",                 // digit 9
            "0/8/8/8/8/8/8/K6k w - - 0 1",                 // digit 0
            "8/8/8/8/8/8/8/K5k w - - 0 1",                 // rank sums to 7
            "8/8/8/8/8/8/8/K7k w - - 0 1",                 // rank sums to 9
            "8/8/8/8/8/8/8/K6k x - - 0 1",                 // bad side
            "8/8/8/8/8/8/8/K6k w KK - 0 1",                // duplicate right
            "8/8/8/8/8/8/8/K6k w Kz - 0 1",                // bad right char
            "8/8/8/8/8/8/8/K6k w - e4 0 1",                // ep rank 4
            "8/8/8/8/8/8/8/K6k w - x3 0 1",                // bad ep file
            "8/8/8/8/8/8/8/K6k w - - x 1",                 // bad halfmove
            "8/8/8/8/8/8/8/K6k w - - -1 1",                // negative halfmove
            "8/8/8/8/8/8/8/K6k w - - 0 0",                 // fullmove < 1
            "8/8/8/8/8/8/8/K6k w - - 0 x",                 // bad fullmove
            "8/8/8/8/8/8/8/K6k w j - 0 1",                 // bad piece letter
            "8/8/8/8/8/8/8/K6k w X - 0 1",                 // bad castling letter
        )
        for (fen in bad) {
            assertFailsWith<IllegalArgumentException>(fen) { Position.fromFen(fen) }
        }
    }

    @Test
    fun rejectsImpossiblePositions() {
        val bad = listOf(
            "8/8/8/8/8/8/8/K7 w - - 0 1",                  // no black king
            "8/8/8/8/8/8/8/7k w - - 0 1",                  // no white king
            "8/8/8/8/8/8/8/KK5k w - - 0 1",                // two white kings
            "4k2k/8/8/8/8/8/8/K7 w - - 0 1",               // two black kings
            "P7/8/8/8/8/8/8/K6k w - - 0 1",                // white pawn rank 8
            "k7/8/8/8/8/8/8/K6p1 w - - 0 1",               // black pawn rank 1
            "4k3/8/8/8/8/8/8/4R1K1 w - - 0 1",             // black king in check, white to move
            "8/8/8/8/8/8/8/Kk6 w - - 0 1",                 // adjacent kings
        )
        for (fen in bad) {
            assertFailsWith<IllegalArgumentException>(fen) { Position.fromFen(fen) }
        }
        // The mirror of the check position is a normal in-check position.
        val checked = Position.fromFen("4k3/8/8/8/8/8/8/4R1K1 b - - 0 1")
        assertEquals(true, checked.isCheck())
    }

    @Test
    fun equalityIsFullFen() {
        val a = Position.initial()
        val b = Position.fromFen(Position.START_FEN)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        val moved = a.play(Move.parseUci("e2e4"))
        assertNotEquals(a, moved)
        assertEquals(moved, Position.fromFen(moved.fen()))
    }
}
