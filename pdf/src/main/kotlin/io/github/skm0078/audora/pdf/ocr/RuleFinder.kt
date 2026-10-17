// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/ocr/RuleFinder.kt
// Purpose: Finds the horizontal and vertical lines ruled on a scanned page: a ruled table's grid (RI-4a part 2).

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.config.RuleConfig
import io.github.skm0078.audora.pdf.document.PageImage
import kotlin.math.roundToInt

/** A rule's pixels in the image it was found in: [left] to [right] and [top] to [bottom], inclusive, y down. */
internal data class PixelRule(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val horizontal: Boolean get() = right - left >= bottom - top
}

/**
 * A rule is a long, thin stroke of ink: pixels darker than the page's Otsu threshold (capped by
 * `RuleConfig.maxDarkBelow`), since a scan's thin rules are often grey. Rows are scanned for runs of ink at least
 * `RuleConfig.minHorizontalEm` long (gaps up to `gapEm` bridged), each row OR'd with the next few (`smearEm`) so a
 * slightly skewed rule stays one run; runs on neighbouring rows that overlap are one rule, kept when it is at most
 * `maxThicknessEm` thick. A thicker one is split into its bands of well-inked rows (`minInkShare`: a double rule),
 * each kept when thin enough; a filled bar or a photograph stays one thick stroke and is dropped. Columns are scanned
 * the same way for vertical rules (`minVerticalEm`).
 *
 * Text is never this long and thin: a Devanagari or Bengali headline runs one word at most, and word spaces break
 * it. Linear in the image's pixels, twice over.
 */
internal class RuleFinder(private val config: RuleConfig) {
    /** The rules on [image], whose text lines are [emPx] pixels high; none when finding rules is off. */
    fun find(image: PageImage, emPx: Float): List<PixelRule> {
        if (!config.enabled || emPx <= 0f) return emptyList()
        val rows = Grey(image.gray, image.width, image.height)
        val darkBelow = minOf(otsu(image.gray), config.maxDarkBelow)
        val horizontal = scan(rows, emPx, config.minHorizontalEm, darkBelow)
        val vertical = scan(rows.transposed(), emPx, config.minVerticalEm, darkBelow).map {
            PixelRule(it.top, it.left, it.bottom, it.right)
        }
        return horizontal + vertical
    }

    /** The grey level that best splits [gray] into ink and paper (Otsu): the one maximising between-class variance. */
    private fun otsu(gray: ByteArray): Int {
        val counts = LongArray(LEVELS)
        for (pixel in gray) counts[pixel.toInt() and BYTE_MASK]++
        val total = gray.size.toDouble()
        val sumAll = counts.indices.sumOf { it * counts[it].toDouble() }
        var below = 0.0
        var sumBelow = 0.0
        var best = 0
        var bestVariance = -1.0
        for (level in 0 until LEVELS) {
            below += counts[level]
            sumBelow += level * counts[level].toDouble()
            val above = total - below
            if (below == 0.0 || above == 0.0) continue
            val difference = sumBelow / below - (sumAll - sumBelow) / above
            val variance = below * above * difference * difference
            if (variance > bestVariance) {
                bestVariance = variance
                best = level + 1
            }
        }
        return best
    }

    /** Rules running along the rows of [grey]. */
    private fun scan(grey: Grey, emPx: Float, minEm: Float, darkBelow: Int): List<PixelRule> {
        val smear = maxOf(1, (config.smearEm * emPx).roundToInt())
        val gap = (config.gapEm * emPx).roundToInt()
        val minLength = (minEm * emPx).roundToInt()
        val maxThickness = smear + (config.maxThicknessEm * emPx).roundToInt()
        val inkAbove = IntArray(grey.width) // ink in rows y until y + smear, per column
        for (y in 0 until minOf(smear, grey.height)) grey.addRow(y, inkAbove, 1, darkBelow)
        val open = ArrayList<IntArray>() // left, top, right, bottom
        val done = ArrayList<PixelRule>()
        for (y in 0 until grey.height) {
            for ((left, right) in runs(inkAbove, gap, minLength)) extend(open, left, right, y)
            val closed = open.filter { it[BOTTOM] < y }
            open.removeAll(closed.toSet())
            closed.flatMapTo(done) { rulesOf(grey, it, smear, maxThickness, darkBelow) }
            grey.addRow(y, inkAbove, -1, darkBelow)
            if (y + smear < grey.height) grey.addRow(y + smear, inkAbove, 1, darkBelow)
        }
        open.flatMapTo(done) { rulesOf(grey, it, smear, maxThickness, darkBelow) }
        return done
    }

