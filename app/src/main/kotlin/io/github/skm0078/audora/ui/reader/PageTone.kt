// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/PageTone.kt
// Purpose: Dark and sepia pages (plan item 18): the colour filter over the drawn PDF page, and the matching paper
//          and ink for the text view.
@file:Suppress("MagicNumber") // Colour values and matrix coefficients are the palette itself.

package io.github.skm0078.audora.ui.reader

import androidx.compose.ui.graphics.Color

/**
 * How the page is coloured. [ORIGINAL] draws the PDF as it is; [SEPIA] maps it to brown ink on cream paper, easier
 * on the eyes in warm light; [DARK] turns white paper dark grey and black ink light grey, for reading at night.
 * Pictures are filtered too: a photo turns sepia or negative, which is the price of filtering the whole page.
 */
enum class PageTone(val paper: Color?, val ink: Color?) {
    ORIGINAL(null, null),
    SEPIA(Color(0xFFF4ECD8), Color(0xFF5B4636)),
    DARK(Color(0xFF181818), Color(0xFFE2E2E2)),
    ;

    /**
     * The 4×5 colour matrix (Android's row-major layout, offsets in 0..255) that maps the page's white to [paper] and
     * its black to [ink] through each pixel's luminance; null for [ORIGINAL]. Mid-tones fall in between, so
     * antialiased text keeps its smooth edges.
     */
    fun colorMatrix(): FloatArray? {
        val to = paper ?: return null
        val from = requireNotNull(ink)
        val rows = listOf(from.red to to.red, from.green to to.green, from.blue to to.blue).flatMap { (black, white) ->
            val span = white - black
            listOf(LUMA_R * span, LUMA_G * span, LUMA_B * span, 0f, black * CHANNEL_MAX)
        }
        return (rows + listOf(0f, 0f, 0f, 1f, 0f)).toFloatArray()
    }

    private companion object {
        // Rec. 601 luma weights: how bright each channel looks.
        const val LUMA_R = 0.299f
        const val LUMA_G = 0.587f
        const val LUMA_B = 0.114f
        const val CHANNEL_MAX = 255f
    }
}
