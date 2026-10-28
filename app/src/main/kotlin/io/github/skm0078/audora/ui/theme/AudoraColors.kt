// File: app/src/main/kotlin/io/github/skm0078/audora/ui/theme/AudoraColors.kt
// Purpose: Audora's own colours, used when wallpaper colours are off: forest green by day, the reading lamp by
//          night, and the true-black dark variant.

@file:Suppress("MagicNumber") // A palette is its literal colour values.

package io.github.skm0078.audora.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Daylight in forest green (maintainer's pick, 2026-10-28): a pale sage page instead of the old cream, deep forest
// green for the accent (6.4:1 under white text), and the lamp's amber kept as the tertiary colour.
internal val LampLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF2E6B3F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC6E9CC),
    onPrimaryContainer = Color(0xFF0A2913),
    inversePrimary = Color(0xFF9BD3A6),
    secondary = Color(0xFF52634F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3E8CE),
    onSecondaryContainer = Color(0xFF102010),
    tertiary = Color(0xFF8A5A12),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB3),
    onTertiaryContainer = Color(0xFF2B1700),
    background = Color(0xFFEEF4EA),
    onBackground = Color(0xFF171D17),
    surface = Color(0xFFEEF4EA),
    onSurface = Color(0xFF171D17),
    surfaceVariant = Color(0xFFDCE6D7),
    onSurfaceVariant = Color(0xFF414A40),
    surfaceTint = Color(0xFF2E6B3F),
    inverseSurface = Color(0xFF2C322B),
    inverseOnSurface = Color(0xFFEDF2E9),
    outline = Color(0xFF717A6F),
    outlineVariant = Color(0xFFC1CABD),
    surfaceBright = Color(0xFFF6FAF3),
    surfaceDim = Color(0xFFD8E0D4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F9F1),
    surfaceContainer = Color(0xFFE8EFE4),
    surfaceContainerHigh = Color(0xFFE2EADE),
    surfaceContainerHighest = Color(0xFFDCE5D8),
)

// The same lamp at night: a light amber on deep brown, so night reading is not a blue-white glare.
internal val LampDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFF2A65A),
    onPrimary = Color(0xFF2A1A0A),
    primaryContainer = Color(0xFF6B3A0E),
    onPrimaryContainer = Color(0xFFFFDDBB),
    inversePrimary = Color(0xFFA8520E),
    secondary = Color(0xFF8FCFC7),
    onSecondary = Color(0xFF003733),
    secondaryContainer = Color(0xFF1E4D48),
    onSecondaryContainer = Color(0xFFCFE6E2),
    tertiary = Color(0xFFCFBCF2),
    onTertiary = Color(0xFF2E1A5A),
    tertiaryContainer = Color(0xFF45307A),
    onTertiaryContainer = Color(0xFFE9DDFB),
    background = Color(0xFF15110D),
    onBackground = Color(0xFFF3EADF),
    surface = Color(0xFF15110D),
    onSurface = Color(0xFFF3EADF),
    surfaceVariant = Color(0xFF3A322A),
    onSurfaceVariant = Color(0xFFCDBFAE),
    surfaceTint = Color(0xFFF2A65A),
    inverseSurface = Color(0xFFF3EADF),
    inverseOnSurface = Color(0xFF2A221A),
    outline = Color(0xFF978A7C),
    outlineVariant = Color(0xFF3A322A),
    surfaceBright = Color(0xFF3A322A),
    surfaceDim = Color(0xFF15110D),
    surfaceContainerLowest = Color(0xFF0E0B08),
    surfaceContainerLow = Color(0xFF1A1511),
    surfaceContainer = Color(0xFF1E1914),
    surfaceContainerHigh = Color(0xFF28221C),
    surfaceContainerHighest = Color(0xFF332C25),
)

/**
 * True black for OLED screens: the background and the lowest surfaces turn off their pixels, and each container
 * steps one shade darker so cards still stand apart from the page.
 */
internal fun ColorScheme.trueBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = surfaceContainerLowest,
    surfaceContainer = surfaceContainerLow,
    surfaceContainerHigh = surfaceContainer,
    surfaceContainerHighest = surfaceContainerHigh,
)

/** Collection markers (the tablet sidebar): mid tones that read on light and dark surfaces alike, never alone. */
internal val ShelfDotColours: List<Color> = listOf(
    Color(0xFF9C5A33),
    Color(0xFF2F7A5F),
    Color(0xFF3F5FA8),
    Color(0xFF7A4C9A),
    Color(0xFFB0832A),
    Color(0xFF3E8A99),
)
