// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.lkmc.chessandcocoa.R
import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.chess.Square
import ch.lkmc.chessandcocoa.coach.decide.Difficulty
import ch.lkmc.chessandcocoa.coach.evidence.MarkedSquare
import ch.lkmc.chessandcocoa.coach.evidence.SquareRole
import ch.lkmc.chessandcocoa.coach.teach.BoardSide
import ch.lkmc.chessandcocoa.coach.teach.TeachingEvent
import ch.lkmc.chessandcocoa.session.EvidenceFrame
import ch.lkmc.chessandcocoa.session.EvidenceLine
import ch.lkmc.chessandcocoa.session.GameSession
import ch.lkmc.chessandcocoa.session.Phase
import ch.lkmc.chessandcocoa.session.SessionState
import ch.lkmc.chessandcocoa.ui.board.BoardFrame
import ch.lkmc.chessandcocoa.ui.board.BoardStyle
import ch.lkmc.chessandcocoa.ui.board.ChessBoard
import ch.lkmc.chessandcocoa.ui.board.Coordinates
import ch.lkmc.chessandcocoa.ui.board.MoveAnimation
import ch.lkmc.chessandcocoa.ui.common.IconAction
import ch.lkmc.chessandcocoa.ui.common.ScreenTopBar
import ch.lkmc.chessandcocoa.ui.theme.Space

/** Callbacks from the game screen to navigation. */
data class GameNavigation(
    val onBack: () -> Unit,
    val onNewGame: () -> Unit,
    val onReview: () -> Unit,
    val onSettings: () -> Unit,
)

/**
 * "Show me why" for one teaching event: the claim's own continuation,
 * replayed on the main board in place of the live game, which it never touches.
 */
private class Evidence(val event: TeachingEvent, val frames: List<EvidenceFrame>, val line: EvidenceLine = EvidenceLine.MAIN)

/**
 * The game: board first, one calm coaching panel, voluntary hints, explicit
 * takebacks (concept Section 4.4). Nothing here is timed.
 *
 * Portrait, top to bottom; the board's size depends only on the window, so it
 * never moves when coaching appears or changes:
 *
 * ```
 *   ← Steady opponent · You play Black   ⋮
 *   ● Your move
 *   ┌──────────── board ────────────┐
 *   └───────────────────────────────┘
 *   1. e4 e5  2. Bc4 Nf6  3. Qf3 …        (inside the panel on short screens)
 *   coaching panel (scrolls)
 *   [ Hint ]  [ Take back ]
 * ```
 *
 * Landscape and wide, near-square windows put the board beside the coaching;
 * the board side setting picks left (the default) or right.
 */
