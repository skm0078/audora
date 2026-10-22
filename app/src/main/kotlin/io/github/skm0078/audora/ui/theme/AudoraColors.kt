// File: app/src/main/kotlin/io/github/skm0078/audora/ui/theme/AudoraColors.kt
// Purpose: Audora's own "reading lamp" colours, used when wallpaper colours are off, and the true-black dark variant.

@file:Suppress("MagicNumber") // A palette is its literal colour values.

package io.github.skm0078.audora.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Warm paper and ink for daylight; the accent is a lamp amber dark enough for white text on it (4.5:1).
internal val LampLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFFA8520E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF7DFC4),
    onPrimaryContainer = Color(0xFF3A1E05),
    inversePrimary = Color(0xFFF2A65A),
    secondary = Color(0xFF2F6B66),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFE6E2),
    onSecondaryContainer = Color(0xFF0E2A27),
    tertiary = Color(0xFF5B3F8C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE9DDFB),
    onTertiaryContainer = Color(0xFF1F0B45),
    background = Color(0xFFF8F2E8),
    onBackground = Color(0xFF24190F),
    surface = Color(0xFFF8F2E8),
    onSurface = Color(0xFF24190F),
    surfaceVariant = Color(0xFFF0E6D6),
    onSurfaceVariant = Color(0xFF5E4F41),
    surfaceTint = Color(0xFFA8520E),
    inverseSurface = Color(0xFF3A2A1C),
    inverseOnSurface = Color(0xFFFFF6EA),
    outline = Color(0xFF8C7B69),
    outlineVariant = Color(0xFFD6C6AF),
    surfaceBright = Color(0xFFFFFCF7),
    surfaceDim = Color(0xFFE6DACA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFCF7),
    surfaceContainer = Color(0xFFF4ECDF),
    surfaceContainerHigh = Color(0xFFF0E6D6),
    surfaceContainerHighest = Color(0xFFEADCC8),
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
