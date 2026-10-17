// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach

import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.coach.decide.Difficulty
import ch.lkmc.chessandcocoa.coach.decide.Obligation
import ch.lkmc.chessandcocoa.coach.decide.PlanRole
import ch.lkmc.chessandcocoa.coach.decide.SelectionBasis
import ch.lkmc.chessandcocoa.coach.evidence.Claim
import ch.lkmc.chessandcocoa.coach.evidence.ClaimVerifier
import ch.lkmc.chessandcocoa.coach.game.Coach
import ch.lkmc.chessandcocoa.coach.game.GameRecord
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
            val decision = recorded.copy(plan = null, planAssessment = null, planRole = PlanRole.NONE, selectionBasis = basis)
            assertEquals("I have no longer plan to explain for this move.", Renderer.intent(decision))
        }
    }

    private suspend fun opening(coach: Coach): GameRecord {
        val start = coach.newGame("intent", Side.BLACK, Difficulty.STEADY, 1, 0, OpenFileTemplateTest.ROUTE_FEN)
        return coach.playComputerMove(start, preferences, 0).record
    }
}
