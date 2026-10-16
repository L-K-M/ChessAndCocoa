// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.lkmc.chessandcocoa.R
import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.chess.Square
import ch.lkmc.chessandcocoa.coach.evidence.MarkedSquare
import ch.lkmc.chessandcocoa.coach.teach.TeachingEvent
import ch.lkmc.chessandcocoa.session.GameSession
import ch.lkmc.chessandcocoa.session.Phase
import ch.lkmc.chessandcocoa.session.SessionError
import ch.lkmc.chessandcocoa.session.SessionState
import ch.lkmc.chessandcocoa.ui.board.BoardStyle
import ch.lkmc.chessandcocoa.ui.board.ChessBoard
import ch.lkmc.chessandcocoa.ui.board.Coordinates

/** Callbacks from the game screen to navigation. */
data class GameNavigation(
    val onBack: () -> Unit,
    val onNewGame: () -> Unit,
    val onReview: () -> Unit,
    val onSettings: () -> Unit,
)

/**
 * The game: board first, one calm feedback card, voluntary hints, explicit
 * takebacks (concept Section 4.4). Nothing here is timed.
 */
@Composable
fun GameScreen(session: GameSession, navigation: GameNavigation) {
    val state by session.state.collectAsStateWithLifecycle()
    val record = state.record ?: return
    val board = state.board ?: return

    // Selection and the tap handler outlive recompositions of individual squares, so they read
    // the live board through rememberUpdatedState instead of capturing a stale snapshot.
    var selected by remember { mutableStateOf<Square?>(null) }
    var promotion by remember { mutableStateOf<List<Move>>(emptyList()) }
    var evidenceFor by remember { mutableStateOf<TeachingEvent?>(null) }
    var resignAsked by remember { mutableStateOf(false) }
    var feedbackHidden by remember(state.events.firstOrNull()?.id) { mutableStateOf(false) }
    val liveBoard by rememberUpdatedState(board)
    val livePhase by rememberUpdatedState(state.phase)

    LaunchedEffect(record.revision, state.phase) {
        selected = null
        promotion = emptyList()
    }

    val onTap: (Square) -> Unit = remember {
        { square ->
            val from = selected
            val moves = from?.let { liveBoard.legalMoves[it] }.orEmpty().filter { it.to == square }
            when {
                livePhase != Phase.HUMAN_TURN -> Unit
                moves.size == 1 -> {
                    selected = null
                    session.play(moves.single())
                }
                moves.size > 1 -> promotion = moves
                else -> selected = if (square != from && liveBoard.legalMoves.containsKey(square)) square else null
            }
        }
    }

    val marks: List<MarkedSquare> = buildList {
        // Only events about the position on the board mark it; older ones replay in "Show me why".
        if (!feedbackHidden) state.events.filter { it.ply == record.ply }.forEach { addAll(it.marks) }
        state.hint?.let { addAll(it.marks) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                navigationIcon = { TextButton(onClick = navigation.onBack) { Text(stringResource(R.string.action_back)) } },
                actions = { GameMenu(state, navigation, onResign = { resignAsked = true }) },
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth > maxHeight
            val boardView: @Composable (Modifier) -> Unit = { modifier ->
                ChessBoard(
                    board = board,
                    modifier = modifier,
                    selected = selected,
                    marks = marks,
                    coordinates = if (state.preferences.showCoordinates) Coordinates.SHOWN else Coordinates.HIDDEN,
                    style = if (state.preferences.highContrastBoard) BoardStyle.HIGH_CONTRAST else BoardStyle.COCOA,
                    onSquareTap = onTap,
                )
            }
            val panel: @Composable (Modifier) -> Unit = { modifier ->
                SidePanel(
                    state = state,
                    showStatus = wide,
                    feedbackHidden = feedbackHidden,
                    onToggleFeedback = { feedbackHidden = !feedbackHidden },
                    onShowWhy = { evidenceFor = it },
                    onHint = session::hint,
                    onTakeBack = session::takeBack,
                    onReview = navigation.onReview,
                    modifier = modifier,
                )
            }
            if (wide) {
                Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    boardView(Modifier.fillMaxSize().weight(1f, fill = false))
                    panel(Modifier.weight(1f).fillMaxSize())
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                    StatusLine(state, Modifier.padding(vertical = 8.dp))
                    boardView(Modifier.fillMaxWidth().widthIn(max = 560.dp).align(Alignment.CenterHorizontally))
                    panel(Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }

    if (promotion.isNotEmpty()) {
        PromotionDialog(promotion, board.sideToMove, onChoose = { move -> promotion = emptyList(); selected = null; session.play(move) }, onDismiss = { promotion = emptyList() })
    }
    evidenceFor?.let { event -> EvidenceSheet(event, session, onDismiss = { evidenceFor = null }) }
    if (resignAsked) {
        AlertDialog(
            onDismissRequest = { resignAsked = false },
            title = { Text(stringResource(R.string.resign_title)) },
            text = { Text(stringResource(R.string.resign_text)) },
            confirmButton = { TextButton(onClick = { resignAsked = false; session.resign() }) { Text(stringResource(R.string.action_resign)) } },
            dismissButton = { TextButton(onClick = { resignAsked = false }) { Text(stringResource(R.string.resign_keep_playing)) } },
        )
    }
    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = session::dismissError,
            text = { Text(stringResource(if (error == SessionError.SAVE_FAILED) R.string.error_save else R.string.error_engine)) },
            confirmButton = { TextButton(onClick = session::dismissError) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

@Composable
private fun GameMenu(state: SessionState, navigation: GameNavigation, onResign: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text(stringResource(R.string.action_more)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.action_new_game)) }, onClick = { open = false; navigation.onNewGame() })
            DropdownMenuItem(text = { Text(stringResource(R.string.action_review)) }, onClick = { open = false; navigation.onReview() })
            DropdownMenuItem(text = { Text(stringResource(R.string.action_settings)) }, onClick = { open = false; navigation.onSettings() })
            if (state.phase != Phase.GAME_OVER) {
                DropdownMenuItem(text = { Text(stringResource(R.string.action_resign)) }, onClick = { open = false; onResign() })
            }
        }
    }
}

