// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa

import android.app.Application
import ch.lkmc.chessandcocoa.coach.analysis.Searcher
import ch.lkmc.chessandcocoa.coach.game.Coach
import ch.lkmc.chessandcocoa.data.GameStore
import ch.lkmc.chessandcocoa.data.PreferencesStore
import ch.lkmc.chessandcocoa.nativeengine.EngineHost
import ch.lkmc.chessandcocoa.session.GameSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * Composition root. The engine, coach and session live as long as the
 * process: the in-process engine can start only once, and a game keeps
 * thinking across rotations and screen changes.
 */
class ChessCocoaApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val engine = EngineHost(scope)

    val session = GameSession(
        coach = Coach(Searcher(engine.analyst)),
        store = GameStore(File(app.filesDir, GAMES_DIR)),
        preferencesStore = PreferencesStore(app),
        scope = scope,
    )

    private companion object {
        const val GAMES_DIR = "games"
    }
}
