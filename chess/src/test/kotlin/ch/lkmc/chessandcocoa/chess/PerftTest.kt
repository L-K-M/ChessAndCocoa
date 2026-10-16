// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.chess

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Node counts from the Chess Programming Wiki "Perft Results" page.
 * Depths are chosen so the whole suite stays well under a minute.
 */
class PerftTest {

    private fun perft(pos: Position, depth: Int): Long {
        if (depth == 0) return 1L
        var nodes = 0L
        for (move in pos.legalMoves()) {
            nodes += perft(pos.play(move), depth - 1)
        }
        return nodes
    }

    private fun check(fen: String, depth: Int, expected: Long) {
        assertEquals(expected, perft(Position.fromFen(fen), depth), "perft($depth) of $fen")
    }

    @Test
    fun startPosition() {
        check(Position.START_FEN, 5, 4_865_609L)
    }

    @Test
    fun kiwipete() {
        check("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1", 4, 4_085_603L)
    }

    @Test
    fun position3() {
        check("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1", 6, 11_030_083L)
    }

    @Test
    fun position4() {
        check("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1", 4, 422_333L)
    }

    @Test
    fun position5() {
        check("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8", 4, 2_103_487L)
    }

    @Test
    fun position6() {
        check("r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10", 4, 3_894_594L)
    }
}
