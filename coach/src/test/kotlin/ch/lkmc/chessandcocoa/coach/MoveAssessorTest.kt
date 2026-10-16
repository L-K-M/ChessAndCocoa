// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach

import ch.lkmc.chessandcocoa.chess.Game
import ch.lkmc.chessandcocoa.coach.analysis.AnalystBudgets
import ch.lkmc.chessandcocoa.coach.assess.MoveAssessor
import ch.lkmc.chessandcocoa.coach.assess.QualityCategory
import ch.lkmc.chessandcocoa.coach.decide.Difficulty
import ch.lkmc.chessandcocoa.coach.evidence.Claim
import ch.lkmc.chessandcocoa.coach.evidence.ClaimBuilder
import ch.lkmc.chessandcocoa.coach.evidence.ClaimVerifier
import ch.lkmc.chessandcocoa.coach.render.Renderer
import ch.lkmc.chessandcocoa.engine.Score
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class MoveAssessorTest {

    @Test
    fun aMuchFasterForcedLossIsNotCalledBest() {
        // Mated in 1 where the best defense lasts 8 moves: not "as strong as any move".
        assertNotEquals(QualityCategory.BEST, MoveAssessor.categorize(Score.Mate(-8), Score.Mate(-1)))
        assertEquals(QualityCategory.BEST, MoveAssessor.categorize(Score.Mate(-8), Score.Mate(-7)))
    }

    @Test
    fun aPieceThatWasAlreadyHangingIsNotBlamedOnTheMove() = runBlocking {
        // The knight on d5 is already attacked by the c6 pawn and undefended; a3 ignores it.
        val parent = Game.new("6k1/pp3ppp/2p5/3N4/8/8/PP3PPP/6K1 w - - 0 1")
        val move = parent.current.parseSan("a3")
        val assessment = MoveAssessor(TestEngine.searcher(), AnalystBudgets())
            .assess(parent, move, prior = null, priorPlan = null, planWindowCp = Difficulty.STEADY.planWindowCp, assisted = false)

        val claims = ClaimVerifier.verified(ClaimBuilder.forHumanMove(parent, assessment, null, null, emptyList()), parent.play(move))
        val exposed = claims.filterIsInstance<Claim.ExposesPiece>().single()
        assertEquals("Your knight on d5 was already in danger, and this move does not save it. I can capture it next: cxd5.", Renderer.render(exposed))
    }
}
