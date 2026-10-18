// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.data

import android.content.Context
import androidx.core.content.edit
import ch.lkmc.chessandcocoa.coach.teach.BoardSide
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PreferencesStoreTest {

    @Test
    fun preferencesSavedBeforeTheBoardSideSettingLoadAsLeft() {
        val context = RuntimeEnvironment.getApplication()
        // JSON exactly as a version without the boardSide field wrote it.
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit {
            putString(
                KEY,
                "{\"commentary\":\"NORMAL\",\"disclosure\":\"AFTER_CHOICE\"," +
                    "\"showNumbers\":false,\"showCoordinates\":true,\"highContrastBoard\":false,\"schemaVersion\":1}",
            )
        }

        assertEquals(BoardSide.LEFT, PreferencesStore(context).load().boardSide)
    }

    @Test
    fun theBoardSideSurvivesASaveAndLoad() {
        val context = RuntimeEnvironment.getApplication()
        val store = PreferencesStore(context)

        store.save(store.load().copy(boardSide = BoardSide.RIGHT))

        assertEquals(BoardSide.RIGHT, store.load().boardSide)
    }

    private companion object {
        // The same file and key PreferencesStore reads; writing them directly is the point.
        const val FILE = "experience"
        const val KEY = "preferences"
    }
}
