// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/render/RenderSize.kt
// Purpose: Picks the pixel size of a rendered page: the screen's width, capped so a page bitmap stays small.

package io.github.skm0078.audora.pdf.render

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class RenderSize(val width: Int, val height: Int) {
    companion object {
        /**
         * The page drawn [targetWidth] pixels wide at its own aspect ratio ([pageWidth] × [pageHeight] points as
         * displayed, rotation applied), scaled down if that would exceed [maxPixels] (a very tall page, such as a
         * scroll or a receipt). Both sides are at least 1 pixel.
         */
        fun fit(pageWidth: Float, pageHeight: Float, targetWidth: Int, maxPixels: Int): RenderSize {
            require(pageWidth > 0f && pageHeight > 0f) { "page size must be positive, was $pageWidth × $pageHeight" }
            require(targetWidth > 0 && maxPixels > 0) { "targetWidth and maxPixels must be positive" }
            val aspect = pageHeight.toDouble() / pageWidth
            var width = targetWidth.toDouble()
            if (width * width * aspect > maxPixels) width = floor(sqrt(maxPixels / aspect))
            val w = max(width.toInt(), 1)
            val h = max((w * aspect).roundToInt(), 1)
            return RenderSize(w, h)
        }
    }
}
