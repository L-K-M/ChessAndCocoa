// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.session

import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.chess.Square
import ch.lkmc.chessandcocoa.coach.analysis.Searcher
import ch.lkmc.chessandcocoa.coach.decide.Difficulty
import ch.lkmc.chessandcocoa.coach.evidence.MarkedSquare
import ch.lkmc.chessandcocoa.coach.evidence.SquareRole
import ch.lkmc.chessandcocoa.coach.game.Coach
import ch.lkmc.chessandcocoa.coach.teach.CommentaryLevel
import ch.lkmc.chessandcocoa.coach.teach.HintLevel
import ch.lkmc.chessandcocoa.coach.teach.TeachingEvent
import ch.lkmc.chessandcocoa.coach.teach.Tone
import ch.lkmc.chessandcocoa.data.GameStore
import ch.lkmc.chessandcocoa.data.PreferencesStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class GameSessionTest {

    private lateinit var directory: File
    private lateinit var scope: CoroutineScope
    private val analyst = FakeAnalyst()

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("games").toFile()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        scope.cancel()
        directory.deleteRecursively()
    }

    @Test
    fun opponentMovesFirstWhenThePlayerTakesBlack() = runBlocking {
        val session = session()
        session.newGame(SideChoice.BLACK, Difficulty.STEADY)
        val state = awaitPhase(session, Phase.HUMAN_TURN)
        assertEquals(Side.BLACK, state.record!!.humanSide)
        assertEquals(1, state.record!!.ply)
    }

    @Test
    fun aMoveIsAssessedAndAnsweredThenSaved() = runBlocking {
        val session = session()
        session.newGame(SideChoice.WHITE, Difficulty.STEADY)
        awaitPhase(session, Phase.HUMAN_TURN)

        session.play(Move.parseUci("e2e4"))
        val state = awaitPly(session, 2)
        assertEquals(Phase.HUMAN_TURN, state.phase)
        assertEquals(1, state.record!!.assessments.size)
        assertEquals(1, state.record!!.decisions.size)

        // Resume in a new session: the same record, decisions included (Section 14.3).
        session.flushSaves()
        val resumed = session()
        resumed.resume()
        val restored = awaitPly(resumed, 2).record!!
        assertEquals(state.record!!.decisions, restored.decisions)
    }

    @Test
    fun aTakebackDiscardsTheOpponentsStaleAnswer() = runBlocking {
        val session = session()
        session.newGame(SideChoice.WHITE, Difficulty.STEADY)
        awaitPhase(session, Phase.HUMAN_TURN)

        // Hold every search so the opponent's answer is still pending at the takeback.
        val gate = CompletableDeferred<Unit>()
        analyst.gate = gate
        session.play(Move.parseUci("e2e4"))
        withTimeout(TIMEOUT) { session.state.first { it.phase == Phase.ASSESSING } }
        session.takeBack()
        gate.complete(Unit)
        analyst.gate = null

        val state = awaitPhase(session, Phase.HUMAN_TURN)
        assertEquals(0, state.record!!.ply)
        Thread.sleep(SETTLE_MILLIS)
        assertEquals(0, session.state.value.record!!.ply, "a stale result was applied")
    }

    @Test
    fun experienceSettingsNeverChangeTheOpponent() = runBlocking {
        val session = session()
        session.newGame(SideChoice.WHITE, Difficulty.GENTLE)
        val before = awaitPhase(session, Phase.HUMAN_TURN).record!!

        session.updatePreferences(session.state.value.preferences.copy(commentary = CommentaryLevel.QUIET, showNumbers = true))
        val after = session.state.value.record!!
        assertEquals(before, after)
        assertEquals(Difficulty.GENTLE, after.difficulty)
    }

    @Test
    fun aHintMarksTheMoveAsAssisted() = runBlocking {
        val session = session()
        session.newGame(SideChoice.WHITE, Difficulty.STEADY)
        awaitPhase(session, Phase.HUMAN_TURN)

        session.hint()
        val hinted = withTimeout(TIMEOUT) { session.state.first { it.hint != null } }
        assertNotNull(hinted.hint)
        session.play(Move.parseUci("d2d4"))
        val state = awaitPly(session, 2)
        assertTrue(state.record!!.assessments.single().assisted)
    }

    @Test
    fun aFailedOpponentMoveIsTriedAgainAfterTheError() = runBlocking {
        val session = session()
        analyst.failures = 1
        session.newGame(SideChoice.BLACK, Difficulty.STEADY)
        withTimeout(TIMEOUT) { session.state.first { it.error == SessionError.ENGINE_UNAVAILABLE } }

        session.dismissError()
        val state = awaitPhase(session, Phase.HUMAN_TURN)
        assertEquals(1, state.record!!.ply)
    }

    @Test
    fun aTakebackKeepsTheHintShownForThatDecision() = runBlocking {
        val session = session()
        session.newGame(SideChoice.WHITE, Difficulty.STEADY)
        awaitPhase(session, Phase.HUMAN_TURN)

        session.hint()
        withTimeout(TIMEOUT) { session.state.first { it.hint != null } }
        session.play(Move.parseUci("d2d4"))
        awaitPly(session, 2)
        session.takeBack()

        // Back at the hinted decision: the help was already seen, so a new move there is still assisted.
        val record = awaitPly(session, 0).record!!
        assertTrue(record.assistedAt(0))
        assertEquals(HintLevel.THREAT, record.hintLevelAt(0))
    }

    @Test
    fun theReviewUsesTheGamesOwnMoveNumbers() = runBlocking {
        // A practice position at move 30; Gentle concedes often against the all-equal fake analyst.
        val session = session(seed = CONCESSION_SEED)
        session.newGame(SideChoice.WHITE, Difficulty.GENTLE, startFen = MOVE_30_FEN)
        repeat(PRACTICE_MOVES) { index ->
            val board = awaitPly(session, index * 2).board!!
            session.play(board.legalMoves.values.flatten().first())
        }
        awaitPly(session, PRACTICE_MOVES * 2)

        val review = session.review()!!
        assertTrue(review.concessions.isNotEmpty(), "no concession to check")
        val numbers = review.concessions.map { Regex("On move (\\d+)").find(it)!!.groupValues[1].toInt() }
        assertTrue(numbers.all { it >= 30 }, "move numbers were $numbers")
    }

    @Test
    fun theMoveListIsNumberedPairs() = runBlocking {
        val session = session()
        session.newGame(SideChoice.WHITE, Difficulty.STEADY)
        awaitPhase(session, Phase.HUMAN_TURN)

        session.play(Move.parseUci("e2e4"))
        val rows = awaitPly(session, 2).moves
        assertEquals(1, rows.size)
        assertEquals(1, rows.single().number)
        assertNotNull(rows.single().black)
        assertEquals("e4", rows.single().white)
    }

    @Test
    fun aGameStartingWithBlackOpensItsFirstRowWithoutWhite() = runBlocking {
        val session = session()
        session.newGame(SideChoice.BLACK, Difficulty.STEADY, startFen = BLACK_TO_MOVE_FEN)
        awaitPhase(session, Phase.HUMAN_TURN)

        session.play(Move.parseUci("g8f6"))
        val rows = withTimeout(TIMEOUT) { session.state.first { it.record?.ply == 2 } }.moves
        assertEquals(MoveRow(3, null, "Nf6"), rows.first())
        assertNotNull(rows[1].white)
        assertEquals(4, rows[1].number)
    }

    @Test
    fun aSavedGameIsOfferedToContinue() = runBlocking {
        val first = session()
        first.newGame(SideChoice.BLACK, Difficulty.KEEN)
        awaitPhase(first, Phase.HUMAN_TURN)
        first.flushSaves()

        val second = session()
        val summary = withTimeout(TIMEOUT) { second.state.first { it.resumable != null } }.resumable!!
        assertEquals(Difficulty.KEEN, summary.difficulty)
        assertEquals(Side.BLACK, summary.humanSide)
        assertTrue(summary.humanToMove)
        assertEquals(1, summary.moveNumber)
        assertEquals(32, summary.board.pieces.size)
    }

    @Test
    fun aFinishedGameIsNotOfferedToContinue() = runBlocking {
        val session = session()
        session.newGame(SideChoice.WHITE, Difficulty.STEADY)
        awaitPhase(session, Phase.HUMAN_TURN)
        assertTrue(session.state.value.canContinue)

        session.resign()
        val state = awaitPhase(session, Phase.GAME_OVER)
        assertEquals(null, state.resumable)
    }

    /** A point with no line to replay is still shown on its own position, never on a later one. */
    @Test
    fun aPointWithoutALineIsShownOnThePositionItExplains() = runBlocking {
        val session = session()
        session.newGame(SideChoice.WHITE, Difficulty.STEADY)
        awaitPhase(session, Phase.HUMAN_TURN)
        session.play(Move.parseUci("e2e4"))
        val replied = awaitPly(session, 2).record!!

        // Read after the reply, a note about 1. e4 shows the board right after 1. e4.
        val note = TeachingEvent(
            id = "note", ply = 1, revision = replied.revision, tone = Tone.CALM, moveLabel = "1. e4",
            brief = "", expanded = listOf(""), marks = listOf(MarkedSquare(Square.parse("e4"), SquareRole.MOVED)),
            evidence = null, claims = emptyList(),
        )
        val frame = session.evidence(note).single()
        assertEquals(BoardSnapshot.of(replied.game.truncate(1), Side.WHITE, playable = false), frame.board)
    }

    private fun session(seed: Long = 1L): GameSession {
        val context = RuntimeEnvironment.getApplication()
        return GameSession(Coach(Searcher(analyst)), GameStore(directory), PreferencesStore(context), scope, seeds = { seed })
    }

    private suspend fun awaitPhase(session: GameSession, phase: Phase): SessionState =
        withTimeout(TIMEOUT) { session.state.first { it.phase == phase } }

    private suspend fun awaitPly(session: GameSession, ply: Int): SessionState =
        withTimeout(TIMEOUT) { session.state.first { it.record?.ply == ply && it.phase == Phase.HUMAN_TURN } }

    private companion object {
        const val TIMEOUT = 20_000L
        const val SETTLE_MILLIS = 300L
        const val CONCESSION_SEED = 7L
        const val PRACTICE_MOVES = 8
        const val MOVE_30_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 30"
        const val BLACK_TO_MOVE_FEN = "r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5Q2/PPPP1PPP/RNB1K1NR b KQkq - 3 3"
    }
}
