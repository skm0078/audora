// File: app/src/main/kotlin/io/github/skm0078/audora/ui/share/CardFormat.kt
// Purpose: A share card's sizes (square, story, post) and backgrounds, and the quote's own type (ADR
//          2026-10-27-share-cards).

@file:Suppress("MagicNumber") // Platform picture sizes and a palette are their literal values.

package io.github.skm0078.audora.ui.share

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.playback.R as PlaybackR
import io.github.skm0078.audora.ui.reader.PageTone

/**
 * The picture's size in pixels. 1080 wide is what Instagram, WhatsApp Status and Facebook show at full quality; every
 * card is laid out on a [DESIGN_WIDTH_DP]-wide canvas, so the same design fills each size.
 */
enum class CardShape(val widthPx: Int, val heightPx: Int, @StringRes val label: Int) {
    SQUARE(1080, 1080, R.string.share_card_square),
    STORY(1080, 1920, R.string.share_card_story),
    POST(1080, 1350, R.string.share_card_post),
    ;

    companion object {
        /** Cards are designed at this width; at 1080 px one card dp is 3 px. */
        const val DESIGN_WIDTH_DP = 360f
    }
}

/**
 * A card's background and the colours on it. [ink] is the text, [quiet] the credit and the mark, [accent] the quote
 * mark. Cover and picture backgrounds sit under a [scrim] so white text stays readable (4.5:1) on any photo.
 */
enum class CardLook(
    @StringRes val label: Int,
    val brush: Brush,
    val ink: Color,
    val quiet: Color,
    val accent: Color,
    val scrim: Color = Color.Transparent,
) {
    PAPER(R.string.share_card_look_light, solid(0xFFFBF8F3), Color(0xFF1B1B21), Color(0xFF55545C), Color(0xFFA8520E)),
    SEPIA(R.string.share_card_look_sepia, solid(0xFFF3E7CF), Color(0xFF3B2F22), Color(0xFF6B5843), Color(0xFF8A5A2B)),
    NIGHT(R.string.share_card_look_dark, solid(0xFF16161C), Color(0xFFF2EFF7), Color(0xFFB9B7C2), Color(0xFFF2A65A)),
    DUSK(
        R.string.share_card_look_dusk,
        Brush.verticalGradient(listOf(Color(0xFF3B4A6B), Color(0xFF1F2537))),
        Color(0xFFFFFFFF),
        Color(0xFFDAE2FF),
        Color(0xFFB8C4EE),
    ),
    COVER(
        R.string.share_card_look_cover,
        solid(0xFF1F2537),
        Color(0xFFFFFFFF),
        Color(0xFFE6E6EE),
        Color(0xFFFFD66B),
        scrim = Color(0x8C000000),
    ),
    PICTURE(
        R.string.share_card_look_picture,
        solid(0xFF1F2537),
        Color(0xFFFFFFFF),
        Color(0xFFE6E6EE),
        Color(0xFFFFD66B),
        scrim = Color(0x80000000),
    ),
    ;

    /** Drawn over a photo: shared as JPEG (a PNG of a photo is several MB); flat looks stay crisp PNG. */
    val isPhoto: Boolean get() = this == COVER || this == PICTURE

    companion object {
        /**
         * The look matching the reader's page colours, so lines leave looking as they were read: sepia pages make a
         * sepia card, dark pages a night one, and the book's own colours follow the app (paper, or night when dark).
         */
        fun forReading(tone: PageTone, darkApp: Boolean): CardLook = when (tone) {
            PageTone.SEPIA -> SEPIA
            PageTone.DARK -> NIGHT
            PageTone.ORIGINAL -> if (darkApp) NIGHT else PAPER
        }
    }
}

private fun solid(argb: Long) = SolidColor(Color(argb))

/**
 * Tiro (Tiro Typeworks, SIL Open Font License 1.1): bookish serifs drawn for Devanagari and Bengali with matching
 * Latin, trimmed to their script and Latin (about 0.2 MB for both). English quotes use Tiro Devanagari Hindi's Latin.
 * The files live in :playback, whose quote video draws its words with the same fonts (QuoteVideoFonts).
 */
internal object QuoteFonts {
    private val devanagari = FontFamily(Font(PlaybackR.font.tiro_devanagari_hindi_regular))
    private val bangla = FontFamily(Font(PlaybackR.font.tiro_bangla_regular))

    fun of(script: Lang): FontFamily = if (script == Lang.BN) bangla else devanagari
}
