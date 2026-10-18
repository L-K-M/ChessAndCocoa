// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ch.lkmc.chessandcocoa.R
import ch.lkmc.chessandcocoa.chess.Piece
import ch.lkmc.chessandcocoa.chess.PieceType
import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.coach.decide.Difficulty
import ch.lkmc.chessandcocoa.session.GameSummary
import ch.lkmc.chessandcocoa.session.SideChoice
import ch.lkmc.chessandcocoa.ui.board.BoardDetail
import ch.lkmc.chessandcocoa.ui.board.ChessBoard
import ch.lkmc.chessandcocoa.ui.board.Coordinates
import ch.lkmc.chessandcocoa.ui.board.PieceArtwork
import ch.lkmc.chessandcocoa.ui.board.drawableFor
import ch.lkmc.chessandcocoa.ui.common.SectionHeader
import ch.lkmc.chessandcocoa.ui.game.difficultyLabel
import ch.lkmc.chessandcocoa.ui.game.sideName
import ch.lkmc.chessandcocoa.ui.theme.Space

data class HomeActions(
    val onContinue: () -> Unit,
    val onNewGame: (SideChoice, Difficulty) -> Unit,
    val onSettings: () -> Unit,
    val onAbout: () -> Unit,
)

/**
 * "Continue" or "New game" first; no lesson, quiz or setup in the way
 * (Section 4.4). The brand stays small: a mark and a wordmark, then the
 * promise, then the one obvious action.
 *
 * ```
 *   [mug] Chess & Cocoa
 *
 *   a cozy chess teaching app
 *   Play a game.
 *   Understand the moves.
 *
 *   ┌ your game: board · opponent · move ┐     or     three short promises
 *   │ [ Continue ]                        │             [ New game ]
 *   └─────────────────────────────────────┘
 *   [ New game ]
 *
 *   Settings · About & licenses
 * ```
 */
@Composable
fun HomeScreen(
    resumable: GameSummary?,
    engineStarting: Boolean,
    actions: HomeActions,
    lastDifficulty: Difficulty = Difficulty.GENTLE,
) {
    var choosing by rememberSaveable { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
            val wide = maxWidth > maxHeight && maxWidth >= WIDE_WIDTH
            val minHeight = maxHeight
            // The brand, welcome and actions as one centred group; quiet links at the foot.
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Column(
                    Modifier.fillMaxWidth().heightIn(min = minHeight).padding(horizontal = Space.xl, vertical = Space.l),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(0.dp))
                    if (wide) {
                        Row(Modifier.padding(vertical = Space.xl), horizontalArrangement = Arrangement.spacedBy(Space.xxxl), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Brand()
                                Spacer(Modifier.height(Space.xxl))
                                Welcome()
                            }
                            Column(Modifier.weight(1f)) { Actions(resumable, engineStarting, onContinue = actions.onContinue, onNewGame = { choosing = true }) }
                        }
                    } else {
                        Column(Modifier.widthIn(max = CONTENT_WIDTH).padding(vertical = Space.xl)) {
                            Brand()
                            Spacer(Modifier.height(Space.xxxl))
                            Welcome()
                            Spacer(Modifier.height(Space.xxl))
                            Actions(resumable, engineStarting, onContinue = actions.onContinue, onNewGame = { choosing = true })
                        }
                    }
                    Footer(actions)
                }
            }
        }
    }

    if (choosing) {
        NewGameSheet(
            initialDifficulty = resumable?.difficulty ?: lastDifficulty,
            onStart = { side, difficulty -> choosing = false; actions.onNewGame(side, difficulty) },
            onDismiss = { choosing = false },
        )
    }
}

/** The mark and the wordmark, small: the brand without taking over the screen. */
@Composable
private fun Brand(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.hero_cocoa),
            contentDescription = null,
            modifier = Modifier.size(BRAND_MARK).clip(MaterialTheme.shapes.medium),
        )
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = Space.m))
    }
}

