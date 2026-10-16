// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.session

import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.coach.analysis.Searcher
import ch.lkmc.chessandcocoa.coach.decide.Difficulty
import ch.lkmc.chessandcocoa.coach.game.Coach
import ch.lkmc.chessandcocoa.coach.teach.CommentaryLevel
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

    private fun session(): GameSession {
        val context = RuntimeEnvironment.getApplication()
        return GameSession(Coach(Searcher(analyst)), GameStore(directory), PreferencesStore(context), scope)
    }

    private suspend fun awaitPhase(session: GameSession, phase: Phase): SessionState =
        withTimeout(TIMEOUT) { session.state.first { it.phase == phase } }

    private suspend fun awaitPly(session: GameSession, ply: Int): SessionState =
        withTimeout(TIMEOUT) { session.state.first { it.record?.ply == ply && it.phase == Phase.HUMAN_TURN } }

    private companion object {
        const val TIMEOUT = 20_000L
        const val SETTLE_MILLIS = 300L
    }
}
