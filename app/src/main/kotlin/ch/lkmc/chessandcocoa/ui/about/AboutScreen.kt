// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.ui.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ch.lkmc.chessandcocoa.BuildConfig
import ch.lkmc.chessandcocoa.R
import ch.lkmc.chessandcocoa.ui.common.ScreenTopBar
import ch.lkmc.chessandcocoa.ui.common.SectionHeader
import ch.lkmc.chessandcocoa.ui.theme.Space

/** Legal documents bundled into the app's assets by the build. */
enum class LegalDocument(val asset: String) {
    GPL("legal/LICENSE.txt"),
    NOTICES("legal/THIRD_PARTY_NOTICES.md"),
}

/** The release-gate notices (concept Section 14.4): license, source pointer, credits, ODbL acknowledgment. */
@Composable
fun AboutScreen(onOpen: (LegalDocument) -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = { ScreenTopBar(title = stringResource(R.string.about_title), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter),
            verticalArrangement = Arrangement.spacedBy(Space.m),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = Space.readingWidth), verticalArrangement = Arrangement.spacedBy(Space.m)) {
                Row(Modifier.padding(vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.hero_cocoa), contentDescription = null, modifier = Modifier.size(MARK).clip(MaterialTheme.shapes.large))
                    Column(Modifier.padding(start = Space.l)) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.app_descriptor), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                SectionHeader(stringResource(R.string.about_section_license))
                Text(stringResource(R.string.about_license), style = MaterialTheme.typography.bodyMedium)
                SelectionContainer {
                    Text(stringResource(R.string.about_source, stringResource(R.string.source_url)), style = MaterialTheme.typography.bodyMedium)
                }
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
                    Column {
                        DocumentRow(stringResource(R.string.about_view_license)) { onOpen(LegalDocument.GPL) }
                        HorizontalDivider(Modifier.padding(horizontal = Space.l), color = MaterialTheme.colorScheme.outlineVariant)
                        DocumentRow(stringResource(R.string.about_view_notices)) { onOpen(LegalDocument.NOTICES) }
                    }
                }

                SectionHeader(stringResource(R.string.about_section_credits), Modifier.padding(top = Space.s))
                listOf(
                    R.string.about_stockfish,
                    R.string.about_leela,
                    R.string.about_pieces,
                    R.string.about_fonts,
                    R.string.about_icons,
                    R.string.about_libraries,
                ).forEach { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
                Spacer(Modifier.height(Space.l))
            }
        }
    }
}

/** A row that opens a bundled document. */
@Composable
private fun DocumentRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LegalTextScreen(document: LegalDocument, onBack: () -> Unit) {
    val context = LocalContext.current
    val text = remember(document) { context.assets.open(document.asset).bufferedReader().use { it.readText() } }
    Scaffold(
        topBar = { ScreenTopBar(title = stringResource(if (document == LegalDocument.GPL) R.string.about_view_license else R.string.about_view_notices), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        SelectionContainer(Modifier.padding(padding)) {
            Text(
                text,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter, vertical = Space.m),
            )
        }
    }
}

private val MARK = 64.dp
private val ROW_HEIGHT = 56.dp