@Composable
fun GameScreen(session: GameSession, navigation: GameNavigation) {
    val state by session.state.collectAsStateWithLifecycle()
    val record = state.record ?: return
    val liveBoard = state.board ?: return

    // Selection and the tap handler outlive recompositions of individual squares, so they read
    // the live board through rememberUpdatedState instead of capturing a stale snapshot.
    var selected by remember { mutableStateOf<Square?>(null) }
    var promotion by remember { mutableStateOf<List<Move>>(emptyList()) }
    var evidence by remember { mutableStateOf<Evidence?>(null) }
    var step by remember { mutableIntStateOf(0) }
    var resignAsked by remember { mutableStateOf(false) }
    var feedbackHidden by remember(state.events.firstOrNull()?.id) { mutableStateOf(false) }
    val currentBoard by rememberUpdatedState(liveBoard)
    val livePhase by rememberUpdatedState(state.phase)

    LaunchedEffect(record.revision, state.phase) {
        selected = null
        promotion = emptyList()
    }

    // A replay belongs to the game and branch it explains.
    LaunchedEffect(record.gameId, record.branchId) { evidence = null }
    BackHandler(enabled = evidence != null) { evidence = null }

    val onTap: (Square) -> Unit = remember {
        { square ->
            val from = selected
            val moves = from?.let { currentBoard.legalMoves[it] }.orEmpty().filter { it.to == square }
            when {
                livePhase != Phase.HUMAN_TURN -> Unit
                moves.size == 1 -> {
                    selected = null
                    session.play(moves.single())
                }
                moves.size > 1 -> promotion = moves
                // Any piece can be selected to see how it is protected; the player's own also show their moves.
                else -> selected = if (square != from && square in currentBoard.pieces) square else null
            }
        }
    }

    val showWhy: (TeachingEvent) -> Unit = { event ->
        step = 0
        evidence = Evidence(event, session.evidence(event))
    }

    // Only events about the position on the board mark it; older ones replay in "Show me why".
    val liveMarks: List<MarkedSquare> = buildList {
        if (!feedbackHidden) state.events.filter { it.ply == record.ply }.forEach { addAll(it.marks) }
        state.hint?.let { addAll(it.marks) }
        selected?.let { square -> liveBoard.defenders[square].orEmpty().forEach { add(MarkedSquare(it, SquareRole.DEFENDER)) } }
    }
    val protection = selected?.let { square ->
        liveBoard.pieces[square]?.let { piece ->
            Protection(square, piece, liveBoard.defenders[square].orEmpty().mapNotNull { defender -> liveBoard.pieces[defender]?.let { defender to it } })
        }
    }

    val shown = evidence
    val frame = shown?.frames?.getOrNull(step.coerceIn(0, (shown.frames.size - 1).coerceAtLeast(0)))
    val liveAnimation = rememberAdvance(record.gameId, record.ply + if (state.pendingMove != null) 1 else 0)
    val stepAnimation = rememberAdvance(shown?.let { it.event.id to it.line }, step)

    val boardView: @Composable (Modifier) -> Unit = { modifier ->
        val coordinates = if (state.preferences.showCoordinates) Coordinates.SHOWN else Coordinates.HIDDEN
        val style = if (state.preferences.highContrastBoard) BoardStyle.HIGH_CONTRAST else BoardStyle.COCOA
        if (shown != null) {
            // Marks belong to the position the session explains; should it have none, the live
            // board stands in unmarked and, like every replay, does not take moves.
            ChessBoard(
                board = frame?.board ?: liveBoard,
                modifier = modifier,
                marks = frame?.marks.orEmpty(),
                arrows = shown.frames.getOrNull(step + 1)?.board?.lastMove?.let(::listOf).orEmpty(),
                coordinates = coordinates,
                style = style,
                frame = BoardFrame.EVIDENCE,
                animation = stepAnimation,
            )
        } else {
            ChessBoard(
                board = liveBoard,
                modifier = modifier,
                selected = selected,
                marks = liveMarks,
                coordinates = coordinates,
                style = style,
                animation = liveAnimation,
                // Squares are buttons only while a move can be made, so TalkBack does not offer dead ones.
                onSquareTap = onTap.takeIf { state.phase == Phase.HUMAN_TURN },
            )
        }
    }

    val status: @Composable () -> Unit = {
        if (shown != null) EvidenceBanner(Modifier.padding(horizontal = Space.gutter, vertical = Space.s))
        else StatusRow(state, Modifier.padding(horizontal = Space.gutter, vertical = Space.s))
    }

    val panel: @Composable (HistoryPlacement) -> Unit = { history ->
        if (shown != null) {
            EvidencePanel(shown.event, shown.frames, step, shown.line, onStep = { step = it }, onLine = { line ->
                step = 0
                evidence = Evidence(shown.event, session.evidence(shown.event, line), line)
            })
        } else {
            CoachPanel(
                state = state,
                marks = liveMarks,
                protection = protection,
                feedbackHidden = feedbackHidden,
                onToggleFeedback = { feedbackHidden = !feedbackHidden },
                onShowWhy = showWhy,
                history = history,
            )
        }
    }

    val actions: @Composable () -> Unit = {
        ActionBar(
            mode = when {
                shown != null -> ActionMode.EVIDENCE
                state.phase == Phase.GAME_OVER -> ActionMode.GAME_OVER
                else -> ActionMode.PLAY
            },
            state = state,
            evidenceStep = step,
            evidenceSteps = shown?.frames?.size ?: 0,
            callbacks = ActionCallbacks(
                onHint = session::hint,
                onTakeBack = session::takeBack,
                onReview = navigation.onReview,
                onNewGame = navigation.onNewGame,
                onStep = { step = it },
                onLeaveEvidence = { evidence = null },
            ),
        )
    }

    val title = stringResource(R.string.opponent_named, stringResource(difficultyLabel(record.difficulty)))
    val subtitle = stringResource(R.string.game_you_play, stringResource(sideName(record.humanSide)))
    // Anything chosen from the menu leaves a replay first, so it never lingers over a changed game.
    val menu: @Composable () -> Unit = {
        GameMenu(
            state,
            GameNavigation(
                onBack = navigation.onBack,
                onNewGame = { evidence = null; navigation.onNewGame() },
                onReview = { evidence = null; navigation.onReview() },
                onSettings = { evidence = null; navigation.onSettings() },
            ),
            onResign = { evidence = null; resignAsked = true },
        )
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints {
            // Reserve the action bar before deciding whether a wide window has room to stack.
            val roomBelowBoard = maxHeight - maxWidth - TOOLBAR_HEIGHT
            val nearSquare = maxWidth >= TWO_PANE_MIN_WIDTH && roomBelowBoard < STACKED_SCREEN_ROOM
            if (maxWidth > maxHeight || nearSquare) {
                SideBySideGame(title, subtitle, navigation.onBack, menu, state.preferences.boardSide, boardView, status, panel, actions)
            } else {
                PortraitGame(title, subtitle, navigation.onBack, menu, boardView, status, panel, actions, state)
            }
        }
    }

    if (promotion.isNotEmpty()) {
        PromotionDialog(promotion, liveBoard.sideToMove, onChoose = { move -> promotion = emptyList(); selected = null; session.play(move) }, onDismiss = { promotion = emptyList() })
    }
    if (resignAsked) {
        ResignDialog(onResign = { resignAsked = false; session.resign() }, onDismiss = { resignAsked = false })
    }
    state.error?.let { error -> ErrorDialog(error, onDismiss = session::dismissError) }
}