@Composable
private fun SidePanel(
    state: SessionState,
    showStatus: Boolean,
    feedbackHidden: Boolean,
    onToggleFeedback: () -> Unit,
    onShowWhy: (TeachingEvent) -> Unit,
    onHint: () -> Unit,
    onTakeBack: () -> Unit,
    onReview: () -> Unit,
    modifier: Modifier,
) {
    val record = state.record ?: return
    Column(modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showStatus) StatusLine(state)

        state.events.forEach { event ->
            FeedbackCard(event, hidden = feedbackHidden, onToggle = onToggleFeedback, onShowWhy = { onShowWhy(event) })
        }
        state.hint?.let { hint -> HintCard(hint) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val humanTurn = state.phase == Phase.HUMAN_TURN
            OutlinedButton(onClick = onHint, enabled = humanTurn) {
                Text(stringResource(if (record.hintLevelAt(record.ply) == null) R.string.action_hint else R.string.action_more_hint))
            }
            OutlinedButton(onClick = onTakeBack, enabled = record.assessments.isNotEmpty() && state.phase != Phase.ASSESSING) {
                Text(stringResource(R.string.action_take_back))
            }
            if (state.phase == Phase.GAME_OVER) {
                OutlinedButton(onClick = onReview) { Text(stringResource(R.string.action_review)) }
            }
        }

        MoveList(state.notation)
        Spacer(Modifier.height(8.dp))
    }
}

/** Whose turn it is, in calm words; announced to screen readers when it changes. */
@Composable
fun StatusLine(state: SessionState, modifier: Modifier = Modifier) {
    val record = state.record ?: return
    val outcome = record.outcome
    val text = when (state.phase) {
        Phase.HUMAN_TURN -> stringResource(if (state.board?.checkSquare != null) R.string.status_your_move_check else R.string.status_your_move)
        Phase.ASSESSING -> stringResource(R.string.status_assessing)
        Phase.OPPONENT_THINKING -> stringResource(R.string.status_thinking)
        Phase.HINTING -> stringResource(R.string.status_hinting)
        Phase.GAME_OVER -> when (outcome?.winner) {
            null -> stringResource(R.string.status_draw)
            record.humanSide -> stringResource(R.string.status_you_won)
            else -> stringResource(R.string.status_you_lost)
        }
        Phase.NO_GAME -> ""
    }
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun MoveList(notation: String) {
    if (notation.isEmpty()) return
    Column {
        Text(stringResource(R.string.moves_title), style = MaterialTheme.typography.labelLarge)
        Text(notation, style = MaterialTheme.typography.bodyMedium)
    }
}
