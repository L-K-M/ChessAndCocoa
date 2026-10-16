// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.data

import android.content.Context
import ch.lkmc.chessandcocoa.coach.teach.ExperiencePreferences
import kotlinx.serialization.json.Json

/**
 * Experience preferences, stored apart from games and from any engine or
 * difficulty setting (concept Section 14.3): changing them can never touch
 * chess evidence or the opponent's recorded intentions.
 */
class PreferencesStore(context: Context) {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(): ExperiencePreferences {
        val text = prefs.getString(KEY, null) ?: return ExperiencePreferences()
        return runCatching { JSON.decodeFromString(ExperiencePreferences.serializer(), text) }
            .getOrDefault(ExperiencePreferences())
    }

    fun save(preferences: ExperiencePreferences) {
        prefs.edit().putString(KEY, JSON.encodeToString(ExperiencePreferences.serializer(), preferences)).apply()
    }

    private companion object {
        const val FILE = "experience"
        const val KEY = "preferences"
        val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
