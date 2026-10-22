// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/ocr/WornPrint.kt
// Purpose: Tells a black and white scan from a grey image, and softens one so worn strokes join up before OCR.

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.config.WornPrintConfig
import io.github.skm0078.audora.pdf.document.PageImage
import kotlin.math.roundToInt

/** The two image steps behind [WornPrintConfig], each a few passes over the pixels with no allocation per pixel. */
internal object WornPrint {
    /** The share of [image]'s pixels darker than `darkBelow` or lighter than `lightAbove`. */
    fun bilevelShare(image: PageImage, config: WornPrintConfig): Float {
        var extreme = 0
        for (byte in image.gray) {
            val grey = byte.toInt() and GREY_MASK
            if (grey < config.darkBelow || grey > config.lightAbove) extreme++
        }
        return extreme.toFloat() / image.gray.size
    }

    /** The pixels' radius for [image]'s resolution: `radiusPerInch` of an inch, at least one pixel. */
    fun radiusFor(image: PageImage, config: WornPrintConfig): Int =
        (image.dpi * config.radiusPerInch).roundToInt().coerceAtLeast(1)

    /**
     * [image] with each pixel the mean of the square [radius] pixels each way around it, [passes] times; edges repeat
     * their outermost pixel. Each pass is a horizontal then a vertical running sum, so the cost does not grow with
     * the radius.
     */
    fun softened(image: PageImage, radius: Int, passes: Int): PageImage {
        require(radius > 0 && passes > 0) { "radius and passes must be > 0" }
        val width = image.width
        val height = image.height
        val current = IntArray(image.gray.size) { image.gray[it].toInt() and GREY_MASK }
        val scratch = IntArray(current.size)
        repeat(passes) {
            boxPass(current, scratch, length = width, lines = height, step = 1, lineStep = width, radius = radius)
            boxPass(scratch, current, length = height, lines = width, step = width, lineStep = 1, radius = radius)
        }
        val gray = ByteArray(current.size) { current[it].toByte() }
        return PageImage(image.pageIndex, width, height, image.dpi, gray)
    }

    /**
     * One running mean along [lines] lines of [length] pixels from [from] into [to]: pixel `k` of line `l` is at
     * `l * lineStep + k * step`.
     */
    @Suppress("LongParameterList") // Both directions share this one loop; the steps say which.
    private fun boxPass(from: IntArray, to: IntArray, length: Int, lines: Int, step: Int, lineStep: Int, radius: Int) {
        val window = 2 * radius + 1
        for (line in 0 until lines) {
            val base = line * lineStep
            fun at(k: Int): Int = from[base + k.coerceIn(0, length - 1) * step]
            var sum = 0
            for (k in -radius..radius) sum += at(k)
            for (k in 0 until length) {
                to[base + k * step] = (sum + window / 2) / window
                sum += at(k + radius + 1) - at(k - radius)
            }
        }
    }

    private const val GREY_MASK = 0xFF
}