/**
 * Screen readers reach the status, the coaching and the actions before the
 * board's 64 squares, which stay together in reading order.
 */
private val BoardTraversal = Modifier.semantics {
    isTraversalGroup = true
    traversalIndex = 1f
}

/** Where the move list sits: under the board, or inside the panel when the screen is short. */
enum class HistoryPlacement { STRIP, IN_PANEL }

@Composable
private fun PortraitGame(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    menu: @Composable () -> Unit,
    board: @Composable (Modifier) -> Unit,
    status: @Composable () -> Unit,
    panel: @Composable (HistoryPlacement) -> Unit,
    actions: @Composable () -> Unit,
    state: SessionState,
) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))) {
        ScreenTopBar(title = title, subtitle = subtitle, onBack = onBack, actions = { menu() })

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val boardSide = minOf(maxWidth - Space.m * 2, MAX_BOARD)
            val placement = if (maxHeight - boardSide < SHORT_SCREEN_ROOM) HistoryPlacement.IN_PANEL else HistoryPlacement.STRIP
            BoardColumn(
                maxBoard = boardSide,
                status = status,
                board = { board(Modifier.fillMaxSize().then(BoardTraversal)) },
                history = if (placement == HistoryPlacement.STRIP) ({ MoveHistory(state.moves, HistoryLayout.STRIP) }) else null,
                panel = { panel(placement) },
                actions = actions,
            )
        }
    }
}

/**
 * Stacks status, board, move strip, panel and actions. The board takes the
 * width it is offered and shrinks when tall text or a short window would
 * leave the panel too little room: first down to [MIN_BOARD], below that
 * only as far as the window forces. The panel gets whatever height is left,
 * so nothing ever sits under the actions.
 */
@Composable
private fun BoardColumn(
    maxBoard: Dp,
    status: @Composable () -> Unit,
    board: @Composable () -> Unit,
    history: (@Composable () -> Unit)?,
    panel: @Composable () -> Unit,
    actions: @Composable () -> Unit,
) {
    Layout(
        contents = listOf(status, board, history ?: {}, panel, actions),
        modifier = Modifier.fillMaxSize(),
    ) { (statusM, boardM, historyM, panelM, actionsM), constraints ->
        val width = constraints.maxWidth
        val loose = Constraints(maxWidth = width, maxHeight = constraints.maxHeight)
        val statusP = statusM.map { it.measure(loose) }
        val historyP = historyM.map { it.measure(loose) }
        val actionsP = actionsM.map { it.measure(loose) }
        val chrome = (statusP + historyP + actionsP).sumOf { it.height }

        val available = (constraints.maxHeight - chrome - BOARD_GAP.roundToPx()).coerceAtLeast(0)
        val preferred = available - MIN_PANEL.roundToPx()
        val side = minOf(maxBoard.roundToPx(), maxOf(preferred, minOf(MIN_BOARD.roundToPx(), available)))
        val boardP = boardM.map { it.measure(Constraints.fixed(side, side)) }
        val panelHeight = available - side
        val panelP = panelM.map { it.measure(Constraints.fixed(width, panelHeight)) }

        layout(width, constraints.maxHeight) {
            var y = 0
            statusP.forEach { it.place(0, y); y += it.height }
            boardP.forEach { it.place((width - side) / 2, y) }
            y += side + BOARD_GAP.roundToPx()
            historyP.forEach { it.place(0, y); y += it.height }
            panelP.forEach { it.place(0, y); y += it.height }
            actionsP.forEach { it.place(0, constraints.maxHeight - it.height) }
        }
    }
}

