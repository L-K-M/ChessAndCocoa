// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val CocoaColors = lightColorScheme(
    primary = Cocoa,
    onPrimary = CreamSurface,
    primaryContainer = CreamVariant,
    onPrimaryContainer = Ink,
    secondary = Teal,
    onSecondary = CreamSurface,
    tertiary = Plum,
    background = Cream,
    onBackground = Ink,
    surface = CreamSurface,
    onSurface = Ink,
    surfaceVariant = CreamVariant,
    onSurfaceVariant = InkMuted,
    error = Terracotta,
    outline = CocoaLight,
)

/** The single warm, light theme. Readability wins over atmosphere. */
@Composable
fun CocoaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CocoaColors, content = content)
}
