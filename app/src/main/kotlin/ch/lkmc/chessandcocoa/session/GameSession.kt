// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.session

import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.chess.Position
import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.coach.decide.Difficulty
import ch.lkmc.chessandcocoa.coach.game.Coach
import ch.lkmc.chessandcocoa.coach.game.GameRecord
import ch.lkmc.chessandcocoa.coach.teach.ExperiencePreferences
import ch.lkmc.chessandcocoa.coach.teach.DisclosureSetting
import ch.lkmc.chessandcocoa.coach.teach.GameReview
import ch.lkmc.chessandcocoa.coach.teach.Hint
import ch.lkmc.chessandcocoa.coach.teach.HintLevel
import ch.lkmc.chessandcocoa.coach.teach.OpponentIntent
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
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
    /** Feedback for the current turn: on the player's last move, then any news from the opponent's reply. */
    val events: List<TeachingEvent> = emptyList(),
    /** The move list as numbered rows in SAN, e.g. "1. e4 e5", "2. Nf3". */
    val moves: List<MoveRow> = emptyList(),
    val hint: Hint? = null,
    val opponentIntent: OpponentIntent? = null,
    val preferences: ExperiencePreferences = ExperiencePreferences(),
    /** The game "Continue" would open, or null when there is none. */
    val resumable: GameSummary? = null,
    val error: SessionError? = null,
) {
    val canContinue: Boolean get() = resumable != null
}

/** One numbered line of the move list; a side that has not moved on that line is null. */
data class MoveRow(val number: Int, val white: String?, val black: String?)

