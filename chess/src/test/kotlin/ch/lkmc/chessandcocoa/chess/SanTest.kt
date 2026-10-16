// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SanTest {

    private fun uci(move: String) = Move.parseUci(move)

    private fun sanOf(fen: String, move: String): String =
        Position.fromFen(fen).san(uci(move))

    @Test
    fun basics() {
        val pos = Position.initial()
        assertEquals("e4", pos.san(uci("e2e4")))
        assertEquals("Nf3", pos.san(uci("g1f3")))
        assertEquals("O-O", Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1").san(uci("e1g1")))
        assertEquals("O-O-O", Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1").san(uci("e1c1")))
    }

    @Test
    fun capturesAndPromotions() {
        val ep = Position.fromFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2")
        assertEquals("exd6", ep.san(uci("e5d6")))
        val promo = Position.fromFen("4k3/P7/8/8/8/8/8/4K3 w - - 0 1")
        assertEquals("a8=Q+", promo.san(uci("a7a8q")))
        assertEquals("a8=N", promo.san(uci("a7a8n")))
        val promoCap = Position.fromFen("1r2k3/P7/8/8/8/8/8/4K3 w - - 0 1")
        assertEquals("axb8=Q+", promoCap.san(uci("a7b8q")))
    }

    @Test
    fun checkAndMateSuffixes() {
        val backRank = Position.fromFen("6k1/8/8/8/8/8/8/4K2R w - - 0 1")
        assertEquals("Rh8+", backRank.san(uci("h1h8")))
        val mate = Position.fromFen("6k1/2Q5/5K2/8/8/8/8/8 w - - 0 1")
        assertEquals("Qg7#", mate.san(uci("c7g7")))
        val castleCheck = Position.fromFen("5k2/8/8/8/8/8/8/R3K2R w KQ - 0 1")
        assertEquals("O-O+", castleCheck.san(uci("e1g1")))
    }

    @Test
    fun disambiguation() {
        // Two knights, different files: file letter.
        val knights = Position.fromFen("4k3/8/8/8/8/2N5/8/4K1NR w KQ - 0 1")
        assertEquals("Nce2", knights.san(uci("c3e2")))
        assertEquals("Nge2", knights.san(uci("g1e2")))
        // Two rooks on the same file: rank digit.
        val rooks = Position.fromFen("4k3/8/8/R7/8/8/8/R3K3 w Q - 0 1")
        assertEquals("R1a3", rooks.san(uci("a1a3")))
        assertEquals("R5a3", rooks.san(uci("a5a3")))
        // Three queens hitting e5: all three disambiguation forms.
        val queens = Position.fromFen("5k2/8/8/Q5Q1/8/8/8/Q3K3 w - - 0 1")
        assertEquals("Qge5", queens.san(uci("g5e5")))  // file only
        assertEquals("Q1e5", queens.san(uci("a1e5")))  // rank only
        assertEquals("Qa5e5", queens.san(uci("a5e5"))) // file and rank
    }

    @Test
    fun disambiguationCountsLegalMovesOnly() {
        // Both rooks geometrically reach e5, but the one on a5 is pinned
        // to Ka1 along the a-file, so only Rh5 can legally go there: the
        // SAN has no disambiguation.
        val pos = Position.fromFen("r3k3/8/8/R6R/8/8/8/K7 w - - 0 1")
        assertEquals("Re5+", pos.san(uci("h5e5")))
    }

    @Test
    fun parseSan() {
        val pos = Position.fromFen("4k3/8/8/Q5Q1/8/8/8/Q3K3 w - - 0 1")
        assertEquals(uci("a5e5"), pos.parseSan("Qa5e5"))
        assertEquals(uci("g5e5"), pos.parseSan("Qge5"))
        // Tolerates suffixes.
        assertEquals(uci("a5e5"), pos.parseSan("Qa5e5+"))
        assertEquals(uci("a5e5"), pos.parseSan("Qa5e5!"))
        assertEquals(uci("a5e5"), pos.parseSan("Qa5e5?!"))
        assertFailsWith<IllegalArgumentException> { pos.parseSan("Qe5") }
    }

    @Test
    fun parseSanCastling() {
        val pos = Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        assertEquals(uci("e1g1"), pos.parseSan("O-O"))
        assertEquals(uci("e1g1"), pos.parseSan("0-0"))
        assertEquals(uci("e1c1"), pos.parseSan("O-O-O"))
        assertEquals(uci("e1c1"), pos.parseSan("0-0-0"))
        assertEquals(uci("e1c1"), pos.parseSan("O-O-O+"))
    }

    @Test
    fun parseSanRejectsIllegalAndAmbiguous() {
        val pos = Position.initial()
        assertFailsWith<IllegalArgumentException> { pos.parseSan("e5") }
        assertFailsWith<IllegalArgumentException> { pos.parseSan("Kxe2") }
        assertFailsWith<IllegalArgumentException> { pos.parseSan("garbage") }
        val mate = Position.fromFen("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3")
        assertFailsWith<IllegalArgumentException> { mate.parseSan("Ke2") }
    }
}