@Composable
private fun Welcome() {
    Text(stringResource(R.string.app_descriptor), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
    Spacer(Modifier.height(Space.s))
    Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.displaySmall)
}

@Composable
private fun ColumnScope.Actions(resumable: GameSummary?, engineStarting: Boolean, onContinue: () -> Unit, onNewGame: () -> Unit) {
    if (resumable != null) {
        ContinueCard(resumable, onContinue)
        Spacer(Modifier.height(Space.m))
        OutlinedButton(onClick = onNewGame, modifier = Modifier.fillMaxWidth().height(BIG_BUTTON)) {
            ButtonLabel(R.drawable.ic_add, stringResource(R.string.action_new_game))
        }
    } else {
        Promises()
        Spacer(Modifier.height(Space.xl))
        Button(onClick = onNewGame, modifier = Modifier.fillMaxWidth().height(BIG_BUTTON)) {
            ButtonLabel(R.drawable.ic_play_arrow, stringResource(R.string.action_new_game))
        }
    }

    if (engineStarting) {
        Row(Modifier.padding(top = Space.l).align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.secondary)
            Text(stringResource(R.string.engine_starting), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Space.s))
        }
    }
}

/** The saved game at a glance: its position, opponent, and whose move it is. */
@Composable
private fun ContinueCard(game: GameSummary, onContinue: () -> Unit) {
    val boardDescription = stringResource(R.string.home_board_description)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(Space.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChessBoard(
                    board = game.board,
                    coordinates = Coordinates.HIDDEN,
                    detail = BoardDetail.THUMBNAIL,
                    modifier = Modifier.size(THUMBNAIL).semantics { contentDescription = boardDescription },
                )
                Column(Modifier.padding(start = Space.l), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                    Text(stringResource(R.string.home_continue_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                    Text(stringResource(R.string.opponent_named, stringResource(difficultyLabel(game.difficulty))), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.home_game_detail, stringResource(sideName(game.humanSide)), game.moveNumber),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(if (game.humanToMove) R.string.home_turn_yours else R.string.home_turn_mine),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Spacer(Modifier.height(Space.l))
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(BIG_BUTTON)) {
                ButtonLabel(R.drawable.ic_play_arrow, stringResource(R.string.action_continue))
            }
        }
    }
}

/** What a first game is like, before anyone has to choose anything. */
@Composable
private fun Promises() {
    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        Promise(R.drawable.ic_hourglass_empty, stringResource(R.string.home_value_untimed))
        Promise(R.drawable.ic_auto_stories, stringResource(R.string.home_value_explain))
        Promise(R.drawable.ic_lightbulb, stringResource(R.string.home_value_help))
    }
}

@Composable
private fun Promise(icon: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(PROMISE_ICON).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Space.m))
    }
}

@Composable
private fun Footer(actions: HomeActions, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        TextButton(onClick = actions.onSettings, modifier = Modifier.heightIn(min = Space.touchTarget)) {
            ButtonLabel(R.drawable.ic_settings, stringResource(R.string.action_settings))
        }
        TextButton(onClick = actions.onAbout, modifier = Modifier.heightIn(min = Space.touchTarget)) {
            ButtonLabel(R.drawable.ic_info, stringResource(R.string.action_about))
        }
    }
}

