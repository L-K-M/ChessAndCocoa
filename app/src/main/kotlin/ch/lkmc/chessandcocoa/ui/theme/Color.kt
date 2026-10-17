// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// All colour lives here; screens never use ad-hoc Color(0x…) values.
// "Evening study": cream paper and cocoa ink, with three restrained accents
// (concept Section 4.5). Every text pair meets WCAG AA in both themes, and no
// colour carries meaning alone: each highlight also has a shape or a label.
//
//   sage        guidance: selection, legal moves, "Show me why", encouraging notes
//   honey       the last move and hints
//   terracotta  check and serious news (never flashing)
//   mulberry    teaching marks: targets and threats

// Daylight: warm paper, cocoa ink.
internal object Daylight {
    val paper = Color(0xFFFBF6EF)
    val paperBright = Color(0xFFFFFBF6)
    val paperDim = Color(0xFFE2D6C6)
    val containerLowest = Color(0xFFFFFFFF)
    val containerLow = Color(0xFFF6EFE5)
    val container = Color(0xFFF1E8DB)
    val containerHigh = Color(0xFFEBE1D3)
    val containerHighest = Color(0xFFE5D9C8)

    // Its own value: Material's contentColorFor matches roles by colour, so a
    // variant equal to a container role would give that container muted text.
    val variant = Color(0xFFE8DCCB)
    val ink = Color(0xFF2A1F19)
    val inkMuted = Color(0xFF5E4F44)
    val outline = Color(0xFF8A7869)
    val outlineSoft = Color(0xFFDCCFBF)

    val cocoa = Color(0xFF5C3D2E)
    val onCocoa = Color(0xFFFFF8F1)
    val latte = Color(0xFFF0DCCA)
    val onLatte = Color(0xFF3B2216)
    val sage = Color(0xFF4A6A4C)
    val sageContainer = Color(0xFFD6E5CF)
    val onSageContainer = Color(0xFF1C301E)
    val honey = Color(0xFF7A5A12)
    val honeyContainer = Color(0xFFF6E2B3)
    val onHoneyContainer = Color(0xFF3A2A00)
    val terracotta = Color(0xFFA3452D)
    val terracottaContainer = Color(0xFFF7DBD1)
    val onTerracottaContainer = Color(0xFF4A150A)
}

// Evening: deep cocoa surfaces, cream text, softened accents.
internal object Evening {
    val paper = Color(0xFF17110E)
    val paperBright = Color(0xFF3E342D)
    val containerLowest = Color(0xFF120D0A)
    val containerLow = Color(0xFF1F1814)
    val container = Color(0xFF251D18)
    val containerHigh = Color(0xFF2F2620)
    val containerHighest = Color(0xFF3A3029)
    val variant = Color(0xFF3E332B)
    val ink = Color(0xFFF0E4D7)
    val inkMuted = Color(0xFFD3C2B2)
    val outline = Color(0xFF9C8A7B)
    val outlineSoft = Color(0xFF4A3D34)

    val latte = Color(0xFFE6BEA2)
    val onLatte = Color(0xFF3F2517)
    val cocoaContainer = Color(0xFF5A3B2B)
    val onCocoaContainer = Color(0xFFFADCC8)
    val sage = Color(0xFFB0CBA6)
    val onSage = Color(0xFF213622)
    val sageContainer = Color(0xFF354B36)
    val onSageContainer = Color(0xFFD2E8C8)
    val honey = Color(0xFFE8C46E)
    val onHoney = Color(0xFF3E2E00)
    val honeyContainer = Color(0xFF574000)
    val onHoneyContainer = Color(0xFFFFE3A1)
    val terracotta = Color(0xFFF0A48D)
    val onTerracotta = Color(0xFF561E0F)
    val terracottaContainer = Color(0xFF76301D)
    val onTerracottaContainer = Color(0xFFFFDBD0)
}

