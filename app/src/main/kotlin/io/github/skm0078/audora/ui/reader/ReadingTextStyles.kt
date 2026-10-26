// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReadingTextStyles.kt
// Purpose: The type for book text (text view, Focus view, the player's sentence): the phone's serif, airy lines,
//          or the listener's own font (CF-5 adds two for dyslexic readers), size and spacing in the text view.

package io.github.skm0078.audora.ui.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em

/**
 * Serif reads as a book; Android draws it with Noto Serif, and Hindi and Bengali with their Noto faces. Line height
 * leaves room for Devanagari and Bengali marks above and below the line, so they never clip (item 39).
 */
internal object ReadingTextStyles {
    @Composable
    fun body(): TextStyle = MaterialTheme.typography.bodyLarge.copy(
        fontFamily = FontFamily.Serif,
        lineHeight = BODY_LINE_HEIGHT.em,
    )

    @Composable
    fun focus(): TextStyle = MaterialTheme.typography.titleLarge.copy(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        lineHeight = LARGE_LINE_HEIGHT.em,
    )

    /** The sentence being read, as large as the player can show it. */
    @Composable
    fun spoken(): TextStyle = MaterialTheme.typography.headlineSmall.copy(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        lineHeight = LARGE_LINE_HEIGHT.em,
    )

    /**
     * The text view's type for [look]: body or Focus view size, in the listener's typeface, size and spacing
     * (plan item 15).
     */
    @Composable
    fun forLook(look: ReadingLook): TextStyle {
        val base = if (look.focus) focus() else body()
        val lineHeight = if (look.focus) LARGE_LINE_HEIGHT else BODY_LINE_HEIGHT
        return base.copy(
            fontFamily = ReadingFonts.familyOf(look.font),
            fontSize = base.fontSize * look.textScale,
            lineHeight = (lineHeight * look.spacing.factor).em,
        )
    }

    private const val BODY_LINE_HEIGHT = 1.6f
    private const val LARGE_LINE_HEIGHT = 1.45f
}
