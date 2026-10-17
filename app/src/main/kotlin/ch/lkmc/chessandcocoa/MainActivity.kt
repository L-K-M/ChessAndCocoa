// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.lkmc.chessandcocoa.coach.decide.Difficulty
import ch.lkmc.chessandcocoa.nativeengine.EngineStatus
import ch.lkmc.chessandcocoa.ui.about.AboutScreen
import ch.lkmc.chessandcocoa.ui.about.LegalDocument
import ch.lkmc.chessandcocoa.ui.about.LegalTextScreen
import ch.lkmc.chessandcocoa.ui.game.GameNavigation
import ch.lkmc.chessandcocoa.ui.game.GameScreen
import ch.lkmc.chessandcocoa.ui.home.HomeActions
import ch.lkmc.chessandcocoa.ui.home.HomeScreen
import ch.lkmc.chessandcocoa.ui.home.NewGameSheet
import ch.lkmc.chessandcocoa.ui.review.ReviewContext
import ch.lkmc.chessandcocoa.ui.review.ReviewScreen
import ch.lkmc.chessandcocoa.ui.settings.SettingsScreen
import ch.lkmc.chessandcocoa.ui.theme.CocoaTheme

/** Top-level destinations; a handful of screens needs no navigation library. */
enum class Screen { HOME, GAME, REVIEW, SETTINGS, ABOUT, LICENSE, NOTICES }

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as ChessCocoaApp).container
        setContent {
            CocoaTheme { App(container) }
        }
    }
}

@Composable
private fun App(container: AppContainer) {
    val session = container.session
    val state by session.state.collectAsStateWithLifecycle()
    val engine by container.engine.status.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var previous by rememberSaveable { mutableStateOf(Screen.HOME) }
    var choosingGame by rememberSaveable { mutableStateOf(false) }

    fun go(next: Screen) {
        previous = screen
        screen = next
    }

    BackHandler(enabled = screen != Screen.HOME) {
        screen = when (screen) {
            Screen.LICENSE, Screen.NOTICES -> Screen.ABOUT
            Screen.REVIEW, Screen.SETTINGS -> if (previous == Screen.GAME) Screen.GAME else Screen.HOME
            else -> Screen.HOME
        }
    }

    when (screen) {
        Screen.HOME -> HomeScreen(
            resumable = state.resumable,
            engineStarting = engine == EngineStatus.STARTING,
            lastDifficulty = state.record?.difficulty ?: Difficulty.GENTLE,
            actions = HomeActions(
                onContinue = {
                    if (state.record?.outcome == null && state.record != null) go(Screen.GAME) else { session.resume(); go(Screen.GAME) }
                },
                onNewGame = { side, difficulty -> session.newGame(side, difficulty); go(Screen.GAME) },
                onSettings = { go(Screen.SETTINGS) },
                onAbout = { go(Screen.ABOUT) },
            ),
        )
        Screen.GAME -> GameScreen(
            session,
            GameNavigation(
                onBack = { screen = Screen.HOME },
                onNewGame = { choosingGame = true },
                onReview = { go(Screen.REVIEW) },
                onSettings = { go(Screen.SETTINGS) },
            ),
        )
        Screen.REVIEW -> {
            // Rebuilding the review replays its claims, so do it once per record.
            val record = state.record
            val review = remember(record) { session.review() }
            if (record != null && review != null) {
                ReviewScreen(review, ReviewContext(record.humanSide, record.difficulty, onNewGame = { choosingGame = true }, onBack = { screen = Screen.GAME }))
            }
        }
        Screen.SETTINGS -> SettingsScreen(state.preferences, session::updatePreferences, onBack = { screen = if (previous == Screen.GAME) Screen.GAME else Screen.HOME })
        Screen.ABOUT -> AboutScreen(onOpen = { go(if (it == LegalDocument.GPL) Screen.LICENSE else Screen.NOTICES) }, onBack = { screen = Screen.HOME })
        Screen.LICENSE -> LegalTextScreen(LegalDocument.GPL, onBack = { screen = Screen.ABOUT })
        Screen.NOTICES -> LegalTextScreen(LegalDocument.NOTICES, onBack = { screen = Screen.ABOUT })
    }

    if (choosingGame) {
        NewGameSheet(
            initialDifficulty = state.record?.difficulty ?: Difficulty.GENTLE,
            onStart = { side, difficulty -> choosingGame = false; session.newGame(side, difficulty); screen = Screen.GAME },
            onDismiss = { choosingGame = false },
        )
    }
}
