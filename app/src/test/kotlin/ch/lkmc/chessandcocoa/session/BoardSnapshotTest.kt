// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.session

import ch.lkmc.chessandcocoa.chess.Game
import ch.lkmc.chessandcocoa.chess.Position
import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.chess.Square
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What the board shows when the player taps a piece to see how it is protected. */
class BoardSnapshotTest {

    @Test
    fun aPieceIsDefendedByItsOwnSidesPiecesCheapestFirst() {
        val game = Game.new().let { it.play(it.current.parseSan("Nf3")) }

        val board = BoardSnapshot.of(game, Side.WHITE, playable = true)

        assertEquals(listOf("e2", "g2").map(Square::parse), board.defenders[Square.parse("f3")])
        // Knights, then the bishop, the queen, and the king last.
        assertEquals(listOf("b1", "f3", "c1", "d1", "e1").map(Square::parse), board.defenders[Square.parse("d2")])
    }

    @Test
    fun aPinnedPieceDoesNotDefendOffItsPin() {
        // The bishop on b4 pins the knight on d2 to the king, so only the pawn on d3 defends e4.
        val position = Position.fromFen("4k3/8/8/8/1b2P3/3P4/3N4/4K3 w - - 0 1")

        val board = BoardSnapshot.of(position, Side.WHITE, lastMove = null, playable = true)

        assertEquals(listOf(Square.parse("d3")), board.defenders[Square.parse("e4")])
    }

    @Test
    fun aBoardThatTakesNoMovesShowsNoProtection() {
        val board = BoardSnapshot.of(Game.new(), Side.WHITE, playable = false)

        assertTrue(board.defenders.isEmpty())
    }
}
