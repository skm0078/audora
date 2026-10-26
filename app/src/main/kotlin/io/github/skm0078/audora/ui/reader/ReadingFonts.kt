// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReadingFonts.kt
// Purpose: The typeface behind each text view font choice, including the two bundled for dyslexic readers (CF-5).

package io.github.skm0078.audora.ui.reader

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.skm0078.audora.R

/**
 * Atkinson Hyperlegible (Braille Institute) and OpenDyslexic are bundled under the SIL Open Font License 1.1 (texts in
 * assets/licences). Both draw Latin letters only: Android falls back to its Noto faces for Devanagari and Bengali, so a
 * Hindi or Bengali book reads as before in every choice. Bold files back "Bold word starts" and the bold highlight, so
 * neither is a smeared fake bold.
 */
internal object ReadingFonts {
    private val atkinsonHyperlegible = FontFamily(
        Font(R.font.atkinson_hyperlegible_regular, FontWeight.Normal),
        Font(R.font.atkinson_hyperlegible_bold, FontWeight.Bold),
    )

    private val openDyslexic = FontFamily(
        Font(R.font.opendyslexic_regular, FontWeight.Normal),
        Font(R.font.opendyslexic_bold, FontWeight.Bold),
    )

    fun familyOf(font: ReadingFont): FontFamily = when (font) {
        ReadingFont.SERIF -> FontFamily.Serif
        ReadingFont.SANS -> FontFamily.SansSerif
        ReadingFont.ATKINSON_HYPERLEGIBLE -> atkinsonHyperlegible
        ReadingFont.OPEN_DYSLEXIC -> openDyslexic
    }
}
