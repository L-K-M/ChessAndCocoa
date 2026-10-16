// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ch.lkmc.chessandcocoa.BuildConfig
import ch.lkmc.chessandcocoa.R

/** Legal documents bundled into the app's assets by the build. */
enum class LegalDocument(val asset: String) {
    GPL("legal/LICENSE.txt"),
    NOTICES("legal/THIRD_PARTY_NOTICES.md"),
}

/** The release-gate notices (concept Section 14.4): license, source pointer, credits, ODbL acknowledgment. */
@Composable
fun AboutScreen(onOpen: (LegalDocument) -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.about_license))
            SelectionContainer { Text(stringResource(R.string.about_source, stringResource(R.string.source_url))) }
            Text(stringResource(R.string.about_stockfish))
            Text(stringResource(R.string.about_leela))
            Text(stringResource(R.string.about_pieces))
            Text(stringResource(R.string.about_libraries))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onOpen(LegalDocument.GPL) }) { Text(stringResource(R.string.about_view_license)) }
                OutlinedButton(onClick = { onOpen(LegalDocument.NOTICES) }) { Text(stringResource(R.string.about_view_notices)) }
            }
        }
    }
}

@Composable
fun LegalTextScreen(document: LegalDocument, onBack: () -> Unit) {
    val context = LocalContext.current
    val text = remember(document) { context.assets.open(document.asset).bufferedReader().use { it.readText() } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (document == LegalDocument.GPL) R.string.about_view_license else R.string.about_view_notices)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        SelectionContainer(Modifier.padding(padding)) {
            Text(
                text,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            )
        }
    }
}
