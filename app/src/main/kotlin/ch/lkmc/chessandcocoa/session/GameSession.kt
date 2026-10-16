// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.session

import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.coach.decide.Difficulty
import ch.lkmc.chessandcocoa.coach.evidence.Continuation
import ch.lkmc.chessandcocoa.coach.game.Coach
import ch.lkmc.chessandcocoa.coach.game.GameRecord
import ch.lkmc.chessandcocoa.coach.teach.ExperiencePreferences
import ch.lkmc.chessandcocoa.coach.teach.GameReview
import ch.lkmc.chessandcocoa.coach.teach.Hint
import ch.lkmc.chessandcocoa.coach.teach.HintLevel
import ch.lkmc.chessandcocoa.coach.teach.TeachingEvent
import ch.lkmc.chessandcocoa.data.GameStore
import ch.lkmc.chessandcocoa.data.PreferencesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class Phase { NO_GAME, HUMAN_TURN, HINTING, ASSESSING, OPPONENT_THINKING, GAME_OVER }

enum class SideChoice { WHITE, BLACK, RANDOM }

/** What the UI shows; one immutable snapshot at a time. */
data class SessionState(
    val phase: Phase = Phase.NO_GAME,
    val record: GameRecord? = null,
    val board: BoardSnapshot? = null,
    /** The player's move, shown while it is being assessed. */
    val pendingMove: Move? = null,
    val event: TeachingEvent? = null,
    val hint: Hint? = null,
    val preferences: ExperiencePreferences = ExperiencePreferences(),
    val canContinue: Boolean = false,
    val error: SessionError? = null,
)

enum class SessionError { ENGINE_UNAVAILABLE, SAVE_FAILED }

/**
 * The game session controller (concept Section 5): the only layer the UI
 * talks to. It owns the live [GameRecord], runs coach work off the main
 * thread, persists after every step, and discards results computed for a
 * revision that is no longer current (R11).
 *
 * ```
 *   UI ──intents──▶ GameSession ──▶ Coach (analysis, plans, teaching)
 *    ▲                   │     └──▶ GameStore / PreferencesStore
 *    └──── state ────────┘
 * ```
 */
