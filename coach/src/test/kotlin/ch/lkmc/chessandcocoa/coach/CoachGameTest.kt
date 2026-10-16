// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach

import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.coach.assess.QualityCategory
import ch.lkmc.chessandcocoa.coach.decide.Difficulty
import ch.lkmc.chessandcocoa.coach.evidence.Claim
import ch.lkmc.chessandcocoa.coach.evidence.ClaimVerifier
import ch.lkmc.chessandcocoa.coach.game.Coach
import ch.lkmc.chessandcocoa.coach.game.GameRecord
import ch.lkmc.chessandcocoa.coach.teach.CommentaryLevel
import ch.lkmc.chessandcocoa.coach.teach.ExperiencePreferences
import ch.lkmc.chessandcocoa.coach.teach.HintLevel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A whole game through the coach's public API, as the app's session drives it. */
class CoachGameTest {

    private val preferences = ExperiencePreferences(commentary = CommentaryLevel.DETAILED)
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true; allowStructuredMapKeys = true }

    @Test
    fun thePlayersMateBlunderGetsTheSameAssessmentOnGentleAndKeen() = runBlocking {
        val coach = Coach(TestEngine.searcher())
        val fen = gameOf("e4", "e5", "Bc4", "Nc6", "Qh5").current.fen()
        val steps = listOf(Difficulty.GENTLE, Difficulty.KEEN).map { difficulty ->
            val record = coach.newGame("analyst-$difficulty", Side.BLACK, difficulty, 1, 0, fen)
            coach.playHumanMove(record, record.game.current.parseSan("Nf6"), preferences, 0)
        }

        val assessments = steps.map { it.record.assessments.single() }
        assertEquals(QualityCategory.BLUNDER, assessments.first().quality.category)
        assertEquals(assessments.first().quality, assessments.last().quality)
        assertTrue(steps.all { step -> step.event?.claims?.any { it is Claim.AllowsMate } == true })
    }

    @Test
    fun aGameKeepsItsHistoryHonestThroughHintsTakebacksAndSaves() = runBlocking {
        val coach = Coach(TestEngine.searcher())
        val random = Random(11)
        var record = coach.newGame("game-1", Side.BLACK, Difficulty.STEADY, seed = 5, nowMillis = 0)
        val frozen = mutableMapOf<Int, ch.lkmc.chessandcocoa.coach.decide.DecisionRecord>()

        while (record.outcome == null && record.ply < PLIES) {
            if (!record.isHumanTurn) {
                record = coach.playComputerMove(record, preferences, 0).record
                frozen[record.decisions.last().ply] = record.decisions.last()
                continue
            }
            if (record.ply == HINT_PLY) record = coach.hint(record, HintLevel.THREAT, 0).first
            // A weak, seeded learner: random legal moves, so mistakes and plans both show up.
            val moves = record.game.current.legalMoves()
            val before = record.revision
            val step = coach.playHumanMove(record, moves[random.nextInt(moves.size)], preferences, 0)
            record = step.record
            assertEquals(before + 1, record.revision)

            // Every displayed claim replays from its own state.
            step.event?.let { event -> assertEquals(event.claims, ClaimVerifier.verified(event.claims, record.game)) }
        }

        // Temporal integrity (R2): decisions recorded earlier were never edited later.
        record.decisions.forEach { assertEquals(frozen.getValue(it.ply), it) }
        assertEquals(record.decisions.size, record.moves.indices.count { it % 2 == 0 })
        assertTrue(record.assessments.any { it.assisted })

        // Save and resume: the same record, intentions included.
        val restored = json.decodeFromString(GameRecord.serializer(), json.encodeToString(GameRecord.serializer(), record))
        assertEquals(record, restored)

        // Takeback: back to the previous human decision, the line kept as an abandoned branch.
        val takenBack = coach.takeBack(record, 0)
        assertTrue(takenBack.ply < record.ply)
        assertTrue(takenBack.isHumanTurn || takenBack.outcome != null)
        assertEquals(record.abandoned.size + 1, takenBack.abandoned.size)
        assertTrue(takenBack.decisions.all { it.ply < takenBack.ply })
        assertTrue(takenBack.episodes.all { episode -> episode.revisions.all { it.ply < takenBack.ply } })

        val review = coach.review(record)
        assertEquals(record.assessments.size, review.humanMoves)
    }

    private companion object {
        const val PLIES = 24
        const val HINT_PLY = 3
    }
}