// Fixed accents keep their tone in both themes (Material 3 "fixed" roles).
internal object Fixed {
    val latte = Color(0xFFF0DCCA)
    val latteDim = Color(0xFFE3C4AC)
    val onLatte = Color(0xFF2E1A10)
    val onLatteVariant = Color(0xFF5C3D2E)
    val sage = Color(0xFFD6E5CF)
    val sageDim = Color(0xFFBBCAB4)
    val onSage = Color(0xFF102012)
    val onSageVariant = Color(0xFF374F38)
    val honey = Color(0xFFF6E2B3)
    val honeyDim = Color(0xFFE5C77F)
    val onHoney = Color(0xFF261A00)
    val onHoneyVariant = Color(0xFF5C4300)
    val scrim = Color(0xFF0E0907)
}

/** Board squares, frame and coordinates for one look. */
@Immutable
data class BoardPalette(
    val light: Color,
    val dark: Color,
    val frame: Color,
    /** Coordinates on a light square; [light] is used on dark squares. */
    val coordinateOnLight: Color,
    val coordinateOnDark: Color,
)

/** Board marks. Each is drawn with its own shape; [halo] keeps shapes visible on either square. */
@Immutable
data class MarkColors(
    val guide: Color,
    val lastMove: Color,
    val lastMoveEdge: Color,
    val check: Color,
    val target: Color,
    val attacker: Color,
    val defender: Color,
    val threat: Color,
    val moved: Color,
    val arrow: Color,
    val halo: Color,
)

/** Colours Material 3 has no role for: the board, its marks and the turn indicator. */
@Immutable
data class CocoaColors(
    val board: BoardPalette,
    val highContrastBoard: BoardPalette,
    val marks: MarkColors,
    /** Frame of the board while it shows "Show me why" instead of the live game. */
    val evidenceFrame: Color,
    /** The turn indicator's disc for each side. */
    val turnWhite: Color = Color(0xFFFFFFFF),
    val turnBlack: Color = Color(0xFF1F1A17),
)

private val CocoaBoard = BoardPalette(
    light = Color(0xFFEFDFC5),
    dark = Color(0xFFB4886A),
    frame = Color(0xFF4B3226),
    coordinateOnLight = Color(0xFF8C6449),
    coordinateOnDark = Color(0xFFF6EAD7),
)

// Slightly deeper squares so the board does not glare in the dark theme.
private val EveningBoard = BoardPalette(
    light = Color(0xFFDCC6A6),
    dark = Color(0xFF9C7357),
    frame = Color(0xFF3D2C22),
    coordinateOnLight = Color(0xFF7A563E),
    coordinateOnDark = Color(0xFFEBDCC6),
)

private val HighContrastBoard = BoardPalette(
    light = Color(0xFFFFFFFF),
    dark = Color(0xFF7D8C99),
    frame = Color(0xFF2A2F33),
    coordinateOnLight = Color(0xFF4F5B66),
    coordinateOnDark = Color(0xFFFFFFFF),
)

private val Marks = MarkColors(
    guide = Color(0xFF2E5E3B),
    lastMove = Color(0xFFE2B85A),
    lastMoveEdge = Color(0xFF6B4A1A),
    check = Color(0xFFA3452D),
    target = Color(0xFF7A3A63),
    attacker = Color(0xFF2E5E3B),
    defender = Color(0xFF4B3226),
    threat = Color(0xFF7A3A63),
    moved = Color(0xFFC9962E),
    arrow = Color(0xFF2E5E3B),
    halo = Color(0xFFFFF8EC),
)

internal val DaylightCocoaColors = CocoaColors(
    board = CocoaBoard,
    highContrastBoard = HighContrastBoard,
    marks = Marks,
    evidenceFrame = Daylight.sage,
)

internal val EveningCocoaColors = CocoaColors(
    board = EveningBoard,
    highContrastBoard = HighContrastBoard,
    marks = Marks,
    evidenceFrame = Color(0xFF6E9470),
)
