// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import ch.lkmc.chessandcocoa.coach.analysis.EngineAnalyst
import ch.lkmc.chessandcocoa.coach.analysis.Searcher
import ch.lkmc.chessandcocoa.coach.decide.Difficulty
import ch.lkmc.chessandcocoa.coach.game.Coach
import ch.lkmc.chessandcocoa.coach.teach.ExperiencePreferences
import ch.lkmc.chessandcocoa.coach.teach.GameReview
import ch.lkmc.chessandcocoa.data.GameStore
import ch.lkmc.chessandcocoa.data.PreferencesStore
import ch.lkmc.chessandcocoa.engine.ProcessUciTransport
import ch.lkmc.chessandcocoa.engine.UciEngine
import ch.lkmc.chessandcocoa.session.GameSession
import ch.lkmc.chessandcocoa.session.Phase
import ch.lkmc.chessandcocoa.session.SideChoice
import ch.lkmc.chessandcocoa.ui.about.AboutScreen
import ch.lkmc.chessandcocoa.ui.game.GameNavigation
import ch.lkmc.chessandcocoa.ui.game.GameScreen
import ch.lkmc.chessandcocoa.ui.home.HomeActions
import ch.lkmc.chessandcocoa.ui.home.HomeScreen
import ch.lkmc.chessandcocoa.ui.review.ReviewScreen
import ch.lkmc.chessandcocoa.ui.settings.SettingsScreen
import ch.lkmc.chessandcocoa.ui.theme.CocoaTheme
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.file.Files
import kotlin.test.assertTrue

/**
 * Renders each screen on the JVM and saves it to docs/screenshots/ for
 * review. The game screens drive a real game against the host Stockfish and
 * skip without it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class ScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val screenshots = File(System.getProperty("screenshots.dir") ?: "build/screenshots")

    @Test
    fun home() {
        compose.setContent {
            CocoaTheme { HomeScreen(canContinue = true, engineStarting = false, actions = HomeActions({}, { _, _ -> }, {}, {})) }
        }
        compose.onRoot().captureRoboImage(shot("home"))
    }

    @Test
    fun settings() {
        compose.setContent { CocoaTheme { SettingsScreen(ExperiencePreferences(), onChange = {}, onBack = {}) } }
        compose.onRoot().captureRoboImage(shot("settings"))
    }

    @Test
    fun about() {
        compose.setContent { CocoaTheme { AboutScreen(onOpen = {}, onBack = {}) } }
        compose.onRoot().captureRoboImage(shot("about"))
    }

    @Test
    fun aBlunderIsExplainedOnTheBoard() {
        // Concept Section 13.2 position after 3.Qh5, the player has Black.
        val session = engineSession()
        session.newGame(SideChoice.BLACK, Difficulty.STEADY, startFen = SCHOLAR_FEN)
        compose.setContent { CocoaTheme { GameScreen(session, navigation()) } }
        compose.waitUntil(TIMEOUT) { session.state.value.phase == Phase.HUMAN_TURN }

        compose.onNodeWithContentDescription("g8, black knight").performClick()
        compose.onNodeWithContentDescription("f6, empty").performClick()
        compose.waitUntil(TIMEOUT) { session.state.value.phase == Phase.GAME_OVER }
        compose.waitForIdle()

        val record = session.state.value.record!!
        assertTrue(record.events.any { it.brief.startsWith("That allows checkmate: Qxf7#") })
        compose.onRoot().captureRoboImage(shot("game-over"))

        // "Show me why" replays the mate on its own board, leaving the game untouched.
        compose.onAllNodesWithText("Show me why").onFirst().performClick()
        compose.waitForIdle()
        captureScreenRoboImage(shot("evidence"))
    }

    @Test
    fun aGameInProgressWithFeedback() {
        val session = engineSession()
        session.newGame(SideChoice.WHITE, Difficulty.STEADY)
        val review = mutableStateOf<GameReview?>(null)
        compose.setContent {
            CocoaTheme {
                val shown = review.value
                if (shown == null) GameScreen(session, navigation()) else ReviewScreen(shown, onBack = {})
            }
        }

        // A short scripted line ending in a queen grab that draws a comment.
        for ((from, to) in listOf("e2" to "e4", "f1" to "c4", "d1" to "f3", "f3" to "f6")) {
            compose.waitUntil(TIMEOUT) { session.state.value.phase == Phase.HUMAN_TURN }
            val board = session.state.value.board!!
            val fromSquare = ch.lkmc.chessandcocoa.chess.Square.parse(from)
            if (board.legalMoves[fromSquare]?.any { it.to.name == to } != true) break
            compose.onNodeWithContentDescription(describe(board, fromSquare)).performClick()
            compose.onNodeWithContentDescription(describe(board, ch.lkmc.chessandcocoa.chess.Square.parse(to))).performClick()
        }
        compose.waitUntil(TIMEOUT) { session.state.value.phase == Phase.HUMAN_TURN }
        compose.waitForIdle()
        assertTrue(session.state.value.record!!.ply >= 4, "moves were not played: ${session.state.value.notation}")
        compose.onRoot().captureRoboImage(shot("game"))

        review.value = session.review()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(shot("review"))
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun landscapeKeepsTheBoardAndPanelSideBySide() {
        val session = engineSession()
        session.newGame(SideChoice.BLACK, Difficulty.STEADY, startFen = SCHOLAR_FEN)
        compose.setContent { CocoaTheme { GameScreen(session, navigation()) } }
        compose.waitUntil(TIMEOUT) { session.state.value.phase == Phase.HUMAN_TURN }
        compose.onNodeWithContentDescription("g8, black knight").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(shot("landscape"))
    }

    private fun describe(board: ch.lkmc.chessandcocoa.session.BoardSnapshot, square: ch.lkmc.chessandcocoa.chess.Square): String {
        val piece = board.pieces[square] ?: return "${square.name}, empty"
        val side = if (piece.side == ch.lkmc.chessandcocoa.chess.Side.WHITE) "white" else "black"
        return "${square.name}, $side ${ch.lkmc.chessandcocoa.coach.render.Renderer.name(piece.type)}"
    }

    private fun engineSession(): GameSession {
        val binary = File(System.getProperty("stockfish.path") ?: "")
        assumeTrue("host Stockfish not built", binary.canExecute())
        val engine = UciEngine(ProcessUciTransport(binary)).apply { start() }
        val context = RuntimeEnvironment.getApplication()
        return GameSession(
            coach = Coach(Searcher(EngineAnalyst(engine))),
            store = GameStore(Files.createTempDirectory("games").toFile()),
            preferencesStore = PreferencesStore(context),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            // A fixed seed makes the opponent's choices, and so the screenshots, repeatable.
            seeds = { SEED },
        )
    }

    private fun navigation() = GameNavigation(onBack = {}, onNewGame = {}, onReview = {}, onSettings = {})

    private fun shot(name: String): String = File(screenshots, "$name.png").path

    private companion object {
        const val TIMEOUT = 60_000L
        const val SEED = 3L
        const val SCHOLAR_FEN = "r1bqkbnr/pppp1ppp/2n5/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 3 3"
    }
}
