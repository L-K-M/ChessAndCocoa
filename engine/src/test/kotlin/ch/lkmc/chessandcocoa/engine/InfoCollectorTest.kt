// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InfoCollectorTest {

    @Test
    fun keepsLatestLinePerMultiPvSlot() {
        val collector = InfoCollector(multiPv = 2)
        collector.accept("info depth 10 seldepth 14 multipv 1 score cp 31 nodes 1000 nps 1 pv e2e4 e7e5")
        collector.accept("info depth 10 seldepth 12 multipv 2 score cp 20 nodes 1000 nps 1 pv d2d4 d7d5")
        collector.accept("info depth 11 seldepth 15 multipv 1 score cp 35 wdl 100 850 50 nodes 2000 nps 1 pv e2e4 c7c5 g1f3")

        val lines = collector.lines()
        assertEquals(listOf("e2e4", "d2d4"), lines.map { it.move })
        assertEquals(Score.Centipawns(35), lines[0].score)
        assertEquals(Wdl(100, 850, 50), lines[0].wdl)
        assertEquals(listOf("e2e4", "c7c5", "g1f3"), lines[0].pv)
        assertEquals(2000, collector.nodes)
    }

    @Test
    fun parsesMateAndBounds() {
        val collector = InfoCollector(multiPv = 1)
        collector.accept("info depth 5 multipv 1 score mate -2 upperbound nodes 10 pv e8d8 h5f7")
        val line = collector.lines().single()
        assertEquals(Score.Mate(-2), line.score)
        assertEquals(ScoreBound.UPPER, line.bound)
    }

    @Test
    fun ignoresStringsAndLinesWithoutPv() {
        val collector = InfoCollector(multiPv = 1)
        collector.accept("info string NNUE evaluation using nn-1a298aa575a0.nnue")
        collector.accept("info depth 1 currmove e2e4 currmovenumber 1")
        assertNull(collector.lines().firstOrNull())
    }
}
