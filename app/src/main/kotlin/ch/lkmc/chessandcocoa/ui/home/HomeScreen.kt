// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ch.lkmc.chessandcocoa.R
import ch.lkmc.chessandcocoa.coach.decide.Difficulty
import ch.lkmc.chessandcocoa.session.SideChoice

data class HomeActions(
    val onContinue: () -> Unit,
    val onNewGame: (SideChoice, Difficulty) -> Unit,
    val onSettings: () -> Unit,
    val onAbout: () -> Unit,
)

/** "Play" and "Continue" first; no lesson, quiz or setup in the way (Section 4.4). */
@Composable
fun HomeScreen(canContinue: Boolean, engineStarting: Boolean, actions: HomeActions) {
    var choosing by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Image(
                painter = painterResource(R.drawable.hero_cocoa),
                contentDescription = null,
                modifier = Modifier.size(180.dp).clip(RoundedCornerShape(32.dp)),
            )
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
            Text(stringResource(R.string.app_descriptor), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)

            Column(Modifier.widthIn(max = 360.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canContinue) {
                    Button(onClick = actions.onContinue, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_continue)) }
                    OutlinedButton(onClick = { choosing = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_new_game)) }
                } else {
                    Button(onClick = { choosing = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_new_game)) }
                }
                TextButton(onClick = actions.onSettings, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_settings)) }
                TextButton(onClick = actions.onAbout, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_about)) }
            }
            if (engineStarting) Text(stringResource(R.string.engine_starting), style = MaterialTheme.typography.bodySmall)
        }
    }

    if (choosing) {
        NewGameDialog(onStart = { side, difficulty -> choosing = false; actions.onNewGame(side, difficulty) }, onDismiss = { choosing = false })
    }
}

@Composable
fun NewGameDialog(onStart: (SideChoice, Difficulty) -> Unit, onDismiss: () -> Unit) {
    var side by remember { mutableStateOf(SideChoice.WHITE) }
    var difficulty by remember { mutableStateOf(Difficulty.GENTLE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_game_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.new_game_side), style = MaterialTheme.typography.titleSmall)
                SideChoice.entries.forEach { choice ->
                    Choice(selected = side == choice, label = stringResource(sideLabel(choice)), onSelect = { side = choice })
                }
                Text(stringResource(R.string.new_game_opponent), style = MaterialTheme.typography.titleSmall)
                Difficulty.entries.forEach { level ->
                    Choice(
                        selected = difficulty == level,
                        label = stringResource(difficultyLabel(level)),
                        description = stringResource(difficultyDescription(level)),
                        onSelect = { difficulty = level },
                    )
                }
                Text(stringResource(R.string.new_game_untimed), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = { onStart(side, difficulty) }) { Text(stringResource(R.string.new_game_start)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun Choice(selected: Boolean, label: String, onSelect: () -> Unit, description: String? = null) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect, role = Role.RadioButton).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun sideLabel(choice: SideChoice): Int = when (choice) {
    SideChoice.WHITE -> R.string.side_white
    SideChoice.BLACK -> R.string.side_black
    SideChoice.RANDOM -> R.string.side_random
}

private fun difficultyLabel(level: Difficulty): Int = when (level) {
    Difficulty.GENTLE -> R.string.difficulty_gentle
    Difficulty.STEADY -> R.string.difficulty_steady
    Difficulty.KEEN -> R.string.difficulty_keen
}

private fun difficultyDescription(level: Difficulty): Int = when (level) {
    Difficulty.GENTLE -> R.string.difficulty_gentle_desc
    Difficulty.STEADY -> R.string.difficulty_steady_desc
    Difficulty.KEEN -> R.string.difficulty_keen_desc
}
