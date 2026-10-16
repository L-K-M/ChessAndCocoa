// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ch.lkmc.chessandcocoa.R
import ch.lkmc.chessandcocoa.coach.teach.CommentaryLevel
import ch.lkmc.chessandcocoa.coach.teach.DisclosureSetting
import ch.lkmc.chessandcocoa.coach.teach.ExperiencePreferences
import ch.lkmc.chessandcocoa.ui.home.Choice

/** Experience settings only: none of them changes opponent strength or analysis (R9, Section 16.4). */
@Composable
fun SettingsScreen(preferences: ExperiencePreferences, onChange: (ExperiencePreferences) -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.settings_commentary), style = MaterialTheme.typography.titleMedium)
            Choice(preferences.commentary == CommentaryLevel.QUIET, stringResource(R.string.commentary_quiet), { onChange(preferences.copy(commentary = CommentaryLevel.QUIET)) }, stringResource(R.string.commentary_quiet_desc))
            Choice(preferences.commentary == CommentaryLevel.NORMAL, stringResource(R.string.commentary_normal), { onChange(preferences.copy(commentary = CommentaryLevel.NORMAL)) }, stringResource(R.string.commentary_normal_desc))
            Choice(preferences.commentary == CommentaryLevel.DETAILED, stringResource(R.string.commentary_detailed), { onChange(preferences.copy(commentary = CommentaryLevel.DETAILED)) }, stringResource(R.string.commentary_detailed_desc))

            Text(stringResource(R.string.settings_disclosure), style = MaterialTheme.typography.titleMedium)
            Choice(preferences.disclosure == DisclosureSetting.AFTER_CHOICE, stringResource(R.string.disclosure_after), { onChange(preferences.copy(disclosure = DisclosureSetting.AFTER_CHOICE)) }, stringResource(R.string.disclosure_after_desc))
            Choice(preferences.disclosure == DisclosureSetting.REVIEW_ONLY, stringResource(R.string.disclosure_review), { onChange(preferences.copy(disclosure = DisclosureSetting.REVIEW_ONLY)) }, stringResource(R.string.disclosure_review_desc))

            Toggle(stringResource(R.string.settings_numbers), preferences.showNumbers, stringResource(R.string.settings_numbers_desc)) { onChange(preferences.copy(showNumbers = it)) }
            Toggle(stringResource(R.string.settings_coordinates), preferences.showCoordinates) { onChange(preferences.copy(showCoordinates = it)) }
            Toggle(stringResource(R.string.settings_contrast), preferences.highContrastBoard) { onChange(preferences.copy(highContrastBoard = it)) }

            Text(stringResource(R.string.settings_separate_note), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, description: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, onValueChange = onChange, role = Role.Switch).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
