// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach

import ch.lkmc.chessandcocoa.chess.Game
import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.chess.PieceType
import ch.lkmc.chessandcocoa.chess.Square
import ch.lkmc.chessandcocoa.coach.evidence.Claim
import ch.lkmc.chessandcocoa.coach.evidence.ClaimBuilder
import ch.lkmc.chessandcocoa.coach.evidence.ClaimVerifier
import ch.lkmc.chessandcocoa.coach.evidence.Continuation
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecaptureEvidenceTest {

    @Test
    fun bothBranchesMustReplayFromTheirOwnPositions() {
        val parent = Game.new(PAWN_FEN)
        val defense = ClaimBuilder.continuation(parent, listOf("f2f3", "d5e4", "f3e4").map(Move::parseUci))
        val claim = exposed(parent).copy(defense = defense)
        val game = parent.play(parent.move("a3"))
        assertTrue(ClaimVerifier.verify(claim, game))

        assertFalse(ClaimVerifier.verify(claim.copy(defense = defense.copy(startFen = game.current.fen())), game))
        assertFalse(ClaimVerifier.verify(claim.copy(defense = defense.copy(sans = defense.sans.dropLast(1))), game))
        assertFalse(ClaimVerifier.verify(claim.copy(continuation = claim.continuation.copy(sans = emptyList())), game))
    }

    @Test
    fun aLegalLineWithoutARecaptureDoesNotProveTheDefense() {
        val parent = Game.new(PAWN_FEN)
        val quietLine = ClaimBuilder.continuation(parent, listOf("f2f3", "d5e4", "f3f4").map(Move::parseUci))
        assertFalse(ClaimVerifier.verify(exposed(parent).copy(defense = quietLine), parent.play(parent.move("a3"))))
    }

    @Test
    fun aPinnedPawnCannotBeOfferedAsARecapturer() {
        // Moving f3-e4 would expose the king on f1 to the rook on f8.
        val parent = Game.new("5rk1/pp4pp/8/3p4/4P3/8/PP3PPP/5K2 w - - 0 1")
        val illegalDefense = Continuation(0, parent.current.fen(), listOf("f2f3", "d5e4", "f3e4").map(Move::parseUci), listOf("f3", "dxe4", "fxe4"))
        val game = parent.play(parent.move("a3"))
        assertTrue(ClaimVerifier.verify(exposed(parent), game))
        assertFalse(ClaimVerifier.verify(exposed(parent).copy(defense = illegalDefense), game))
    }

    @Test
    fun olderClaimsWithoutAComparisonStillDecode() {
        val original = exposed(Game.new(PAWN_FEN)).copy(defense = null)
        val json = Json { classDiscriminator = "type" }
        val encoded = json.encodeToString(Claim.serializer(), original)
        assertTrue("defense" !in encoded)
        assertNull((json.decodeFromString(Claim.serializer(), encoded) as Claim.ExposesPiece).defense)
    }

    private fun exposed(parent: Game): Claim.ExposesPiece {
        val capture = Move.parseUci("d5e4")
        val after = parent.play(parent.move("a3"))
        return Claim.ExposesPiece(
            piece = PieceType.PAWN,
            square = Square.parse("e4"),
            undefended = true,
            captureSan = "dxe4",
            marks = emptyList(),
            continuation = ClaimBuilder.continuation(after, listOf(capture)),
            alreadyExposed = true,
        )
    }

    private companion object {
        const val PAWN_FEN = "6k1/pp3ppp/8/3p4/4P3/8/PP3PPP/6K1 w - - 0 1"
    }
}
