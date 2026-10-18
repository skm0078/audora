// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/layout/GlyphStyles.kt
// Purpose: Glyph size and boldness per offset of a page's text, from glyph boxes and font names.

package io.github.skm0078.audora.pdf.layout

import io.github.skm0078.audora.core.config.LayoutConfig
import io.github.skm0078.audora.pdf.extract.PageText
import kotlin.math.roundToInt

/**
 * A glyph's size is PDFium's font size, which is the `Tf` operand: it ignores the text matrix, so PDFs that set
 * `1 Tf` and scale with `Tm` (Elsevier's) report 1 pt for every glyph. Below [LayoutConfig.minFontSize] the glyph
 * box height stands in. Box heights alone are no good: Devanagari glyphs mix loose and tight boxes (7.5 to 18.5 pt
 * on one 16 pt page).
 */
internal class GlyphStyles(page: PageText, private val config: LayoutConfig) {
    private val sizes = FloatArray(page.text.length)
    private val bold = BooleanArray(page.text.length)

    /** The most common glyph size by glyph count (to the nearest half point); 0 when nothing is sized. */
    val bodySize: Float

    init {
        val boldFonts = HashMap<String, Boolean>()
        for (run in page.fontRuns) {
            val isBold = boldFonts.getOrPut(run.fontName) { isBoldFont(run.fontName, config.boldFontMarkers) }
            for (offset in run.start until run.end) {
                bold[offset] = isBold
                sizes[offset] = run.fontSize
            }
        }
        for (offset in page.text.indices) {
            sizes[offset] = when {
                page.text[offset].isWhitespace() -> 0f
                sizes[offset] >= config.minFontSize -> sizes[offset]
                else -> page.boxAt(offset)?.height?.coerceAtLeast(0f) ?: 0f
            }
        }
        bodySize = dominantSize(listOf(page.text.indices))
    }

    /** Glyph size at [offset]; 0 for whitespace and unsized glyphs. */
    fun sizeAt(offset: Int): Float = sizes[offset]

    fun isBoldAt(offset: Int): Boolean = bold[offset]

    /** The most common size over [ranges] by glyph count, to the nearest half point; 0 when nothing is sized. */
    fun dominantSize(ranges: List<IntRange>): Float {
        val countsByHalfPoint = HashMap<Int, Int>()
        ranges.asSequence().flatMap { it.asSequence() }.map { sizes[it] }.filter { it > 0f }.forEach { size ->
            val key = (size * HALF_POINTS).roundToInt()
            countsByHalfPoint[key] = (countsByHalfPoint[key] ?: 0) + 1
        }
        return countsByHalfPoint.maxByOrNull { it.value }?.key?.let { it / HALF_POINTS } ?: 0f
    }

    /** More than half the glyphs in [ranges] use a bold font. */
    fun isMostlyBold(ranges: List<IntRange>): Boolean {
        val glyphs = ranges.sumOf { it.last - it.first + 1 }
        val boldGlyphs = ranges.sumOf { range -> range.count { bold[it] } }
        return boldGlyphs * 2 > glyphs
    }

    companion object {
        private const val HALF_POINTS = 2f

        /** Subset fonts are named "ABCDEF+Times-Bold"; the prefix is random and could contain a marker. */
        fun isBoldFont(name: String, markers: List<String>): Boolean {
            val base = name.substringAfter('+').lowercase()
            return markers.any { it in base }
        }
    }
}