@Composable
private fun SideBySideGame(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    menu: @Composable () -> Unit,
    boardSide: BoardSide,
    board: @Composable (Modifier) -> Unit,
    status: @Composable () -> Unit,
    panel: @Composable (HistoryPlacement) -> Unit,
    actions: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        // The board takes the full height it can, never more than most of the width.
        val side = minOf(maxHeight - Space.m * 2, maxWidth * WIDE_BOARD_SHARE)

        // LEFT and RIGHT name physical sides, but a Row flips in a right-to-left layout,
        // so the order in the Row follows the layout direction. The window-edge padding
        // stays on the board's outer side, whichever slot it takes.
        val boardFirst = (boardSide == BoardSide.LEFT) == (LocalLayoutDirection.current == LayoutDirection.Ltr)
        val outerPadding = if (boardFirst) PaddingValues(start = Space.m) else PaddingValues(end = Space.m)

        Row(Modifier.fillMaxSize().padding(outerPadding), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            val boardPane: @Composable () -> Unit = {
                Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                    board(Modifier.size(side).then(BoardTraversal))
                }
            }
            val coachingPane: @Composable () -> Unit = {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    ScreenTopBar(title = title, subtitle = subtitle, onBack = onBack, windowInsets = WindowInsets(0), actions = { menu() })
                    status()
                    Box(Modifier.weight(1f).fillMaxWidth()) { panel(HistoryPlacement.IN_PANEL) }
                    actions()
                }
            }
            if (boardFirst) {
                boardPane()
                coachingPane()
            } else {
                coachingPane()
                boardPane()
            }
        }
    }
}

/** What the action bar offers. */
private enum class ActionMode { PLAY, GAME_OVER, EVIDENCE }

private class ActionCallbacks(
    val onHint: () -> Unit,
    val onTakeBack: () -> Unit,
    val onReview: () -> Unit,
    val onNewGame: () -> Unit,
    val onStep: (Int) -> Unit,
    val onLeaveEvidence: () -> Unit,
)

/**
 * A toolbar of equal segments, icon over label, always one row so the panel
 * above keeps its height. One segment at a time carries the emphasis: Hint
 * while playing, Review once the game is over, Back to game in a replay.
 */
@Composable
private fun ActionBar(mode: ActionMode, state: SessionState, evidenceStep: Int, evidenceSteps: Int, callbacks: ActionCallbacks) {
    val record = state.record ?: return
    val canTakeBack = record.assessments.isNotEmpty() && state.phase != Phase.ASSESSING
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.m, vertical = Space.s),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        when (mode) {
            ActionMode.PLAY -> {
                val hintLabel = if (record.hintLevelAt(record.ply) == null) R.string.action_hint else R.string.action_more_hint
                ToolbarAction(R.drawable.ic_lightbulb, stringResource(hintLabel), callbacks.onHint, ActionStyle.ACCENT, enabled = state.phase == Phase.HUMAN_TURN)
                ToolbarAction(R.drawable.ic_undo, stringResource(R.string.action_take_back), callbacks.onTakeBack, enabled = canTakeBack)
            }
            ActionMode.GAME_OVER -> {
                ToolbarAction(R.drawable.ic_auto_stories, stringResource(R.string.action_review_game), callbacks.onReview, ActionStyle.PRIMARY)
                ToolbarAction(R.drawable.ic_add, stringResource(R.string.action_new_game), callbacks.onNewGame)
                ToolbarAction(R.drawable.ic_undo, stringResource(R.string.action_take_back), callbacks.onTakeBack, enabled = canTakeBack)
            }
            ActionMode.EVIDENCE -> {
                ToolbarAction(R.drawable.ic_close, stringResource(R.string.evidence_back_to_game), callbacks.onLeaveEvidence, ActionStyle.PRIMARY)
                if (evidenceSteps > 1) {
                    ToolbarAction(R.drawable.ic_chevron_left, stringResource(R.string.evidence_step_back), { callbacks.onStep(evidenceStep - 1) }, enabled = evidenceStep > 0)
                    ToolbarAction(R.drawable.ic_chevron_right, stringResource(R.string.evidence_step_forward), { callbacks.onStep(evidenceStep + 1) }, enabled = evidenceStep < evidenceSteps - 1)
                }
            }
        }
    }
}

