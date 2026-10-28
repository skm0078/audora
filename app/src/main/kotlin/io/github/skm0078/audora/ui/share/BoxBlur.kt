// File: app/src/main/kotlin/io/github/skm0078/audora/ui/share/BoxBlur.kt
// Purpose: A plain CPU blur for the card's cover background: repeated box blurs, which together look Gaussian.

package io.github.skm0078.audora.ui.share

/** Pure arithmetic on ARGB pixels, so it is tested on the JVM and costs the same on every phone. */
internal object BoxBlur {
    private const val CHANNEL_MASK = 0xFF
    private const val ALPHA_SHIFT = 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8

    /**
     * [pixels] ([width] by [height], row by row) blurred [passes] times each way with a box [radius] pixels wide on
     * each side; edges repeat their last pixel. Returns a new array; [pixels] is left as it was.
     */
    fun blur(pixels: IntArray, width: Int, height: Int, radius: Int, passes: Int): IntArray {
        require(width > 0 && height > 0 && pixels.size == width * height) { "pixels must be width x height" }
        require(radius >= 0 && passes >= 0) { "radius and passes must not be negative" }
        val current = pixels.copyOf()
        val spare = IntArray(current.size)
        repeat(passes) {
            for (y in 0 until height) line(current, spare, Line(y * width, 1, width), radius)
            for (x in 0 until width) line(spare, current, Line(x, width, height), radius)
        }
        return current
    }

    /** One row or column: [count] pixels from [start], [step] apart; [at] repeats the edge pixels past its ends. */
    private class Line(val start: Int, val step: Int, val count: Int) {
        fun at(i: Int): Int = start + i.coerceIn(0, count - 1) * step
    }

    /** One [line] of [from] averaged over a sliding window into [to]. */
    private fun line(from: IntArray, to: IntArray, line: Line, radius: Int) {
        val window = 2 * radius + 1
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        for (i in -radius..radius) {
            val p = from[line.at(i)]
            a += p ushr ALPHA_SHIFT
            r += p shr RED_SHIFT and CHANNEL_MASK
            g += p shr GREEN_SHIFT and CHANNEL_MASK
            b += p and CHANNEL_MASK
        }
        for (i in 0 until line.count) {
            to[line.at(i)] = (a / window shl ALPHA_SHIFT) or (r / window shl RED_SHIFT) or
                (g / window shl GREEN_SHIFT) or (b / window)
            val out = from[line.at(i - radius)]
            val enter = from[line.at(i + radius + 1)]
            a += (enter ushr ALPHA_SHIFT) - (out ushr ALPHA_SHIFT)
            r += (enter shr RED_SHIFT and CHANNEL_MASK) - (out shr RED_SHIFT and CHANNEL_MASK)
            g += (enter shr GREEN_SHIFT and CHANNEL_MASK) - (out shr GREEN_SHIFT and CHANNEL_MASK)
            b += (enter and CHANNEL_MASK) - (out and CHANNEL_MASK)
        }
    }
}