@Composable
private fun ButtonLabel(icon: Int, label: String) {
    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
    Text(label, modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
}

/**
 * Side and opponent in one sheet. Difficulty is described in words and a
 * relative scale, never an Elo rating: the profiles are not calibrated.
 */
@Composable
fun NewGameSheet(initialDifficulty: Difficulty, onStart: (SideChoice, Difficulty) -> Unit, onDismiss: () -> Unit) {
    var side by rememberSaveable { mutableStateOf(SideChoice.WHITE) }
    var difficulty by rememberSaveable { mutableStateOf(initialDifficulty) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.xl).padding(bottom = Space.l).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Text(stringResource(R.string.new_game_title), style = MaterialTheme.typography.headlineSmall)

            SectionHeader(stringResource(R.string.new_game_side), Modifier.padding(top = Space.s))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SideChoice.entries.forEachIndexed { index, choice ->
                    SegmentedButton(
                        selected = side == choice,
                        onClick = { side = choice },
                        shape = SegmentedButtonDefaults.itemShape(index, SideChoice.entries.size),
                        modifier = Modifier.heightIn(min = Space.touchTarget),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            sideKings(choice).forEach { king ->
                                Image(painterResource(drawableFor(king, PieceArtwork.CLASSIC)), contentDescription = null, modifier = Modifier.size(KING_ICON))
                            }
                            Text(stringResource(sideLabel(choice)), modifier = Modifier.padding(start = Space.xs))
                        }
                    }
                }
            }

            SectionHeader(stringResource(R.string.new_game_opponent), Modifier.padding(top = Space.m))
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Difficulty.entries.forEach { level ->
                    DifficultyOption(level, selected = difficulty == level, onSelect = { difficulty = level })
                }
            }

            Row(Modifier.padding(top = Space.s), verticalAlignment = Alignment.Top) {
                Icon(painterResource(R.drawable.ic_hourglass_empty), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Column(Modifier.padding(start = Space.s), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                    Text(stringResource(R.string.new_game_untimed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.new_game_strength_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Button(onClick = { onStart(side, difficulty) }, modifier = Modifier.fillMaxWidth().padding(top = Space.m).height(BIG_BUTTON)) {
                ButtonLabel(R.drawable.ic_play_arrow, stringResource(R.string.new_game_start))
            }
        }
    }
}

@Composable
private fun DifficultyOption(level: Difficulty, selected: Boolean, onSelect: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (selected) colors.secondaryContainer else colors.surfaceContainerLowest,
        contentColor = if (selected) colors.onSecondaryContainer else colors.onSurface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.secondary else colors.outlineVariant),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
    ) {
        Row(Modifier.padding(vertical = Space.m, horizontal = Space.s), verticalAlignment = Alignment.Top) {
            RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(Space.m))
            Column(Modifier.weight(1f).padding(top = Space.s, end = Space.s), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(difficultyLabel(level)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    StrengthScale(level)
                }
                Text(
                    stringResource(difficultyDescription(level)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** Three bars, more filled for a stronger opponent: a relative scale, not a rating. Decorative. */
@Composable
private fun StrengthScale(level: Difficulty) {
    val filled = level.ordinal + 1
    Row(Modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        repeat(Difficulty.entries.size) { index ->
            Box(
                Modifier
                    .width(5.dp)
                    .height(8.dp + 4.dp * index)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(if (index < filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

private fun sideKings(choice: SideChoice): List<Piece> = when (choice) {
    SideChoice.WHITE -> listOf(Piece(Side.WHITE, PieceType.KING))
    SideChoice.BLACK -> listOf(Piece(Side.BLACK, PieceType.KING))
    SideChoice.RANDOM -> listOf(Piece(Side.WHITE, PieceType.KING), Piece(Side.BLACK, PieceType.KING))
}

private fun sideLabel(choice: SideChoice): Int = when (choice) {
    SideChoice.WHITE -> R.string.side_white
    SideChoice.BLACK -> R.string.side_black
    SideChoice.RANDOM -> R.string.side_random
}

private fun difficultyDescription(level: Difficulty): Int = when (level) {
    Difficulty.GENTLE -> R.string.difficulty_gentle_desc
    Difficulty.STEADY -> R.string.difficulty_steady_desc
    Difficulty.KEEN -> R.string.difficulty_keen_desc
}

private val WIDE_WIDTH = 600.dp
private val CONTENT_WIDTH = 480.dp
private val BRAND_MARK = 44.dp
private val THUMBNAIL = 104.dp
private val PROMISE_ICON = 36.dp
private val BIG_BUTTON = 52.dp
private val KING_ICON = 20.dp
