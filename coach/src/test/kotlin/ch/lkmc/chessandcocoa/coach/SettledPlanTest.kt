// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach

import ch.lkmc.chessandcocoa.chess.PieceType
import ch.lkmc.chessandcocoa.chess.Position
import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.chess.Square
import ch.lkmc.chessandcocoa.coach.analysis.AnalystBudgets
import ch.lkmc.chessandcocoa.coach.decide.DecisionRecord
import ch.lkmc.chessandcocoa.coach.decide.Difficulty
import ch.lkmc.chessandcocoa.coach.decide.PlanRole
import ch.lkmc.chessandcocoa.coach.decide.SelectionBasis
import ch.lkmc.chessandcocoa.coach.evidence.ClaimVerifier
import ch.lkmc.chessandcocoa.coach.evidence.SquareRole
import ch.lkmc.chessandcocoa.coach.game.Coach
import ch.lkmc.chessandcocoa.coach.game.GameRecord
import ch.lkmc.chessandcocoa.coach.plan.PlanAction
import ch.lkmc.chessandcocoa.coach.plan.PlanAssessment
import ch.lkmc.chessandcocoa.coach.plan.PlanFamily
import ch.lkmc.chessandcocoa.coach.plan.PlanInstance
import ch.lkmc.chessandcocoa.coach.plan.PlanRevision
import ch.lkmc.chessandcocoa.coach.plan.PlanStatus
import ch.lkmc.chessandcocoa.coach.plan.PlanTarget
import ch.lkmc.chessandcocoa.coach.plan.Subgoal
import ch.lkmc.chessandcocoa.coach.plan.TransitionReason
import ch.lkmc.chessandcocoa.coach.render.Renderer
import ch.lkmc.chessandcocoa.coach.teach.HintLevel
import ch.lkmc.chessandcocoa.engine.EngineProfile
import ch.lkmc.chessandcocoa.engine.Score
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The opponent's own move can settle its plan, for example by taking the
 * target. Hints must then describe the board as it is, not the pressure
 * that came before, and the opponent's idea says what its move did.
 * Reported from a Gentle game: after 14... hxg3 took the pawn, the hint
 * still said the pawn on g3 was under pressure.
 */
class SettledPlanTest {

    private val g3 = Square.parse("g3")

    @Test
    fun aHintDoesNotPointAtATargetTheOpponentAlreadyTook() = runBlocking {
        val record = afterStrike()

        val (_, hint) = Coach(TestEngine.searcher()).hint(record, HintLevel.THREAT, 0)

        assertFalse(hint.marks.any { it.square == g3 && it.role == SquareRole.TARGET }, hint.marks.toString())
        assertFalse("g3" in hint.text, hint.text)
    }

    @Test
    fun theOpponentsIdeaSaysItTookThePawnRatherThanStillAimingAtIt() {
        val record = afterStrike()
        val decision = strikeDecision(record)

        assertTrue(ClaimVerifier.verifyIntent(decision, record.game))
        assertEquals("I chose this move to take your pawn on g3, as my plan intended.", Renderer.intent(decision))
    }

    /** White to move after 14... hxg3, with Black's plan against the g3 pawn still recorded as active. */
    private fun afterStrike(): GameRecord {
        val game = gameOf(*REPORTED_GAME)
        val adoptedPly = game.ply - 3
        val strikePly = game.ply - 1
        val target = PlanTarget(g3, game.pieceIdAt(g3, strikePly), PieceType.PAWN)
        val plan = PlanInstance(
            episodeId = "p$adoptedPly-win_weak_unit-G3",
            family = PlanFamily.WIN_WEAK_UNIT,
            templateVersion = 1,
            owner = Side.BLACK,
            target = target,
            startedPly = adoptedPly,
            startBalance = 0,
            revisions = listOf(
                PlanRevision(1, adoptedPly, PlanStatus.PROPOSED, TransitionReason.PROPOSED, g3, emptyList()),
                PlanRevision(2, adoptedPly, PlanStatus.ACTIVE, TransitionReason.ADOPTED, g3, emptyList(), PlanAction(game.moves[adoptedPly], Subgoal.ADD_ATTACKER)),
                PlanRevision(3, strikePly, PlanStatus.ACTIVE, TransitionReason.CONTINUED, g3, emptyList(), PlanAction(game.moves[strikePly], Subgoal.STRIKE)),
            ),
        )
        return GameRecord(
            gameId = "settled",
            branchId = "b0",
            revision = 0,
            humanSide = Side.WHITE,
            difficulty = Difficulty.GENTLE,
            seed = 1,
            initialFen = Position.START_FEN,
            moves = game.moves,
            episodes = listOf(plan),
            createdAtMillis = 0,
            updatedAtMillis = 0,
        )
    }

    /** The frozen decision that played 14... hxg3 as the plan's strike. */
    private fun strikeDecision(record: GameRecord): DecisionRecord {
        val game = record.game
        val ply = game.ply - 1
        val before = game.positions[ply]
        val move = game.moves[ply]
        val plan = record.episodes.single()
        val even = Score.Centipawns(0)
        return DecisionRecord(
            id = "d-b0-$ply",
            gameId = record.gameId,
            branchId = record.branchId,
            revision = record.revision,
            ply = ply,
            side = Side.BLACK,
            fen = before.fen(),
            history = game.uciMoves().take(ply),
            move = move,
            san = before.san(move),
            selectionBasis = SelectionBasis.PLAN,
            planRole = PlanRole.DECISIVE,
            plan = plan,
            planAssessment = PlanAssessment(plan.current.action!!, before.san(move), even, even, null, emptyList(), holds = true, selectionMethod = "test"),
            candidates = emptyList(),
            withoutPlanMove = move,
            difficulty = record.difficulty,
            engineName = "test",
            engineProfile = EngineProfile(),
            budgets = AnalystBudgets(),
            searches = 0,
            nodes = 0,
        )
    }

    private companion object {
        val REPORTED_GAME = arrayOf(
            "b4", "d6", "a4", "Na6", "b5", "Nb8", "c4", "e5", "Nc3", "Be7", "Ne4", "f5", "Ng3", "f4",
            "Ne4", "Nf6", "f3", "Nxe4", "fxe4", "Be6", "d3", "Bf6", "g3", "h5", "Ba3", "h4", "e3", "hxg3",
        )
    }
}
