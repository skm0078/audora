// File: app/src/main/kotlin/io/github/skm0078/audora/privacy/LockSaverColors.kt
// Purpose: The lock screen saver's night palette, the same in the light and dark themes.

@file:Suppress("MagicNumber") // A palette is its literal colour values.

package io.github.skm0078.audora.privacy

import androidx.compose.ui.graphics.Color

/**
 * The saver's own night colours, the same in light and dark themes: a screen saver is looked at in the dark, and
 * deep forest green keeps it the app's own. Ink on Night is 15:1; the rings and bars are decoration only.
 */
internal object LockSaverColors {
    val Night = Color(0xFF0B140E)
    val Glow = Color(0xFF1C3324)
    val Ring = Color(0xFF6FBF85)
    val Bars = Color(0xFFA8DDB4)
    val Ink = Color(0xFFE6F2E8)
}
