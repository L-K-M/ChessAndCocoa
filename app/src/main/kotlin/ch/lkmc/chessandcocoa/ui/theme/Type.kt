// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ch.lkmc.chessandcocoa.R

// Two bundled faces (media-sources/fonts, tools/fonts/build_fonts.py):
// Fraunces, a soft old-style serif, gives headings their warmth; Atkinson
// Hyperlegible Next, drawn for low-vision readers, keeps body text and
// notation unambiguous (Il1, O0, b6) for beginners.

private val Fraunces = FontFamily(
    Font(R.font.fraunces_medium, FontWeight.Medium),
    Font(R.font.fraunces_semibold, FontWeight.SemiBold),
)

private val Atkinson = FontFamily(
    Font(R.font.atkinson_regular, FontWeight.Normal),
    Font(R.font.atkinson_medium, FontWeight.Medium),
    Font(R.font.atkinson_semibold, FontWeight.SemiBold),
)

/** Tabular figures, so move numbers line up in the move list. */
const val TABULAR_FIGURES = "tnum"

private fun heading(size: Int, line: Int, weight: FontWeight = FontWeight.SemiBold) = TextStyle(
    fontFamily = Fraunces,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = (-0.01).em,
)

private fun text(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: Double = 0.0) = TextStyle(
    fontFamily = Atkinson,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
)

internal val CocoaTypography = Typography(
    displayLarge = heading(52, 60, FontWeight.Medium),
    displayMedium = heading(42, 50, FontWeight.Medium),
    displaySmall = heading(34, 42, FontWeight.Medium),
    headlineLarge = heading(30, 38),
    headlineMedium = heading(26, 34),
    headlineSmall = heading(23, 30),
    titleLarge = heading(21, 28),
    titleMedium = text(17, 24, FontWeight.SemiBold),
    titleSmall = text(15, 20, FontWeight.SemiBold),
    // Coaching text is read, not skimmed: a size up from Material's default.
    bodyLarge = text(17, 26),
    bodyMedium = text(15, 22),
    bodySmall = text(13, 19),
    labelLarge = text(15, 20, FontWeight.SemiBold, 0.005),
    labelMedium = text(13, 18, FontWeight.SemiBold, 0.01),
    labelSmall = text(12, 16, FontWeight.Medium, 0.02),
)
