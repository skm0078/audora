// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/document/RenderSize.kt
// Purpose: Pixel size of a page drawn for OCR: the requested dpi, lowered until the page fits a pixel budget.

package io.github.skm0078.audora.pdf.document

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/** [width] × [height] pixels at [dpi]. */
data class RenderSize(val width: Int, val height: Int, val dpi: Int) {
    companion object {
        private const val POINTS_PER_INCH = 72.0

        /**
         * The size of a [widthPt] × [heightPt] page at [dpi], or at the highest lower dpi whose image holds at most
         * [maxPixels] pixels. Null for a page without area (PDFium reports 0 for a broken page box).
         */
        fun of(widthPt: Int, heightPt: Int, dpi: Int, maxPixels: Long): RenderSize? {
            if (widthPt <= 0 || heightPt <= 0 || dpi <= 0) return null
            val area = widthPt.toDouble() * heightPt / (POINTS_PER_INCH * POINTS_PER_INCH)
            val fitting = floor(sqrt(maxPixels / area)).toInt()
            var effective = minOf(dpi, fitting).coerceAtLeast(1)
            // Rounding up each side can overshoot the budget by a row or column; step down until it fits.
            while (effective > 1 && pixels(widthPt, heightPt, effective) > maxPixels) effective--
            return RenderSize(side(widthPt, effective), side(heightPt, effective), effective)
        }

        private fun side(points: Int, dpi: Int): Int = ceil(points * dpi / POINTS_PER_INCH).toInt()

        private fun pixels(widthPt: Int, heightPt: Int, dpi: Int): Long =
            side(widthPt, dpi).toLong() * side(heightPt, dpi)
    }
}