class GameSession(
    private val coach: Coach,
    private val store: GameStore,
    private val preferencesStore: PreferencesStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val seeds: () -> Long = { UUID.randomUUID().mostSignificantBits },
) {

    private val _state = MutableStateFlow(SessionState(preferences = preferencesStore.load()))
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private var work: Job? = null
    private var warmup: Job? = null

    init {
        scope.launch(Dispatchers.IO) {
            val saved = store.latestUnfinished()
            _state.update { it.copy(canContinue = saved != null) }
        }
    }

    fun newGame(side: SideChoice, difficulty: Difficulty) {
        cancelWork()
        val seed = seeds()
        val humanSide = when (side) {
            SideChoice.WHITE -> Side.WHITE
            SideChoice.BLACK -> Side.BLACK
            SideChoice.RANDOM -> if (seed and 1L == 0L) Side.WHITE else Side.BLACK
        }
        val record = coach.newGame(UUID.randomUUID().toString(), humanSide, difficulty, seed, clock())
        commit(record, event = null, hint = null)
        continueGame(record)
    }

    /** Resumes the most recent unfinished game, with its recorded plans intact. */
    fun resume() {
        cancelWork()
        scope.launch {
            val saved = withContext(Dispatchers.IO) { store.latestUnfinished() } ?: return@launch
            commit(saved, event = saved.events.lastOrNull(), hint = null)
            continueGame(saved)
        }
    }

    fun play(move: Move) {
        val current = _state.value
        val record = current.record ?: return
        if (current.phase != Phase.HUMAN_TURN) return
        if (current.board?.legalMoves?.get(move.from)?.contains(move) != true) return

        _state.update { it.copy(phase = Phase.ASSESSING, pendingMove = move, hint = null, board = pendingBoard(record, move)) }
        work = scope.launch {
            val result = runCatching { coach.playHumanMove(record, move, current.preferences, clock()) }
                .getOrElse { failure -> return@launch fail(failure) }
            if (!stillCurrent(record)) return@launch
            commit(result.record, result.event, hint = null)
            continueGame(result.record)
        }
    }

    /** The next, more revealing hint level (Section 4.2). */
    fun hint() {
        val current = _state.value
        val record = current.record ?: return
        if (current.phase != Phase.HUMAN_TURN) return
        val level = when (record.hintLevelAt(record.ply)) {
            null -> HintLevel.THREAT
            HintLevel.THREAT -> HintLevel.IDEA
            HintLevel.IDEA, HintLevel.MOVE -> HintLevel.MOVE
        }
        // The board waits while the hint loads, so a move cannot race the "assisted" mark.
        _state.update { it.copy(phase = Phase.HINTING, board = BoardSnapshot.of(record.game, record.humanSide, playable = false)) }
        work = scope.launch {
            val (next, hint) = runCatching { coach.hint(record, level, clock()) }
                .getOrElse { failure -> return@launch fail(failure) }
            if (!stillCurrent(record)) return@launch
            commit(next, current.event, hint)
        }
    }

    /** Explicit takeback to the player's previous decision; never automatic (Section 4.4). */
    fun takeBack() {
        val record = _state.value.record ?: return
        cancelWork()
        val restored = coach.takeBack(record, clock())
        if (restored === record) return
        commit(restored, event = null, hint = null)
        continueGame(restored)
    }

    fun resign() {
        val record = _state.value.record ?: return
        cancelWork()
        commit(coach.resign(record, clock()), event = null, hint = null)
    }

    fun review(): GameReview? = _state.value.record?.let(coach::review)

    fun evidence(continuation: Continuation): List<EvidenceFrame> {
        val orientation = _state.value.record?.humanSide ?: Side.WHITE
        return continuation.frames(orientation, lastMove = null)
    }

    fun updatePreferences(preferences: ExperiencePreferences) {
        preferencesStore.save(preferences)
        _state.update { it.copy(preferences = preferences) }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    /** Starts whatever the position calls for: the opponent's move, or analysis while the player thinks. */
    private fun continueGame(record: GameRecord) {
        if (record.outcome != null) return
        if (record.isHumanTurn) {
            warmup = scope.launch { runCatching { coach.prepareHumanTurn(record) } }
            return
        }
        _state.update { it.copy(phase = Phase.OPPONENT_THINKING) }
        work = scope.launch {
            val preferences = _state.value.preferences
            val result = runCatching { coach.playComputerMove(record, preferences, clock()) }
                .getOrElse { failure -> return@launch fail(failure) }
            if (!stillCurrent(record)) return@launch
            // Keep the feedback on the player's move unless the opponent's move has its own news.
            commit(result.record, result.event ?: _state.value.event, hint = null)
            continueGame(result.record)
        }
    }

    private fun commit(record: GameRecord, event: TeachingEvent?, hint: Hint?) {
        val phase = when {
            record.outcome != null -> Phase.GAME_OVER
            record.isHumanTurn -> Phase.HUMAN_TURN
            else -> Phase.OPPONENT_THINKING
        }
        _state.update {
            it.copy(
                phase = phase,
                record = record,
                board = BoardSnapshot.of(record.game, record.humanSide, playable = phase == Phase.HUMAN_TURN),
                pendingMove = null,
                event = event,
                hint = hint,
                canContinue = record.outcome == null,
            )
        }
        scope.launch(Dispatchers.IO) {
            runCatching { store.save(record) }.onFailure { _state.update { it.copy(error = SessionError.SAVE_FAILED) } }
        }
    }

    /** True while [record] is still the live revision; otherwise its result is stale and dropped. */
    private fun stillCurrent(record: GameRecord): Boolean {
        val live = _state.value.record ?: return false
        return live.gameId == record.gameId && live.revision == record.revision
    }

    private fun pendingBoard(record: GameRecord, move: Move): BoardSnapshot =
        BoardSnapshot.of(record.game.play(move), record.humanSide, playable = false)

    private fun fail(failure: Throwable) {
        if (failure is kotlinx.coroutines.CancellationException) return
        val record = _state.value.record
        _state.update { state ->
            state.copy(
                error = SessionError.ENGINE_UNAVAILABLE,
                pendingMove = null,
                phase = if (record?.isHumanTurn == true) Phase.HUMAN_TURN else state.phase,
                board = record?.let { BoardSnapshot.of(it.game, it.humanSide, playable = it.isHumanTurn) } ?: state.board,
            )
        }
    }

    private fun cancelWork() {
        work?.cancel()
        warmup?.cancel()
        work = null
        warmup = null
    }
}
