// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/ocr/PaperLight.kt
// Purpose: A page image's paper brightness, estimated on a shrunk copy, and the page with its light evened out.

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.config.LightingConfig
import io.github.skm0078.audora.pdf.document.PageImage
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * How bright the paper of [image] is across the page ([LightingConfig]): one sample per block of `block` pixels,
 * the block's brightest pixel (ink drops out), widened to the brightest within `widenBlocks` and averaged over
 * `smoothBlocks`. Work is one pass over the pixels to sample them and, only for [evened], one more; everything else
 * runs on the samples (about 100 000 for an A4 page at 300 dpi), so a 12 MP photo costs two passes over its bytes.
 */
internal class PaperLight private constructor(
    private val image: PageImage,
    private val block: Int,
    private val columns: Int,
    private val rows: Int,
    private val samples: IntArray,
) {
    /** The page the light was estimated for. */
    val pageIndex: Int get() = image.pageIndex

    /**
     * The 5th percentile of the samples over their 95th, 0–1: 1 for paper lit evenly, about 0.4 for a page half in
     * a hard shadow. 1 for a page with no light at all, which has nothing to even out.
     */
    val evenness: Float by lazy {
        val histogram = IntArray(GREY_LEVELS)
        for (sample in samples) histogram[sample]++
        val low = percentile(histogram, LOW_SHARE)
        val high = percentile(histogram, HIGH_SHARE)
        if (high == 0) 1f else low.toFloat() / high
    }

    /** [image] with every pixel divided by the paper's brightness there: paper white, ink as dark as on white. */
    fun evened(): PageImage {
        val width = image.width
        val (x0, x1, fx) = axis(width, columns)
        val (y0, y1, fy) = axis(image.height, rows)
        val out = ByteArray(image.gray.size)
        for (y in 0 until image.height) {
            val top = y0[y] * columns
            val bottom = y1[y] * columns
            val wy = fy[y]
            for (x in 0 until width) {
                val wx = fx[x]
                val upper = samples[top + x0[x]] * (ONE - wx) + samples[top + x1[x]] * wx
                val lower = samples[bottom + x0[x]] * (ONE - wx) + samples[bottom + x1[x]] * wx
                val paper = ((upper * (ONE - wy) + lower * wy) shr (2 * FRACTION_BITS)).coerceAtLeast(1)
                val grey = image.gray[y * width + x].toInt() and BYTE_MASK
                out[y * width + x] = (grey * MAX_GREY / paper).coerceAtMost(MAX_GREY).toByte()
            }
        }
        return PageImage(image.pageIndex, width, image.height, image.dpi, out)
    }

    /**
     * For each pixel along an axis of [length] pixels: the two samples (of [count]) around it and the weight of the
     * second in 1/[ONE]; samples sit at their block's centre, and pixels past the first or last centre take its value.
     */
    private fun axis(length: Int, count: Int): Triple<IntArray, IntArray, IntArray> {
        val first = IntArray(length)
        val second = IntArray(length)
        val weight = IntArray(length)
        for (i in 0 until length) {
            val position = (i + HALF) / block - HALF
            val below = floor(position).toInt()
            first[i] = below.coerceIn(0, count - 1)
            second[i] = (below + 1).coerceIn(0, count - 1)
            weight[i] = ((position - below) * ONE).roundToInt().coerceIn(0, ONE)
        }
        return Triple(first, second, weight)
    }

    companion object {
        private const val GREY_LEVELS = 256
        private const val MAX_GREY = 255
        private const val BYTE_MASK = 0xFF
        private const val LOW_SHARE = 0.05f
        private const val HIGH_SHARE = 0.95f
        private const val HALF = 0.5f
        private const val FRACTION_BITS = 8
        private const val ONE = 1 shl FRACTION_BITS

        /** The paper light of [image], sampled as [config] says. */
        fun of(image: PageImage, config: LightingConfig): PaperLight {
            val block = (image.dpi * config.blockInches).roundToInt().coerceAtLeast(1)
            val columns = (image.width + block - 1) / block
            val rows = (image.height + block - 1) / block
            var samples = brightest(image, block, columns, rows)
            samples = slide(samples, columns, rows, config.widenBlocks) { window -> window.max() }
            samples = slide(samples, columns, rows, config.smoothBlocks) { window -> window.sum() / window.size }
            return PaperLight(image, block, columns, rows, samples)
        }

        /** The brightest pixel of each [block] × [block] block (the last row and column of blocks may be smaller). */
        private fun brightest(image: PageImage, block: Int, columns: Int, rows: Int): IntArray {
            val out = IntArray(columns * rows)
            val columnOf = IntArray(image.width) { it / block }
            for (y in 0 until image.height) {
                val row = (y / block) * columns
                val start = y * image.width
                for (x in 0 until image.width) {
                    val grey = image.gray[start + x].toInt() and BYTE_MASK
                    val i = row + columnOf[x]
                    if (grey > out[i]) out[i] = grey
                }
            }
            return out
        }

        /** [values] with each replaced by [combine] of its window [radius] wide each way, rows then columns. */
        private fun slide(
            values: IntArray,
            columns: Int,
            rows: Int,
            radius: Int,
            combine: (IntArray) -> Int,
        ): IntArray {
            if (radius == 0) return values
            val window = IntArray(2 * radius + 1)
            val across = IntArray(values.size)
            for (r in 0 until rows) {
                for (c in 0 until columns) {
                    for (k in window.indices) {
                        window[k] =
                            values[r * columns + (c + k - radius).coerceIn(0, columns - 1)]
                    }
                    across[r * columns + c] = combine(window)
                }
            }
            val out = IntArray(values.size)
            for (r in 0 until rows) {
                for (c in 0 until columns) {
                    for (k in window.indices) window[k] = across[(r + k - radius).coerceIn(0, rows - 1) * columns + c]
                    out[r * columns + c] = combine(window)
                }
            }
            return out
        }

        /** The lowest grey level at or below which at least [share] of the samples counted in [histogram] fall. */
        private fun percentile(histogram: IntArray, share: Float): Int {
            val target = ceil(histogram.sum() * share).toInt().coerceAtLeast(1)
            var seen = 0
            for (level in histogram.indices) {
                seen += histogram[level]
                if (seen >= target) return level
            }
            return histogram.lastIndex
        }
    }
}
