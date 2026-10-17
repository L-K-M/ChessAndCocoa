// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.ui

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import ch.lkmc.chessandcocoa.chess.PieceType
import ch.lkmc.chessandcocoa.chess.Position
import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.chess.Square
import ch.lkmc.chessandcocoa.coach.analysis.EngineAnalyst
import ch.lkmc.chessandcocoa.coach.analysis.Searcher
import ch.lkmc.chessandcocoa.coach.decide.Difficulty
import ch.lkmc.chessandcocoa.coach.game.Coach
import ch.lkmc.chessandcocoa.coach.render.Renderer
import ch.lkmc.chessandcocoa.coach.teach.ExperiencePreferences
import ch.lkmc.chessandcocoa.coach.teach.GameReview
import ch.lkmc.chessandcocoa.data.GameStore
import ch.lkmc.chessandcocoa.data.PreferencesStore
import ch.lkmc.chessandcocoa.engine.ProcessUciTransport
import ch.lkmc.chessandcocoa.engine.UciEngine
import ch.lkmc.chessandcocoa.session.BoardSnapshot
import ch.lkmc.chessandcocoa.session.GameSession
import ch.lkmc.chessandcocoa.session.GameSummary
import ch.lkmc.chessandcocoa.session.Phase
import ch.lkmc.chessandcocoa.session.SideChoice
import ch.lkmc.chessandcocoa.ui.about.AboutScreen
import ch.lkmc.chessandcocoa.ui.game.GameNavigation
import ch.lkmc.chessandcocoa.ui.game.GameScreen
import ch.lkmc.chessandcocoa.ui.home.HomeActions
import ch.lkmc.chessandcocoa.ui.home.HomeScreen
import ch.lkmc.chessandcocoa.ui.review.ReviewContext
import ch.lkmc.chessandcocoa.ui.review.ReviewScreen
import ch.lkmc.chessandcocoa.ui.settings.SettingsScreen
import ch.lkmc.chessandcocoa.ui.theme.Appearance
import ch.lkmc.chessandcocoa.ui.theme.CocoaTheme
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Renders each screen on the JVM and saves it to docs/screenshots/ for
 * review, in both themes, on a compact phone, with large text and in
 * landscape. The game screens drive real games against the host Stockfish,
 * through the same accessibility labels TalkBack reads, and skip without it.
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
        show { HomeScreen(resumable = savedGame(), engineStarting = false, actions = homeActions()) }
        compose.onRoot().captureRoboImage(shot("home"))
    }

    @Test
    fun homeInTheEvening() {
        show(Appearance.EVENING) { HomeScreen(resumable = savedGame(), engineStarting = false, actions = homeActions()) }
        compose.onRoot().captureRoboImage(shot("home-evening"))
    }

    @Test
    fun homeOnFirstLaunch() {
        show { HomeScreen(resumable = null, engineStarting = true, actions = homeActions()) }
        compose.onRoot().captureRoboImage(shot("home-first-launch"))
    }

    @Test
    fun newGameSheet() {
        show { HomeScreen(resumable = savedGame(), engineStarting = false, actions = homeActions()) }
        compose.onNodeWithText("New game").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Start game").assertExists()
        captureScreenRoboImage(shot("new-game"))
    }

    @Test
    fun settings() {
        show { SettingsScreen(ExperiencePreferences(), onChange = {}, onBack = {}) }
        compose.onRoot().captureRoboImage(shot("settings"))
    }

    @Test
    fun settingsInTheEvening() {
        show(Appearance.EVENING) { SettingsScreen(ExperiencePreferences(), onChange = {}, onBack = {}) }
        compose.onRoot().captureRoboImage(shot("settings-evening"))
    }

    @Test
    fun about() {
        show { AboutScreen(onOpen = {}, onBack = {}) }
        compose.onRoot().captureRoboImage(shot("about"))
    }

    @Test
    @Config(qualifiers = "w360dp-h400dp-xxhdpi")
    fun settingsMakeRoomForTheNavigationBarOnce() {
        showOverNavigationBar { SettingsScreen(ExperiencePreferences(), onChange = {}, onBack = {}) }
        assertScrolledEndClearsNavigationBarOnce()
    }

    @Test
    @Config(qualifiers = "w360dp-h400dp-xxhdpi")
    fun aboutMakesRoomForTheNavigationBarOnce() {
        showOverNavigationBar { AboutScreen(onOpen = {}, onBack = {}) }
        assertScrolledEndClearsNavigationBarOnce()
    }

    /**
     * The teaching flow on concept Section 13.2 (after 3.Qh5, the player has
     * Black): a hint, the blunder and its explanation, "Show me why" in and
     * out, the review, a takeback, and resuming the saved game.
     */
    @Test
    fun aBlunderIsExplainedOnTheBoard() {
        val store = Files.createTempDirectory("games").toFile()
        val session = engineSession(store)
        session.newGame(SideChoice.BLACK, Difficulty.STEADY, startFen = SCHOLAR_FEN)
        val review = mutableStateOf<GameReview?>(null)
        show {
            val shown = review.value
            if (shown == null) GameScreen(session, navigation())
            else ReviewScreen(shown, ReviewContext(Side.BLACK, Difficulty.STEADY, onNewGame = {}, onBack = {}))
        }
        compose.waitUntil(TIMEOUT) { session.state.value.phase == Phase.HUMAN_TURN }

        // A hint first: it marks the decision as assisted.
        compose.onNodeWithText("Hint").performClick()
        compose.waitUntil(TIMEOUT) { session.state.value.hint != null && session.state.value.phase == Phase.HUMAN_TURN }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(shot("hint"))

        compose.onNodeWithContentDescription("g8, black knight").performClick()
        compose.onNodeWithContentDescription("f6, empty").performClick()
        compose.waitUntil(TIMEOUT) { session.state.value.phase == Phase.GAME_OVER }
        compose.waitForIdle()

        val record = session.state.value.record!!
        assertTrue(record.events.any { it.brief.startsWith("That allows checkmate: Qxf7#") })
        compose.onRoot().captureRoboImage(shot("game-over"))

        // Hiding the feedback leaves a "Show" toggle, not a second "Show me why".
        compose.onAllNodesWithText("Hide").onFirst().performClick()
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithText("Show me why").fetchSemanticsNodes().isEmpty())
        compose.onAllNodesWithText("Show").onFirst().performClick()
        compose.waitForIdle()

        // "Show me why" replays the mate on the board itself, clearly marked, leaving the game untouched.
        compose.onAllNodesWithText("Show me why").onFirst().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Exploring here does not change your game.").assertExists()
        compose.onRoot().captureRoboImage(shot("evidence"))
        compose.onNodeWithText("Next step").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("f7, white queen").assertExists()
        compose.onRoot().captureRoboImage(shot("evidence-step"))
        compose.onNodeWithText("Back to game").performClick()
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithText("Exploring here does not change your game.").fetchSemanticsNodes().isEmpty())
        assertEquals(record, session.state.value.record, "exploring changed the game")

        review.value = session.review()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(shot("review-finished"))
        review.value = null
        compose.waitForIdle()

        // An explicit takeback returns to the decision; the game is saved and resumes as it was.
        compose.onNodeWithText("Take back").performClick()
        compose.waitUntil(TIMEOUT) { session.state.value.phase == Phase.HUMAN_TURN }
        val restored = session.state.value.record!!
        assertEquals(0, restored.ply)
        assertTrue(restored.assistedAt(0))

        runBlocking { session.flushSaves() }
        val resumed = engineSession(store)
        compose.waitUntil(TIMEOUT) { resumed.state.value.canContinue }
        resumed.resume()
        compose.waitUntil(TIMEOUT) { resumed.state.value.phase == Phase.HUMAN_TURN }
        val reloaded = resumed.state.value.record!!
        assertEquals(restored.gameId, reloaded.gameId)
        assertEquals(restored.branchId, reloaded.branchId)
        assertEquals(restored.revision, reloaded.revision)
        assertEquals(restored.hints, reloaded.hints)
    }

    @Test
    fun aGameInProgressWithFeedback() {
        val session = engineSession()
        session.newGame(SideChoice.WHITE, Difficulty.STEADY)
        val review = mutableStateOf<GameReview?>(null)
        show {
            val shown = review.value
            if (shown == null) GameScreen(session, navigation())
            else ReviewScreen(shown, ReviewContext(Side.WHITE, Difficulty.STEADY, onNewGame = {}, onBack = {}))
        }

        // A short scripted line ending in a queen grab that draws a comment.
        for ((from, to) in listOf("e2" to "e4", "f1" to "c4", "d1" to "f3", "f3" to "f6")) {
            compose.waitUntil(TIMEOUT) { session.state.value.phase == Phase.HUMAN_TURN }
            val board = session.state.value.board!!
            val fromSquare = Square.parse(from)
            if (board.legalMoves[fromSquare]?.any { it.to.name == to } != true) break
            compose.onNodeWithContentDescription(describe(board, fromSquare)).performClick()
            compose.onNodeWithContentDescription(describe(board, Square.parse(to))).performClick()
        }
        compose.waitUntil(TIMEOUT) { session.state.value.phase == Phase.HUMAN_TURN }
        compose.waitForIdle()
        assertTrue(session.state.value.record!!.ply >= 4, "moves were not played: ${session.state.value.moves}")
        compose.onRoot().captureRoboImage(shot("game"))

        review.value = session.review()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(shot("review"))
    }

    @Test
    fun theGameInTheEvening() {
        scholarWithKnightSelected(Appearance.EVENING)
        compose.onRoot().captureRoboImage(shot("game-evening"))
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun aCompactPhoneKeepsTheBoardWhole() {
        scholarWithKnightSelected()
        assertBoardClearOfToolbar()
        compose.onRoot().captureRoboImage(shot("game-compact"))
    }

    @Test
    fun largeTextStillLeavesRoomForTheBoard() {
        scholarWithKnightSelected(fontScale = LARGE_FONT)
        assertBoardClearOfToolbar()
        compose.onRoot().captureRoboImage(shot("game-large-text"))
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun theLargestTextOnACompactPhoneStillPlays() {
        scholarWithKnightSelected(fontScale = LARGEST_FONT)
        assertBoardClearOfToolbar()
        compose.onRoot().captureRoboImage(shot("game-compact-largest-text"))
    }

    @Test
    @Config(qualifiers = "w300dp-h340dp-xxhdpi")
    fun aVeryShortWindowNeverHidesTheBoardUnderTheToolbar() {
        scholarWithKnightSelected()
        assertBoardClearOfToolbar()
    }

    @Test
    fun theHighContrastBoardInTheEvening() {
        scholarWithKnightSelected(Appearance.EVENING, ExperiencePreferences(highContrastBoard = true))
        compose.onRoot().captureRoboImage(shot("game-high-contrast-evening"))
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun landscapeKeepsTheBoardAndPanelSideBySide() {
        scholarWithKnightSelected()
        compose.onRoot().captureRoboImage(shot("landscape"))
    }

    @Test
    fun aPawnPromotesThroughTheChooser() {
        val session = engineSession()
        session.newGame(SideChoice.WHITE, Difficulty.STEADY, startFen = PROMOTION_FEN)
        show { GameScreen(session, navigation()) }
        compose.waitUntil(TIMEOUT) { session.state.value.phase == Phase.HUMAN_TURN }

        compose.onNodeWithContentDescription("e7, white pawn").performClick()
        compose.onNodeWithContentDescription("e8, empty").performClick()
        compose.waitForIdle()
        captureScreenRoboImage(shot("promotion"))

        // Switch Access and Voice Access activate through the semantic click action, not a touch.
        compose.onNodeWithContentDescription("white queen").assert(hasClickAction())
        compose.onNodeWithContentDescription("white queen").performClick()
        compose.waitUntil(TIMEOUT) { (session.state.value.record?.ply ?: 0) >= 1 }
        val promoted = session.state.value.record!!.moves.first()
        assertEquals(PieceType.QUEEN, promoted.promotion)
    }

    private fun scholarWithKnightSelected(
        appearance: Appearance = Appearance.DAYLIGHT,
        preferences: ExperiencePreferences = ExperiencePreferences(),
        fontScale: Float = 1f,
    ) {
        val session = engineSession()
        session.updatePreferences(preferences)
        session.newGame(SideChoice.BLACK, Difficulty.STEADY, startFen = SCHOLAR_FEN)
        show(appearance, fontScale) { GameScreen(session, navigation()) }
        compose.waitUntil(TIMEOUT) { session.state.value.phase == Phase.HUMAN_TURN }
        compose.onNodeWithContentDescription("g8, black knight").performClick()
        compose.waitForIdle()
    }

    /** The bottom rank (Black's back rank here) sits fully above the toolbar and inside the screen. */
    private fun assertBoardClearOfToolbar() {
        val root = compose.onRoot().getBoundsInRoot()
        val corner = compose.onNodeWithContentDescription("a8, black rook").getBoundsInRoot()
        val toolbar = compose.onNodeWithText("Hint").getBoundsInRoot()
        assertTrue(corner.bottom <= toolbar.top, "the board's bottom edge ${corner.bottom} runs under the toolbar at ${toolbar.top}")
        assertTrue(corner.right <= root.right, "the board's right edge ${corner.right} leaves the screen at ${root.right}")
    }

    /** Shows [content] edge to edge, as the app does, with a button navigation bar along the bottom. */
    private fun showOverNavigationBar(content: @Composable () -> Unit) {
        lateinit var view: View
        show {
            view = LocalView.current
            content()
        }
        val bar = with(compose.density) { NAVIGATION_BAR.roundToPx() }
        val insets = WindowInsetsCompat.Builder().setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, bar)).build()
        compose.runOnIdle { ViewCompat.dispatchApplyWindowInsets(view, insets) }
        compose.waitForIdle()
    }

    /** Scrolled to the end, the last line sits just above the navigation bar, not a second bar's height higher. */
    private fun assertScrolledEndClearsNavigationBarOnce() {
        val scroller = compose.onNode(hasScrollAction(), useUnmergedTree = true)
        scroller.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, SCROLL_TO_END) }
        compose.waitForIdle()

        val bar = with(compose.density) { NAVIGATION_BAR.toPx() }
        val barTop = compose.onRoot().getBoundsInRoot().bottom.let { with(compose.density) { it.toPx() } } - bar
        val contentBottom = scroller.fetchSemanticsNode().lowestDescendantBottom()
        assertTrue(barTop - contentBottom < bar, "the content ends ${barTop - contentBottom}px above the navigation bar, a second bar's height")
    }

    private fun SemanticsNode.lowestDescendantBottom(): Float =
        children.maxOfOrNull { maxOf(it.boundsInRoot.bottom, it.lowestDescendantBottom()) } ?: boundsInRoot.bottom

    private fun show(appearance: Appearance = Appearance.DAYLIGHT, fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                CocoaTheme(appearance) { content() }
            }
        }
    }

    private fun savedGame(): GameSummary {
        val position = Position.fromFen(SAVED_FEN)
        return GameSummary(
            difficulty = Difficulty.STEADY,
            humanSide = Side.WHITE,
            moveNumber = position.fullmoveNumber,
            humanToMove = true,
            board = BoardSnapshot.of(position, Side.WHITE, lastMove = null, playable = false),
        )
    }

    private fun homeActions() = HomeActions({}, { _, _ -> }, {}, {})

    private fun describe(board: BoardSnapshot, square: Square): String {
        val piece = board.pieces[square] ?: return "${square.name}, empty"
        val side = if (piece.side == Side.WHITE) "white" else "black"
        return "${square.name}, $side ${Renderer.name(piece.type)}"
    }

    private fun engineSession(store: File = Files.createTempDirectory("games").toFile()): GameSession {
        val binary = File(System.getProperty("stockfish.path") ?: "")
        assumeTrue("host Stockfish not built", binary.canExecute())
        val engine = UciEngine(ProcessUciTransport(binary)).apply { start() }
        val context = RuntimeEnvironment.getApplication()
        return GameSession(
            coach = Coach(Searcher(EngineAnalyst(engine))),
            store = GameStore(store),
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
        const val LARGE_FONT = 1.5f
        const val LARGEST_FONT = 2f
        const val SCROLL_TO_END = 100_000f
        val NAVIGATION_BAR = 48.dp
        const val SCHOLAR_FEN = "r1bqkbnr/pppp1ppp/2n5/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 3 3"
        const val SAVED_FEN = "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/3P1N2/PPP2PPP/RNBQK2R w KQkq - 1 5"
        const val PROMOTION_FEN = "8/4P3/8/8/8/8/k7/4K3 w - - 0 1"
    }
}
