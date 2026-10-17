// File: app/src/main/kotlin/io/github/skm0078/audora/ui/theme/Appearance.kt
// Purpose: The listener's look choices: wallpaper or Audora colours, light or dark, and true black at night.

package io.github.skm0078.audora.ui.theme

/**
 * @property wallpaperColors Android's colours from the wallpaper (on by default); off uses Audora's lamp palette.
 * @property trueBlack dark screens use pure black behind the text, which keeps OLED pixels off and saves battery.
 */
data class Appearance(
    val wallpaperColors: Boolean = true,
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val trueBlack: Boolean = true,
) {
    fun isDark(systemDark: Boolean): Boolean = when (darkMode) {
        DarkMode.SYSTEM -> systemDark
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }
}

enum class DarkMode { SYSTEM, LIGHT, DARK }
