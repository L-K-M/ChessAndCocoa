// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.ui.theme

import androidx.compose.ui.unit.dp

/** The spacing scale: every gap and margin in the app is one of these steps. */
object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp

    /** Side margin of screens on phones. */
    val gutter = 20.dp

    /** Readable line length for running text (about 60 characters at body size). */
    val readingWidth = 560.dp

    /** Minimum touch target for ordinary controls. */
    val touchTarget = 48.dp
}
