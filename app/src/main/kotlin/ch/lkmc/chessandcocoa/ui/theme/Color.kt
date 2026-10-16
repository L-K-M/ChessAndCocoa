// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.ui.theme

import androidx.compose.ui.graphics.Color

// All colour lives here; screens never use ad-hoc Color(0x…) values.
// Cocoa and cream tones with muted accents (concept Section 4.5). No colour
// carries meaning alone: every highlight also has a shape or a label.

val Cream = Color(0xFFFBF5EC)
val CreamSurface = Color(0xFFFFFCF7)
val CreamVariant = Color(0xFFF1E6D6)
val Cocoa = Color(0xFF5E4030)
val CocoaLight = Color(0xFF8A6550)
val MilkChocolate = Color(0xFFA47551)
val Ink = Color(0xFF2B211B)
val InkMuted = Color(0xFF5C4D43)

// Accents: teal for guidance, honey for "last move", plum for threats,
// terracotta for serious news (never flashing, never the only signal).
val Teal = Color(0xFF2F6F6A)
val Honey = Color(0xFFE2B85A)
val Plum = Color(0xFF7A4A6B)
val Terracotta = Color(0xFFA9533D)

// Board palettes.
val BoardLight = Color(0xFFEEDDC2)
val BoardDark = Color(0xFFB2896A)
val BoardLightContrast = Color(0xFFFFFFFF)
val BoardDarkContrast = Color(0xFF7D8C99)
