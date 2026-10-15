// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/extract/PageText.kt
// Purpose: A page's extracted text with per-character boxes, font runs and the signals later routing uses.

package io.github.skm0078.audora.pdf.extract

import io.github.skm0078.audora.core.model.TextBox

/**
 * The text of one page after extraction (Build Spec §5 step 1). ReadingUnit offsets index into [text].
 *
 * Holds one page only: a book is read page at a time and never kept whole in memory (ADR-011 §5).
 */
class PageText internal constructor(
    val pageIndex: Int,
    val text: String,
    /** 4 floats per UTF-16 unit of [text]; NaN when the unit has no box (generated or synthesized). */
    private val boxes: FloatArray,
    val fontRuns: List<FontRun>,
    val stats: PageTextStats,
) {
    init {
        require(boxes.size == text.length * RawChars.BOX_FLOATS) { "boxes must hold 4 floats per UTF-16 unit" }
    }

    fun boxAt(offset: Int): TextBox? {
        val o = offset * RawChars.BOX_FLOATS
        val left = boxes[o]
        if (left.isNaN()) return null
        return TextBox(left = left, top = boxes[o + 1], right = boxes[o + 2], bottom = boxes[o + 3])
    }
}

/** A run of [text][PageText.text] in one font at one size, [start] inclusive to [end] exclusive. */
data class FontRun(val start: Int, val end: Int, val fontName: String, val fontSize: Float)

/**
 * Per-page counts for diagnostics and the page router (P3-03): Unicode-map errors and invisible text decide
 * OCR, font names decide the legacy-font converter (ADR-011 §3). Counts are over PDFium's characters.
 */
data class PageTextStats(
    val pdfiumChars: Int,
    val generatedChars: Int,
    val unicodeMapErrors: Int,
    val invisibleChars: Int,
    val actualTextSpans: Int,
    val actualTextRebuild: ActualTextRebuild,
)
