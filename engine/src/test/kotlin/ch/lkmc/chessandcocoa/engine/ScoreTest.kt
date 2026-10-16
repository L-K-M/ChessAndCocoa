// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScoreTest {

    @Test
    fun rankOrdersMatesAroundCentipawns() {
        val ordered = listOf(
            Score.Mate(0),
            Score.Mate(-1),
            Score.Mate(-5),
            Score.Centipawns(-900),
            Score.Centipawns(0),
            Score.Centipawns(900),
            Score.Mate(5),
            Score.Mate(1),
        )
        assertEquals(ordered, ordered.shuffled(kotlin.random.Random(7)).sortedBy { it.rank() })
    }

    @Test
    fun negateFlipsPerspective() {
        assertEquals(Score.Centipawns(-40), Score.Centipawns(40).negate())
        assertEquals(Score.Mate(-3), Score.Mate(3).negate())
        assertTrue(Score.Mate(2).rank() > Score.Mate(2).negate().rank())
    }
}
