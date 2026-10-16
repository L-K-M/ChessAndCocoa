// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.chess

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Hand-checked static exchange estimates, from the mover's perspective.
 */
class SeeTest {

    private fun see(fen: String, move: String): Int =
        Position.fromFen(fen).see(Move.parseUci(move))

    @Test
    fun defendedPawnTakenByKnight() {
        // Nxd5 wins a pawn (+100) and loses the knight to e6's pawn (-300).
        assertEquals(-200, see("4k3/8/4p3/3p4/5N2/8/8/4K3 w - - 0 1", "f4d5"))
    }

    @Test
    fun undefendedRookCapture() {
        assertEquals(500, see("r3k3/8/8/8/8/8/8/R3K3 w - - 0 1", "a1a8"))
    }

    @Test
    fun queenTradesForRook() {
        // Qxa8 wins the rook and is recaptured by the king: 500 - 900.
        assertEquals(-400, see("rk6/8/8/8/8/8/8/Q3K3 w - - 0 1", "a1a8"))
    }

    @Test
    fun xRayRookBattery() {
        // Re3xe6 is answered by Re8xe6, but the white rook behind on e1
        // sees through and wins the second rook: +500 - 500 + 500.
        assertEquals(500, see("4r1k1/8/4r3/8/8/4R3/8/4R1K1 w - - 0 1", "e3e6"))
        // Same without the battery: a dead swap.
        assertEquals(0, see("4r1k1/8/4r3/8/8/4R3/8/6K1 w - - 0 1", "e3e6"))
    }

    @Test
    fun kingCapturesOnlyLast() {
        // Nxa3 wins the rook; Ka2 may not recapture because the a8 rook
        // still covers a3.
        assertEquals(500, see("r3k3/8/8/8/2n5/R7/K7/8 b - - 0 1", "c4a3"))
        // Without the lurking rook the king recaptures for free: 500 - 300.
        assertEquals(200, see("4k3/8/8/8/2n5/R7/K7/8 b - - 0 1", "c4a3"))
    }

    @Test
    fun enPassantCountsAsPawn() {
        assertEquals(100, see("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2", "e5d6"))
        // A bishop covering d6 levels the swap.
        assertEquals(0, see("4kb2/8/8/3pP3/8/8/8/4K3 w - d6 0 2", "e5d6"))
    }

    @Test
    fun promotionValueGain() {
        // dxc8=Q wins the rook and the promotion bonus; b8's rook takes
        // the queen back: 500 + 800 - 900.
        assertEquals(400, see("1rr3k1/3P4/8/8/8/8/8/4K3 w - - 0 1", "d7c8q"))
        // Undefended: 500 + 800.
        assertEquals(1300, see("2r3k1/3P4/8/8/8/8/8/4K3 w - - 0 1", "d7c8q"))
    }

    @Test
    fun quietMoves() {
        // Safe square: nothing can capture the rook, so 0.
        assertEquals(0, see("4k3/8/8/8/8/8/8/R3K3 w - - 0 1", "a1a4"))
        // The queen steps onto a pawn's square and is lost.
        assertEquals(-900, see("4k3/8/8/1p6/8/8/8/Q3K3 w - - 0 1", "a1a4"))
    }
}