/** What the home screen shows about the game "Continue" opens. */
data class GameSummary(
    val difficulty: Difficulty,
    val humanSide: Side,
    val moveNumber: Int,
    val humanToMove: Boolean,
    val board: BoardSnapshot,
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
    /** Read by [flushSaves] from any thread. */
    @Volatile
    private var lastSave: Job? = null

    /** One writer, first in first out: saves land in revision order. */
    private val saveDispatcher = Dispatchers.IO.limitedParallelism(1)

    init {
        scope.launch(Dispatchers.IO) {
            val saved = store.latestUnfinished()
            // A game started meanwhile is newer than anything on disk.
            _state.update { it.copy(resumable = if (it.record == null) saved?.let(::summary) else it.resumable) }
        }
    }

    /** Starts a game; [startFen] allows curated practice positions (concept Section 15.2). */
    fun newGame(side: SideChoice, difficulty: Difficulty, startFen: String = Position.START_FEN) {
        cancelWork()
        val seed = seeds()
        val humanSide = when (side) {
            SideChoice.WHITE -> Side.WHITE
            SideChoice.BLACK -> Side.BLACK
            SideChoice.RANDOM -> if (seed and 1L == 0L) Side.WHITE else Side.BLACK
        }
        val record = coach.newGame(UUID.randomUUID().toString(), humanSide, difficulty, seed, clock(), startFen)
        continueGame(commit(record, events = emptyList(), hint = null))
    }

    /** Resumes the most recent unfinished game, with its recorded plans intact. */
    fun resume() {
        cancelWork()
        scope.launch {
            val saved = withContext(Dispatchers.IO) { store.latestUnfinished() } ?: return@launch
            continueGame(commit(saved, events = saved.events.filter { it.ply >= saved.ply - 1 }, hint = null))
        }
    }

    fun play(move: Move) {
        val current = _state.value
        val record = current.record ?: return
        if (current.phase != Phase.HUMAN_TURN) return
        if (current.board?.legalMoves?.get(move.from)?.contains(move) != true) return

        _state.update { it.copy(phase = Phase.ASSESSING, pendingMove = move, hint = null, opponentIntent = null, board = pendingBoard(record, move)) }
        work = scope.launch {
            val result = runCatching { coach.playHumanMove(record, move, current.preferences, clock()) }
                .getOrElse { failure -> return@launch fail(failure) }
            if (!stillCurrent(record)) return@launch
            continueGame(commit(result.record, listOfNotNull(result.event), hint = null))
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
            commit(next, current.events, hint)
        }
    }

    /** Explicit takeback to the player's previous decision; never automatic (Section 4.4). */
    fun takeBack() {
        val current = _state.value
        val record = current.record ?: return
        cancelWork()

        // A move still being assessed is not in the record yet: taking it back just drops it.
        val restored = if (current.phase == Phase.ASSESSING) record else coach.takeBack(record, clock())
        continueGame(commit(restored, events = if (restored === record) current.events else emptyList(), hint = null))
    }

    fun resign() {
        val record = _state.value.record ?: return
        cancelWork()
        commit(coach.resign(record, clock()), events = emptyList(), hint = null)
    }

    fun review(): GameReview? = _state.value.record?.let(coach::review)

    /**
     * What "Show me why" shows for [event]: its continuation from its own start,
     * or, for a point with no line, the one position it explains (after its
     * move). Never the live board, which may have moved on since.
     */
    fun evidence(event: TeachingEvent): List<EvidenceFrame> {
        val record = _state.value.record ?: return emptyList()
        event.evidence?.let { return it.frames(record.humanSide, lastMove = null) }
        if (event.ply !in 0..record.game.ply) return emptyList()

        val explained = BoardSnapshot.of(record.game.truncate(event.ply), record.humanSide, playable = false)
        return listOf(EvidenceFrame(explained, san = null))
    }

    fun updatePreferences(preferences: ExperiencePreferences) {
        val previousDisclosure = _state.value.preferences.disclosure
        preferencesStore.save(preferences)
        _state.update {
            it.copy(preferences = preferences, opponentIntent = it.opponentIntent.takeIf { preferences.disclosure == DisclosureSetting.WITH_MOVE })
        }
        val current = _state.value
        val record = current.record ?: return
        if (previousDisclosure != preferences.disclosure && current.phase == Phase.HUMAN_TURN) commit(record, current.events, current.hint)
    }

    /** Clears the error. An opponent move that failed is tried again, so the game never stalls on it. */
    fun dismissError() {
        _state.update { it.copy(error = null) }
        val current = _state.value
        val record = current.record ?: return
        if (current.phase != Phase.OPPONENT_THINKING || work?.isActive == true) return
        continueGame(record)
    }

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
            // Keep the feedback on the player's move; the opponent's news joins it.
            continueGame(commit(result.record, _state.value.events + listOfNotNull(result.event), hint = null))
        }
    }

    private fun commit(source: GameRecord, events: List<TeachingEvent>, hint: Hint?): GameRecord {
        val (record, intent) = if (_state.value.preferences.disclosure == DisclosureSetting.WITH_MOVE) {
            coach.revealOpponentIntent(source, clock())
        } else {
            source to null
        }
        val phase = when {
            record.outcome != null -> Phase.GAME_OVER
            record.isHumanTurn -> Phase.HUMAN_TURN
            else -> Phase.OPPONENT_THINKING
        }
        // Issue the save before publishing the state, so anyone who sees this
        // revision and calls flushSaves also waits for it to reach the disk.
        lastSave = scope.launch(saveDispatcher) {
            runCatching { store.save(record) }.onFailure { _state.update { it.copy(error = SessionError.SAVE_FAILED) } }
        }
        _state.update {
            it.copy(
                phase = phase,
                record = record,
                board = BoardSnapshot.of(record.game, record.humanSide, playable = phase == Phase.HUMAN_TURN),
                pendingMove = null,
                events = events,
                moves = moveRows(record),
                hint = hint,
                opponentIntent = intent,
                resumable = if (record.outcome == null) summary(record) else null,
            )
        }
        return record
    }

    /** Waits until every save issued so far is on disk (for leaving the app, and tests). */
    suspend fun flushSaves() {
        lastSave?.join()
    }

    /** Numbered SAN rows, starting from the game's own first move number and side. */
    private fun moveRows(record: GameRecord): List<MoveRow> {
        val game = record.game
        val first = game.positions.first()
        val rows = mutableListOf<MoveRow>()
        var number = first.fullmoveNumber
        game.sanMoves().forEachIndexed { index, san ->
            val white = (first.sideToMove == Side.WHITE) == (index % 2 == 0)
            if (white) {
                rows += MoveRow(number, san, null)
                return@forEachIndexed
            }
            // Black's move completes the row White opened, or starts one when the game began with Black to move.
            val open = rows.lastOrNull()?.takeIf { it.number == number && it.black == null }
            if (open != null) rows[rows.lastIndex] = open.copy(black = san) else rows += MoveRow(number, null, san)
            number++
        }
        return rows
    }

    private fun summary(record: GameRecord): GameSummary = GameSummary(
        difficulty = record.difficulty,
        humanSide = record.humanSide,
        moveNumber = record.game.current.fullmoveNumber,
        humanToMove = record.isHumanTurn,
        board = BoardSnapshot.of(record.game, record.humanSide, playable = false),
    )

    /**
     * True while [record] is still the live revision and the calling job was
     * not cancelled; otherwise its result is stale and dropped (R11).
     */
    private suspend fun stillCurrent(record: GameRecord): Boolean {
        if (!currentCoroutineContext().isActive) return false
        val live = _state.value.record ?: return false
        return live.gameId == record.gameId && live.revision == record.revision
    }

    private fun pendingBoard(record: GameRecord, move: Move): BoardSnapshot =
        BoardSnapshot.of(record.game.play(move), record.humanSide, playable = false)

    private fun fail(failure: Throwable) {
        if (failure is kotlinx.coroutines.CancellationException) return
        _state.update { it.copy(error = SessionError.ENGINE_UNAVAILABLE, pendingMove = null) }
        val current = _state.value
        val record = current.record ?: return
        // Restore the board and its disclosure together after a failed, uncommitted move.
        commit(record, current.events, current.hint)
    }

    private fun cancelWork() {
        work?.cancel()
        warmup?.cancel()
        work = null
        warmup = null
    }
}
