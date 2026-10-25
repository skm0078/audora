// File: app/src/main/kotlin/io/github/skm0078/audora/ui/theme/ReadingBand.kt
// Purpose: The band behind the sentence being read, strong enough to find at a glance on any paper and theme.

package io.github.skm0078.audora.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * The accent mixed into [paper]. primaryContainer was the band before, and at 1.3:1 against white paper it was hard
 * to find mid-page (release audit P13); this share gives about 1.8:1 against the paper in the lamp palette and in
 * wallpaper colours alike, while black ink on it keeps 10:1 or more, and light ink on a dark page about 6:1.
 */
fun ColorScheme.readingBand(paper: Color): Color = lerp(paper, primary, READING_BAND_SHARE)

private const val READING_BAND_SHARE = 0.4f
