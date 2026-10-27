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
 * Every supported phone has wallpaper colours (minSdk 31); with them off Audora uses its own lamp palette. The app's
 * text is set in [AppFont] (Anek, one design for English, Hindi and Bengali); book text keeps the reader's own fonts.
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

// Material's scale in the app font with bolder headings: screen titles carry the hierarchy, so body text can stay
// calm. Headings use 600, the heaviest weight bundled, so nothing is drawn as a synthesised bold.
internal val AudoraTypography: Typography = Typography().let { base ->
    val app = AppFont.family
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = app),
        displayMedium = base.displayMedium.copy(fontFamily = app),
        displaySmall = base.displaySmall.copy(fontFamily = app, fontWeight = FontWeight.SemiBold),
        headlineLarge = base.headlineLarge.copy(
            fontFamily = app,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.5).sp,
        ),
        headlineMedium = base.headlineMedium.copy(fontFamily = app, fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontFamily = app, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontFamily = app, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = app, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontFamily = app, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontFamily = app),
        bodyMedium = base.bodyMedium.copy(fontFamily = app),
        bodySmall = base.bodySmall.copy(fontFamily = app),
        labelLarge = base.labelLarge.copy(fontFamily = app, fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontFamily = app),
        labelSmall = base.labelSmall.copy(fontFamily = app),
    )
}

private val AudoraShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