/** How strongly a toolbar segment asks to be pressed. */
private enum class ActionStyle { PRIMARY, ACCENT, QUIET }

@Composable
private fun RowScope.ToolbarAction(icon: Int, label: String, onClick: () -> Unit, style: ActionStyle = ActionStyle.QUIET, enabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (style) {
        ActionStyle.PRIMARY -> colors.primary to colors.onPrimary
        ActionStyle.ACCENT -> colors.tertiaryContainer to colors.onTertiaryContainer
        ActionStyle.QUIET -> colors.surfaceContainerLow to colors.onSurface
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = if (enabled) container else colors.surfaceContainerLow,
        contentColor = if (enabled) content else colors.onSurface.copy(alpha = DISABLED_ALPHA),
        modifier = Modifier.weight(1f).heightIn(min = TOOLBAR_HEIGHT),
    ) {
        Column(
            Modifier.padding(horizontal = Space.xs, vertical = Space.s),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null)
            Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = Space.xxs))
        }
    }
}

@Composable
private fun GameMenu(state: SessionState, navigation: GameNavigation, onResign: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconAction(R.drawable.ic_more_vert, stringResource(R.string.action_more), { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuItem(R.drawable.ic_add, R.string.action_new_game) { open = false; navigation.onNewGame() }
            MenuItem(R.drawable.ic_auto_stories, R.string.action_review) { open = false; navigation.onReview() }
            MenuItem(R.drawable.ic_settings, R.string.action_settings) { open = false; navigation.onSettings() }
            if (state.phase != Phase.GAME_OVER) {
                MenuItem(R.drawable.ic_flag, R.string.action_resign) { open = false; onResign() }
            }
        }
    }
}

@Composable
private fun MenuItem(icon: Int, label: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label), style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = { Icon(painterResource(icon), contentDescription = null) },
        onClick = onClick,
        modifier = Modifier.widthIn(min = MENU_WIDTH),
    )
}

/**
 * [MoveAnimation.SLIDE] only when [position] moved one step forward within the
 * same [key] (game or evidence line); takebacks, resumes and jumps never slide.
 */
@Composable
private fun rememberAdvance(key: Any?, position: Int): MoveAnimation {
    val last = remember { LastPosition() }
    val advance = last.key == key && position == last.position + 1
    SideEffect {
        last.key = key
        last.position = position
    }
    return if (advance) MoveAnimation.SLIDE else MoveAnimation.NONE
}

private class LastPosition {
    var key: Any? = null
    var position: Int = Int.MIN_VALUE
}

fun difficultyLabel(level: Difficulty): Int = when (level) {
    Difficulty.GENTLE -> R.string.difficulty_gentle
    Difficulty.STEADY -> R.string.difficulty_steady
    Difficulty.KEEN -> R.string.difficulty_keen
}

fun sideName(side: Side): Int = if (side == Side.WHITE) R.string.side_white else R.string.side_black

private val MAX_BOARD = 560.dp
private val MIN_BOARD = 220.dp
private val MIN_PANEL = 96.dp
private val BOARD_GAP = 4.dp
private val MENU_WIDTH = 200.dp
private val TOOLBAR_HEIGHT = 60.dp
private const val DISABLED_ALPHA = 0.38f

/** Below this height beyond the board, the move list stays inside the panel. */
private val SHORT_SCREEN_ROOM = 330.dp
/** Room for the header, status and coaching after reserving the board and actions. */
private val STACKED_SCREEN_ROOM = 330.dp
private val TWO_PANE_MIN_WIDTH = 600.dp
private const val WIDE_BOARD_SHARE = 0.56f
