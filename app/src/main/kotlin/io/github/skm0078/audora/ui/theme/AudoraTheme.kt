// File: app/src/main/kotlin/io/github/skm0078/audora/ui/theme/AudoraTheme.kt
// Purpose: Material 3 theme: wallpaper colours or the lamp palette, light or dark, true black, type and shapes.

package io.github.skm0078.audora.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Every supported phone has wallpaper colours (minSdk 31); with them off Audora uses its own lamp palette. Text uses
 * the phone's own fonts (Noto for Hindi and Bengali on every Android phone), so the theme adds no font files.
 */
@Composable
fun AudoraTheme(
    appearance: Appearance = Appearance(),
    darkTheme: Boolean = appearance.isDark(isSystemInDarkTheme()),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val base = when {
        appearance.wallpaperColors && darkTheme -> dynamicDarkColorScheme(context)
        appearance.wallpaperColors -> dynamicLightColorScheme(context)
        darkTheme -> LampDarkColors
        else -> LampLightColors
    }
    val colorScheme = if (darkTheme && appearance.trueBlack) base.trueBlack() else base
    MaterialTheme(colorScheme = colorScheme, typography = AudoraTypography, shapes = AudoraShapes, content = content)
}

// Material's scale with bolder headings: screen titles carry the hierarchy, so body text can stay calm.
private val AudoraTypography: Typography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AudoraShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
