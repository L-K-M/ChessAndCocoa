// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach

import ch.lkmc.chessandcocoa.chess.Game
import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.coach.analysis.Analyst
import ch.lkmc.chessandcocoa.coach.analysis.AnalystBudgets
import ch.lkmc.chessandcocoa.coach.analysis.Searcher
import ch.lkmc.chessandcocoa.coach.decide.DecisionInput
import ch.lkmc.chessandcocoa.coach.decide.DecisionRecord
import ch.lkmc.chessandcocoa.coach.decide.Difficulty
import ch.lkmc.chessandcocoa.coach.decide.MoveSelector
import ch.lkmc.chessandcocoa.coach.decide.PlanRole
import ch.lkmc.chessandcocoa.coach.decide.PlansSwitch
import ch.lkmc.chessandcocoa.coach.decide.SelectionBasis
import ch.lkmc.chessandcocoa.engine.AnalysisRequest
import ch.lkmc.chessandcocoa.engine.AnalysisResult
import ch.lkmc.chessandcocoa.engine.CandidateLine
import ch.lkmc.chessandcocoa.engine.Score
import ch.lkmc.chessandcocoa.engine.ScoreBound
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Controlled scores distinguish selection mistakes from changes in engine evaluation. */
class GentleDifficultyTest {
    @Test
    fun gentleCanOverlookAnAttackedKnightWhileStrongerProfilesDefendIt() = runBlocking {
        val game = Game.new("6k1/pp3ppp/2p5/3N4/8/8/PP3PPP/6K1 w - - 0 1")
        val lines = listOf(
            "d5c3" to Score.Centipawns(0),
            "a2a3" to Score.Centipawns(-300),
            "b2b3" to Score.Centipawns(-600),
            "h2h3" to Score.Mate(-1),
        )
        val gentle = decisions(game, Difficulty.GENTLE, lines)
        val conceded = gentle.filter { it.selectionBasis == SelectionBasis.DIFFICULTY_CONCESSION }

        assertTrue(conceded.size >= MIN_CONCESSIONS, "Gentle made ${conceded.size} concessions in $SEEDS seeded choices")
        assertTrue(conceded.all { it.move.uci == "a2a3" })
        assertTrue(gentle.any { it.move.uci == "d5c3" }, "Gentle should still sometimes defend")
        assertTrue(gentle.none { it.move.uci == "b2b3" }, "concessions remain bounded")
        assertTrue(gentle.none { it.move.uci == "h2h3" }, "a known mating loss is never a deliberate concession")
        conceded.forEach { record ->
            assertNull(record.plan)
            assertEquals(PlanRole.NONE, record.planRole)
            assertFalse(record.planIsReason)
        }

        for (difficulty in listOf(Difficulty.STEADY, Difficulty.KEEN)) {
            assertTrue(decisions(game, difficulty, lines).all { it.move.uci == "d5c3" })
        }
    }

    @Test
    fun gentleCanChooseAWeakerLegalReplyToCheck() = runBlocking {
        val game = Game.new("4r1k1/8/8/8/8/8/P6P/4K3 w - - 0 1")
        assertTrue(game.current.isCheck())
        val lines = listOf("e1d2" to Score.Centipawns(0), "e1f2" to Score.Centipawns(-200))
        val decisions = decisions(game, Difficulty.GENTLE, lines)

        assertTrue(decisions.count { it.move.uci == "e1f2" } >= MIN_CONCESSIONS)
        assertTrue(decisions.all { game.current.isLegal(it.move) })
    }

    @Test
    fun gentleStillTakesAnImmediateMateItHasFound() = runBlocking {
        val game = gameOf("e4", "e5", "Bc4", "Nc6", "Qh5", "Nf6")
        val lines = listOf("h5f7" to Score.Mate(1), "h5f3" to Score.Centipawns(0))
        assertTrue(decisions(game, Difficulty.GENTLE, lines).all { it.move.uci == "h5f7" })
    }

    private suspend fun decisions(game: Game, difficulty: Difficulty, lines: List<Pair<String, Score>>): List<DecisionRecord> {
        lines.forEach { assertTrue(game.current.isLegal(Move.parseUci(it.first))) }
        val selector = MoveSelector(Searcher(FixedAnalyst(lines)), AnalystBudgets())
        return (1L..SEEDS).map { seed ->
            selector.decide(DecisionInput(game, "difficulty", "b0", 0, difficulty, emptyList(), seed, PlansSwitch.DISABLED)).record
        }
    }

    private class FixedAnalyst(private val lines: List<Pair<String, Score>>) : Analyst {
        override val searchCount = 0
        override val nodeCount = 0L

        override suspend fun analyze(request: AnalysisRequest): AnalysisResult = AnalysisResult(
            request = request,
            lines = lines.mapIndexed { index, (move, score) ->
                CandidateLine(index + 1, move, score, ScoreBound.EXACT, depth = 8, nodes = 1, pv = listOf(move))
            },
            bestMove = lines.first().first,
            nodes = 1,
            engineName = "Fixed scores",
            complete = true,
        )
    }

    private companion object {
        const val SEEDS = 100L
        const val MIN_CONCESSIONS = 40
    }
}
