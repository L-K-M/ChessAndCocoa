// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach

import ch.lkmc.chessandcocoa.chess.PieceType
import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.chess.Square
import ch.lkmc.chessandcocoa.coach.decide.ConsiderationOutcome
import ch.lkmc.chessandcocoa.coach.decide.Difficulty
import ch.lkmc.chessandcocoa.coach.decide.Obligation
import ch.lkmc.chessandcocoa.coach.decide.PlanConsideration
import ch.lkmc.chessandcocoa.coach.decide.PlanRole
import ch.lkmc.chessandcocoa.coach.decide.SelectionBasis
import ch.lkmc.chessandcocoa.coach.evidence.Claim
import ch.lkmc.chessandcocoa.coach.evidence.ClaimVerifier
import ch.lkmc.chessandcocoa.coach.game.Coach
import ch.lkmc.chessandcocoa.coach.game.GameRecord
import ch.lkmc.chessandcocoa.coach.plan.PlanAction
import ch.lkmc.chessandcocoa.coach.plan.PlanFamily
import ch.lkmc.chessandcocoa.coach.plan.Subgoal
import ch.lkmc.chessandcocoa.coach.render.Renderer
import ch.lkmc.chessandcocoa.coach.teach.DisclosureSetting
import ch.lkmc.chessandcocoa.coach.teach.ExperiencePreferences
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpponentIntentTest {
    private val preferences = ExperiencePreferences(disclosure = DisclosureSetting.WITH_MOVE)

    @Test
    fun theRecordedAimIsRevealedWithoutChangingTheDecisionOrSearchingAgain() = runBlocking {
        val searcher = TestEngine.searcher()
        val coach = Coach(searcher)
        val record = opening(coach)
        val decision = assertNotNull(record.lastDecision)
        val searches = searcher.searchCount

        val (revealed, intent) = coach.revealOpponentIntent(record, 1)
        val shown = assertNotNull(intent)
        assertEquals(decision.id, shown.decisionId)
        assertEquals("1. Rc1", shown.moveLabel)
        assertTrue("open file" in shown.text && "b7" in shown.text, shown.text)
        assertEquals(record.decisions, revealed.decisions)
        assertEquals(record.episodes, revealed.episodes)
        assertEquals(record.moves, revealed.moves)
        assertEquals(searches, searcher.searchCount)
        assertTrue(revealed.assistedAt(revealed.ply))
        assertNull(revealed.hintLevelAt(revealed.ply), "intent does not consume a hint level")
        assertTrue(decision.plan!!.episodeId in revealed.revealedEpisodes)
        assertEquals(record.revision + 1, revealed.revision)
        assertEquals(revealed, coach.revealOpponentIntent(revealed, 2).first, "showing it again is idempotent")

        val human = coach.playHumanMove(revealed, revealed.game.current.parseSan("b6"), preferences, 3)
        assertTrue(human.record.assessments.single().assisted)
        assertTrue(assertNotNull(human.event).claims.any { it is Claim.PlanRevealed })
        assertEquals(1, coach.review(human.record).assistedMoves)

        val json = Json { encodeDefaults = true; allowStructuredMapKeys = true }
        val restored = json.decodeFromString(GameRecord.serializer(), json.encodeToString(GameRecord.serializer(), human.record))
        val takenBack = coach.takeBack(restored, 4)
        assertEquals(revealed.ply, takenBack.ply)
        assertTrue(takenBack.assistedAt(takenBack.ply))

        // Older saves have no disclosure history; they still decode as unassisted.
        val old = JsonObject(json.encodeToJsonElement(GameRecord.serializer(), record).jsonObject.filterKeys { it != "intentReveals" })
        assertTrue(json.decodeFromJsonElement(GameRecord.serializer(), old).intentReveals.isEmpty())
    }

    @Test
    fun aConcurrentIdeaIsNotDescribedAsTheReasonForTheMove() = runBlocking {
        val decision = assertNotNull(opening(Coach(TestEngine.searcher())).lastDecision)
        val concurrent = Renderer.intent(decision.copy(planRole = PlanRole.CONCURRENT))
        val causal = Renderer.intent(decision.copy(planRole = PlanRole.DECISIVE))
        assertTrue(concurrent.startsWith("This move also fits my plan"), concurrent)
        assertTrue(causal.startsWith("I chose this move to"), causal)
    }

    @Test
    fun aMoveThatTakesThePlansTargetSaysSoInsteadOfStillAimingAtIt() = runBlocking {
        val record = opening(Coach(TestEngine.searcher()))
        val decision = assertNotNull(record.lastDecision)
        val plan = assertNotNull(decision.plan)
        val strike = plan.copy(revisions = plan.revisions.dropLast(1) + plan.current.copy(action = PlanAction(decision.move, Subgoal.STRIKE)))

        val open = Renderer.intent(decision.copy(plan = strike))
        val weak = Renderer.intent(decision.copy(plan = strike.copy(family = PlanFamily.WIN_WEAK_UNIT, target = strike.target.copy(pieceType = PieceType.PAWN))))

        assertEquals("I chose this move to take your pawn on b7, as my plan intended.", open)
        assertEquals("I chose this move to take your pawn on b7, as my plan intended.", weak)
        assertTrue(Renderer.intent(decision.copy(plan = strike, planRole = PlanRole.CONCURRENT)).startsWith("This move takes your pawn on b7"))
        assertFalse(ClaimVerifier.verifyIntent(decision.copy(plan = strike), record.game), "a quiet rook move takes nothing")
    }

    @Test
    fun mismatchedPositionsAndUnsupportedPlansAreNotRevealed() = runBlocking {
        val coach = Coach(TestEngine.searcher())
        val record = opening(coach)
        val decision = assertNotNull(record.lastDecision)
        assertFalse(ClaimVerifier.verifyIntent(decision.copy(fen = record.game.current.fen()), record.game))
        assertFalse(ClaimVerifier.verifyIntent(decision.copy(history = listOf("a2a3")), record.game))
        assertFalse(ClaimVerifier.verifyIntent(decision.copy(planAssessment = null), record.game))
        assertFalse(ClaimVerifier.verifyIntent(decision.copy(obligations = listOf(Obligation.FORCED_MOVE)), record.game))
        assertNull(coach.revealOpponentIntent(record.copy(decisions = emptyList()), 0).second)
        assertNull(coach.revealOpponentIntent(coach.resign(record, 0), 0).second)
    }

    @Test
    fun movesWithoutAPlanDoNotAcquireAnInventedStrategicReason() = runBlocking {
        val recorded = assertNotNull(opening(Coach(TestEngine.searcher())).lastDecision)
        for (basis in listOf(SelectionBasis.FALLBACK, SelectionBasis.DIFFICULTY_CONCESSION)) {
            val decision = recorded.copy(plan = null, planAssessment = null, planRole = PlanRole.NONE, selectionBasis = basis, considered = emptyList())
            assertEquals("None of my plan ideas fit this position, so this move is not part of a longer plan.", Renderer.intent(decision))
        }
    }

    @Test
    fun aPlanTestedAndTurnedDownIsNamedWithItsReasonButNotAsTheMovesPurpose() = runBlocking {
        val coach = Coach(TestEngine.searcher())
        val record = opening(coach)
        val recorded = assertNotNull(record.lastDecision)
        val b7 = Square.parse("b7")
        val turnedDown = PlanConsideration("p-weak-b7", PlanFamily.WIN_WEAK_UNIT, b7, ConsiderationOutcome.REFUTED)

        // A deliberate concession reads exactly like the best move, so the reply gets no extra clue.
        for (basis in listOf(SelectionBasis.FALLBACK, SelectionBasis.DIFFICULTY_CONCESSION)) {
            val decision = recorded.copy(plan = null, planAssessment = null, planRole = PlanRole.NONE, selectionBasis = basis, considered = listOf(turnedDown))
            val (revealed, intent) = coach.revealOpponentIntent(record.copy(decisions = record.decisions.dropLast(1) + decision), 1)
            assertEquals(
                "I looked at a plan to win your pawn on b7, but you would have had a good answer to it. This move is not part of a longer plan.",
                assertNotNull(intent).text,
            )
            assertTrue(turnedDown.episodeId in revealed.revealedEpisodes, "later news about a plan the player has seen may be narrated")
        }

        // A plan named by a square that no longer holds its target is left out.
        val stale = recorded.copy(plan = null, planAssessment = null, planRole = PlanRole.NONE, selectionBasis = SelectionBasis.FALLBACK,
            considered = listOf(turnedDown.copy(target = Square.parse("a5"), outcome = ConsiderationOutcome.TOO_COSTLY)))
        val (_, intent) = coach.revealOpponentIntent(record.copy(decisions = record.decisions.dropLast(1) + stale), 1)
        assertEquals("None of my plan ideas fit this position, so this move is not part of a longer plan.", assertNotNull(intent).text)
    }

    @Test
    fun beingInCheckIsSaidTheSameWayWhateverTheMoveChosen() = runBlocking {
        val recorded = assertNotNull(opening(Coach(TestEngine.searcher())).lastDecision)
        for (basis in listOf(SelectionBasis.IMMEDIATE_OBLIGATION, SelectionBasis.DIFFICULTY_CONCESSION)) {
            val decision = recorded.copy(plan = null, planAssessment = null, planRole = PlanRole.NONE, selectionBasis = basis,
                obligations = listOf(Obligation.IN_CHECK), considered = emptyList())
            assertEquals("I need to get my king out of check.", Renderer.intent(decision))
        }
    }

    private suspend fun opening(coach: Coach): GameRecord {
        val start = coach.newGame("intent", Side.BLACK, Difficulty.STEADY, 1, 0, OpenFileTemplateTest.ROUTE_FEN)
        return coach.playComputerMove(start, preferences, 0).record
    }
}
