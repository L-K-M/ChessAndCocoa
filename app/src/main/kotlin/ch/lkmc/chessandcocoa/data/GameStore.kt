// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.data

import androidx.core.util.AtomicFile
import ch.lkmc.chessandcocoa.coach.game.GameRecord
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Saves whole games (moves, frozen decisions, plans, events) as one JSON
 * document each, so a resumed game never rebuilds an intention from the
 * board (concept Section 14.3). Writes are atomic: a crash leaves either the
 * old or the new file, never half of one.
 */
class GameStore(private val directory: File) {

    init {
        directory.mkdirs()
    }

    fun save(record: GameRecord) {
        val file = AtomicFile(fileFor(record.gameId))
        val stream = file.startWrite()
        try {
            stream.write(JSON.encodeToString(GameRecord.serializer(), record).toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (failure: Exception) {
            file.failWrite(stream)
            throw failure
        }
    }

    /** The game, or null when it is missing or unreadable (an unreadable file is never half-trusted). */
    fun load(gameId: String): GameRecord? {
        val file = fileFor(gameId)
        if (!file.exists()) return null
        return runCatching {
            JSON.decodeFromString(GameRecord.serializer(), AtomicFile(file).readFully().toString(Charsets.UTF_8))
        }.getOrNull()
    }

    /** The most recently updated game that is still in progress. */
    fun latestUnfinished(): GameRecord? = directory.listFiles { file -> file.name.endsWith(SUFFIX) }
        .orEmpty()
        .sortedByDescending { it.lastModified() }
        .asSequence()
        .mapNotNull { load(it.name.removeSuffix(SUFFIX)) }
        .firstOrNull { it.outcome == null }

    fun delete(gameId: String) {
        AtomicFile(fileFor(gameId)).delete()
    }

    private fun fileFor(gameId: String): File {
        require(gameId.all { it.isLetterOrDigit() || it == '-' }) { "Unsafe game id: $gameId" }
        return File(directory, gameId + SUFFIX)
    }

    companion object {
        private const val SUFFIX = ".json"

        val JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            allowStructuredMapKeys = true
        }
    }
}
