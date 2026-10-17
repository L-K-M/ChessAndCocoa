// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

// Every Material 3 role is set, so no default purple surface, outline or
// container can leak into a component.
private val DaylightScheme = lightColorScheme(
    primary = Daylight.cocoa,
    onPrimary = Daylight.onCocoa,
    primaryContainer = Daylight.latte,
    onPrimaryContainer = Daylight.onLatte,
    inversePrimary = Evening.latte,
    secondary = Daylight.sage,
    onSecondary = Daylight.containerLowest,
    secondaryContainer = Daylight.sageContainer,
    onSecondaryContainer = Daylight.onSageContainer,
    tertiary = Daylight.honey,
    onTertiary = Daylight.containerLowest,
    tertiaryContainer = Daylight.honeyContainer,
    onTertiaryContainer = Daylight.onHoneyContainer,
    background = Daylight.paper,
    onBackground = Daylight.ink,
    surface = Daylight.paper,
    onSurface = Daylight.ink,
    surfaceVariant = Daylight.variant,
    onSurfaceVariant = Daylight.inkMuted,
    surfaceTint = Daylight.cocoa,
    inverseSurface = Evening.containerHighest,
    inverseOnSurface = Evening.ink,
    error = Daylight.terracotta,
    onError = Daylight.containerLowest,
    errorContainer = Daylight.terracottaContainer,
    onErrorContainer = Daylight.onTerracottaContainer,
    outline = Daylight.outline,
    outlineVariant = Daylight.outlineSoft,
    scrim = Fixed.scrim,
    surfaceBright = Daylight.paperBright,
    surfaceContainer = Daylight.container,
    surfaceContainerHigh = Daylight.containerHigh,
    surfaceContainerHighest = Daylight.containerHighest,
    surfaceContainerLow = Daylight.containerLow,
    surfaceContainerLowest = Daylight.containerLowest,
    surfaceDim = Daylight.paperDim,
    primaryFixed = Fixed.latte,
    primaryFixedDim = Fixed.latteDim,
    onPrimaryFixed = Fixed.onLatte,
    onPrimaryFixedVariant = Fixed.onLatteVariant,
    secondaryFixed = Fixed.sage,
    secondaryFixedDim = Fixed.sageDim,
    onSecondaryFixed = Fixed.onSage,
    onSecondaryFixedVariant = Fixed.onSageVariant,
    tertiaryFixed = Fixed.honey,
    tertiaryFixedDim = Fixed.honeyDim,
    onTertiaryFixed = Fixed.onHoney,
    onTertiaryFixedVariant = Fixed.onHoneyVariant,
)

private val EveningScheme = darkColorScheme(
    primary = Evening.latte,
    onPrimary = Evening.onLatte,
    primaryContainer = Evening.cocoaContainer,
    onPrimaryContainer = Evening.onCocoaContainer,
    inversePrimary = Daylight.cocoa,
    secondary = Evening.sage,
    onSecondary = Evening.onSage,
    secondaryContainer = Evening.sageContainer,
    onSecondaryContainer = Evening.onSageContainer,
    tertiary = Evening.honey,
    onTertiary = Evening.onHoney,
    tertiaryContainer = Evening.honeyContainer,
    onTertiaryContainer = Evening.onHoneyContainer,
    background = Evening.paper,
    onBackground = Evening.ink,
    surface = Evening.paper,
    onSurface = Evening.ink,
    surfaceVariant = Evening.variant,
    onSurfaceVariant = Evening.inkMuted,
    surfaceTint = Evening.latte,
    inverseSurface = Daylight.container,
    inverseOnSurface = Daylight.ink,
    error = Evening.terracotta,
    onError = Evening.onTerracotta,
    errorContainer = Evening.terracottaContainer,
    onErrorContainer = Evening.onTerracottaContainer,
    outline = Evening.outline,
    outlineVariant = Evening.outlineSoft,
    scrim = Fixed.scrim,
    surfaceBright = Evening.paperBright,
    surfaceContainer = Evening.container,
    surfaceContainerHigh = Evening.containerHigh,
    surfaceContainerHighest = Evening.containerHighest,
    surfaceContainerLow = Evening.containerLow,
    surfaceContainerLowest = Evening.containerLowest,
    surfaceDim = Evening.paper,
    primaryFixed = Fixed.latte,
    primaryFixedDim = Fixed.latteDim,
    onPrimaryFixed = Fixed.onLatte,
    onPrimaryFixedVariant = Fixed.onLatteVariant,
    secondaryFixed = Fixed.sage,
    secondaryFixedDim = Fixed.sageDim,
    onSecondaryFixed = Fixed.onSage,
    onSecondaryFixedVariant = Fixed.onSageVariant,
    tertiaryFixed = Fixed.honey,
    tertiaryFixedDim = Fixed.honeyDim,
    onTertiaryFixed = Fixed.onHoney,
    onTertiaryFixedVariant = Fixed.onHoneyVariant,
)

// Gently rounded, never pill-shaped panels: buttons stay fully rounded by Material's own default.
private val CocoaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Light or evening colours; the app follows the system setting. */
enum class Appearance { DAYLIGHT, EVENING }

private val LocalCocoaColors = staticCompositionLocalOf { DaylightCocoaColors }

/** Theme access for the colours Material 3 has no role for. */
object CocoaTheme {
    val colors: CocoaColors
        @Composable @ReadOnlyComposable get() = LocalCocoaColors.current
}

/** Warm cream by day, deep cocoa in the evening. Readability wins over atmosphere. */
@Composable
fun CocoaTheme(
    appearance: Appearance = if (isSystemInDarkTheme()) Appearance.EVENING else Appearance.DAYLIGHT,
    content: @Composable () -> Unit,
) {
    val scheme = if (appearance == Appearance.EVENING) EveningScheme else DaylightScheme
    val colors = if (appearance == Appearance.EVENING) EveningCocoaColors else DaylightCocoaColors
    CompositionLocalProvider(LocalCocoaColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = CocoaTypography, shapes = CocoaShapes, content = content)
    }
}
