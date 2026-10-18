// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import ch.lkmc.chessandcocoa.R
import ch.lkmc.chessandcocoa.coach.teach.BoardSide
import ch.lkmc.chessandcocoa.coach.teach.CommentaryLevel
import ch.lkmc.chessandcocoa.coach.teach.DisclosureSetting
import ch.lkmc.chessandcocoa.coach.teach.ExperiencePreferences
import ch.lkmc.chessandcocoa.ui.common.ScreenTopBar
import ch.lkmc.chessandcocoa.ui.common.SectionHeader
import ch.lkmc.chessandcocoa.ui.theme.BoardPalette
import ch.lkmc.chessandcocoa.ui.theme.CocoaTheme
import ch.lkmc.chessandcocoa.ui.theme.Space

/** Experience settings only: none of them changes opponent strength or analysis (R9, Section 16.4). */
@Composable
fun SettingsScreen(preferences: ExperiencePreferences, onChange: (ExperiencePreferences) -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = { ScreenTopBar(title = stringResource(R.string.settings_title), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter),
            verticalArrangement = Arrangement.spacedBy(Space.s),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = Space.readingWidth), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                SectionHeader(stringResource(R.string.settings_group_coaching))
                Group {
                    GroupTitle(stringResource(R.string.settings_commentary))
                    Column(Modifier.selectableGroup()) {
                        CommentaryLevel.entries.forEach { level ->
                            Choice(
                                selected = preferences.commentary == level,
                                label = stringResource(commentaryLabel(level)),
                                description = stringResource(commentaryDescription(level)),
                                onSelect = { onChange(preferences.copy(commentary = level)) },
                            )
                        }
                    }
                    GroupDivider()
                    GroupTitle(stringResource(R.string.settings_disclosure))
                    Column(Modifier.selectableGroup()) {
                        DisclosureSetting.entries.forEach { setting ->
                            Choice(
                                selected = preferences.disclosure == setting,
                                label = stringResource(disclosureLabel(setting)),
                                description = stringResource(disclosureDescription(setting)),
                                onSelect = { onChange(preferences.copy(disclosure = setting)) },
                            )
                        }
                    }
                    GroupDivider()
                    Toggle(stringResource(R.string.settings_numbers), preferences.showNumbers, stringResource(R.string.settings_numbers_desc)) { onChange(preferences.copy(showNumbers = it)) }
                }

                SectionHeader(stringResource(R.string.settings_group_board), Modifier.padding(top = Space.m))
                Group {
                    Toggle(stringResource(R.string.settings_coordinates), preferences.showCoordinates) { onChange(preferences.copy(showCoordinates = it)) }
                    GroupDivider()
                    Toggle(stringResource(R.string.settings_contrast), preferences.highContrastBoard, swatch = CocoaTheme.colors.highContrastBoard) { onChange(preferences.copy(highContrastBoard = it)) }
                    GroupDivider()
                    GroupTitle(stringResource(R.string.settings_board_side))
                    Column(Modifier.selectableGroup()) {
                        BoardSide.entries.forEach { side ->
                            Choice(
                                selected = preferences.boardSide == side,
                                label = stringResource(boardSideLabel(side)),
                                description = stringResource(boardSideDescription(side)),
                                onSelect = { onChange(preferences.copy(boardSide = side)) },
                            )
                        }
                    }
                }

                Row(Modifier.padding(vertical = Space.l), verticalAlignment = Alignment.Top) {
                    Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.settings_separate_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Space.s),
                    )
                }
            }
        }
    }
}

/** Related settings share one quiet surface, separated by hairlines. */
@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(vertical = Space.s), content = content)
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = Space.l, end = Space.l, top = Space.m, bottom = Space.xs))
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(Modifier.padding(horizontal = Space.l, vertical = Space.xs), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun Choice(selected: Boolean, label: String, description: String, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(horizontal = Space.s, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(Space.s))
        Column(Modifier.padding(start = Space.xs, end = Space.s)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, description: String? = null, swatch: BoardPalette? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .toggleable(value = checked, onValueChange = onChange, role = Role.Switch)
            .padding(horizontal = Space.l, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = Space.m)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        swatch?.let { Swatch(it) }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Two squares of a board palette: a preview of what the switch changes. Decorative. */
@Composable
private fun Swatch(palette: BoardPalette) {
    Row(Modifier.padding(end = Space.m).clip(MaterialTheme.shapes.extraSmall).clearAndSetSemantics {}) {
        Box(Modifier.size(SWATCH).background(palette.light))
        Box(Modifier.size(SWATCH).background(palette.dark))
    }
}

private fun commentaryLabel(level: CommentaryLevel): Int = when (level) {
    CommentaryLevel.QUIET -> R.string.commentary_quiet
    CommentaryLevel.NORMAL -> R.string.commentary_normal
    CommentaryLevel.DETAILED -> R.string.commentary_detailed
}

private fun commentaryDescription(level: CommentaryLevel): Int = when (level) {
    CommentaryLevel.QUIET -> R.string.commentary_quiet_desc
    CommentaryLevel.NORMAL -> R.string.commentary_normal_desc
    CommentaryLevel.DETAILED -> R.string.commentary_detailed_desc
}

private fun disclosureLabel(setting: DisclosureSetting): Int = when (setting) {
    DisclosureSetting.WITH_MOVE -> R.string.disclosure_with_move
    DisclosureSetting.AFTER_CHOICE -> R.string.disclosure_after
    DisclosureSetting.REVIEW_ONLY -> R.string.disclosure_review
}

private fun disclosureDescription(setting: DisclosureSetting): Int = when (setting) {
    DisclosureSetting.WITH_MOVE -> R.string.disclosure_with_move_desc
    DisclosureSetting.AFTER_CHOICE -> R.string.disclosure_after_desc
    DisclosureSetting.REVIEW_ONLY -> R.string.disclosure_review_desc
}

private fun boardSideLabel(side: BoardSide): Int = when (side) {
    BoardSide.LEFT -> R.string.board_side_left
    BoardSide.RIGHT -> R.string.board_side_right
}

private fun boardSideDescription(side: BoardSide): Int = when (side) {
    BoardSide.LEFT -> R.string.board_side_left_desc
    BoardSide.RIGHT -> R.string.board_side_right_desc
}

private val ROW_HEIGHT = 56.dp
private val SWATCH = 14.dp
