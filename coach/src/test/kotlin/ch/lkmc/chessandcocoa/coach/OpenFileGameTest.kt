// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach

import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.coach.decide.Difficulty
import ch.lkmc.chessandcocoa.coach.evidence.Claim
import ch.lkmc.chessandcocoa.coach.evidence.ClaimVerifier
import ch.lkmc.chessandcocoa.coach.game.Coach
import ch.lkmc.chessandcocoa.coach.game.GameRecord
import ch.lkmc.chessandcocoa.coach.plan.PlanFamily
import ch.lkmc.chessandcocoa.coach.teach.CommentaryLevel
import ch.lkmc.chessandcocoa.coach.teach.ExperiencePreferences
import ch.lkmc.chessandcocoa.coach.teach.HintLevel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The new family uses the same frozen records, hints, feedback and saves as existing plans. */
class OpenFileGameTest {
    @Test
    fun thePlanIsRecordedBeforeTheReplyAndRevealedAfterIt() = runBlocking {
        val coach = Coach(TestEngine.searcher())
        val preferences = ExperiencePreferences(commentary = CommentaryLevel.DETAILED)
        val start = coach.newGame("open-file", Side.BLACK, Difficulty.STEADY, 1, 0, OpenFileTemplateTest.ROUTE_FEN)
        val computer = coach.playComputerMove(start, preferences, 0)
        val recorded = computer.record
        val decision = assertNotNull(recorded.lastDecision)

        assertEquals(PlanFamily.INVADE_OPEN_FILE, assertNotNull(decision.plan).family)
        assertEquals("Rc1", decision.san)
        assertTrue(assertNotNull(decision.planAssessment).holds)
        assertNull(computer.event, "the opponent's plan stays hidden before the player's choice")

        val (_, hint) = coach.hint(recorded, HintLevel.THREAT, 0)
        assertTrue("open files" in hint.text)

        val human = coach.playHumanMove(recorded, recorded.game.current.parseSan("b6"), preferences, 0)
        assertEquals(decision, human.record.decisions.single())
        val event = assertNotNull(human.event)
        assertTrue(event.claims.any { it is Claim.PlanRevealed && it.family == PlanFamily.INVADE_OPEN_FILE })
        assertTrue((event.expanded + event.brief).any { "open file" in it })
        assertEquals(event.claims, ClaimVerifier.verified(event.claims, human.record.game))

        val json = Json { encodeDefaults = true; allowStructuredMapKeys = true }
        val restored = json.decodeFromString(GameRecord.serializer(), json.encodeToString(GameRecord.serializer(), human.record))
        assertEquals(human.record, restored)
        assertEquals(recorded.episodes, coach.takeBack(restored, 0).episodes)
        assertTrue(coach.review(restored).episodes.isNotEmpty())
    }
}