    /** Runs of columns with ink at least [minLength] long, gaps up to [gap] bridged. */
    private fun runs(ink: IntArray, gap: Int, minLength: Int): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        var start = -1
        var last = -1
        for (x in ink.indices) {
            if (ink[x] == 0) continue
            if (start >= 0 && x - last - 1 > gap) {
                if (last - start + 1 >= minLength) out.add(start to last)
                start = x
            } else if (start < 0) {
                start = x
            }
            last = x
        }
        if (start >= 0 && last - start + 1 >= minLength) out.add(start to last)
        return out
    }

    /** Adds the run [left]..[right] on row [y] to the open rule it continues, or opens one. */
    private fun extend(open: MutableList<IntArray>, left: Int, right: Int, y: Int) {
        val rule = open.firstOrNull { it[BOTTOM] >= y - 1 && it[LEFT] <= right && it[RIGHT] >= left }
        if (rule == null) {
            open.add(intArrayOf(left, y, right, y))
        } else {
            rule[LEFT] = minOf(rule[LEFT], left)
            rule[RIGHT] = maxOf(rule[RIGHT], right)
            rule[BOTTOM] = y
        }
    }

    /**
     * The rules an open run of rows makes, their ink rows only (each row was OR'd with the next ones): one when it is
     * thin enough, else those of its bands of rows inked at least `minInkShare` as much as its most inked row that are.
     */
    @Suppress("LongParameterList") // The run, the image it is in and the three limits it is measured by.
    private fun rulesOf(grey: Grey, rows: IntArray, smear: Int, maxThickness: Int, darkBelow: Int): List<PixelRule> {
        if (rows[BOTTOM] - rows[TOP] + 1 <= maxThickness) {
            return listOf(PixelRule(rows[LEFT], minOf(rows[TOP] + smear - 1, rows[BOTTOM]), rows[RIGHT], rows[BOTTOM]))
        }
        val ink = (rows[TOP]..rows[BOTTOM]).map { y -> grey.inkIn(y, rows[LEFT], rows[RIGHT], darkBelow) }
        val minInk = config.minInkShare * ink.max()
        val bands = ArrayList<PixelRule>()
        var start = -1
        for (y in rows[TOP]..rows[BOTTOM] + 1) {
            val inked = y <= rows[BOTTOM] && ink[y - rows[TOP]] >= minInk
            if (inked && start < 0) start = y
            if (!inked && start >= 0) {
                if (y - start <= maxThickness - smear) bands.add(PixelRule(rows[LEFT], start, rows[RIGHT], y - 1))
                start = -1
            }
        }
        return bands
    }

    /** One byte of grey per pixel, row by row. */
    private class Grey(val pixels: ByteArray, val width: Int, val height: Int) {
        /** Adds [sign] to [ink] for each pixel of row [y] darker than [darkBelow]. */
        fun addRow(y: Int, ink: IntArray, sign: Int, darkBelow: Int) {
            val row = y * width
            for (x in 0 until width) if ((pixels[row + x].toInt() and BYTE_MASK) < darkBelow) ink[x] += sign
        }

        /** How many pixels of row [y] from [left] to [right] are darker than [darkBelow]. */
        fun inkIn(y: Int, left: Int, right: Int, darkBelow: Int): Int {
            val row = y * width
            return (left..right).count { x -> (pixels[row + x].toInt() and BYTE_MASK) < darkBelow }
        }

        fun transposed(): Grey {
            val out = ByteArray(pixels.size)
            for (y in 0 until height) {
                val row = y * width
                for (x in 0 until width) out[x * height + y] = pixels[row + x]
            }
            return Grey(out, height, width)
        }
    }

    private companion object {
        const val LEFT = 0
        const val TOP = 1
        const val RIGHT = 2
        const val BOTTOM = 3
        const val BYTE_MASK = 0xFF
        const val LEVELS = 256
    }
}
