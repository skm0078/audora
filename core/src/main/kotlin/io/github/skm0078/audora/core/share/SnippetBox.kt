// File: core/src/main/kotlin/io/github/skm0078/audora/core/share/SnippetBox.kt
// Purpose: The box a listener drags on a drawn page to share part of it as a picture: kept on the page, never
//          thinner than a handle, never more than a third of the page (ADR 2026-10-28-page-snippet).

package io.github.skm0078.audora.core.share

import io.github.skm0078.audora.core.config.ShareCardConfig
import kotlin.math.abs

/** A corner of the box, dragged to resize it. */
enum class SnippetCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/**
 * Part of a page in fractions of the page as drawn (0..1 across and down, top left first). Every box made through
 * [around], [moved] or [resized] keeps [ShareCardConfig]'s rules: inside the page, each side at least
 * `snippetMinSide`, the area at most `snippetMaxShare`.
 */
data class SnippetBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(left < right && top < bottom) { "An empty or inverted box: $this" }
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top

    /** The share of the page's area the box covers. */
    val area: Float get() = width * height

    /** The same box slid by [dx], [dy], stopped at the page's edges. */
    fun moved(dx: Float, dy: Float): SnippetBox {
        val x = (left + dx).coerceIn(0f, 1f - width)
        val y = (top + dy).coerceIn(0f, 1f - height)
        return SnippetBox(x, y, x + width, y + height)
    }

    /**
     * [corner] dragged by [dx], [dy]; the opposite corner stays. Past the size limit, the side the drag moved less is
     * shortened, so the side being pulled follows the finger.
     */
    fun resized(corner: SnippetCorner, dx: Float, dy: Float, config: ShareCardConfig = ShareCardConfig()): SnippetBox {
        val min = config.snippetMinSide
        val movesLeft = corner == SnippetCorner.TOP_LEFT || corner == SnippetCorner.BOTTOM_LEFT
        val movesTop = corner == SnippetCorner.TOP_LEFT || corner == SnippetCorner.TOP_RIGHT
        var newLeft = if (movesLeft) (left + dx).coerceIn(0f, right - min) else left
        var newRight = if (movesLeft) right else (right + dx).coerceIn(left + min, 1f)
        var newTop = if (movesTop) (top + dy).coerceIn(0f, bottom - min) else top
        var newBottom = if (movesTop) bottom else (bottom + dy).coerceIn(top + min, 1f)
        val w = newRight - newLeft
        val h = newBottom - newTop
        if (w * h > config.snippetMaxShare) {
            if (abs(dx) >= abs(dy)) {
                val fitted = (config.snippetMaxShare / w).coerceAtLeast(min)
                if (movesTop) newTop = newBottom - fitted else newBottom = newTop + fitted
            } else {
                val fitted = (config.snippetMaxShare / h).coerceAtLeast(min)
                if (movesLeft) newLeft = newRight - fitted else newRight = newLeft + fitted
            }
        }
        return SnippetBox(newLeft, newTop, newRight, newBottom)
    }

    companion object {
        /** A band across the middle of the page, for a page whose lines have no positions. */
        fun middle(config: ShareCardConfig = ShareCardConfig()): SnippetBox {
            val side = MIDDLE_SIDE
            val height = (config.snippetMaxShare * MIDDLE_FILL / (1f - 2 * side)).coerceAtMost(1f)
            val top = (1f - height) / 2
            return SnippetBox(side, top, 1f - side, top + height)
        }

        /**
         * Around [lines] (each as left, top, right, bottom fractions), with a little paper around them. A sentence
         * taller than the limit keeps its first lines: the box is cut from the bottom. Null when there are no lines.
         */
        fun around(lines: List<SnippetBox>, config: ShareCardConfig = ShareCardConfig()): SnippetBox? {
            if (lines.isEmpty()) return null
            val pad = config.snippetPad
            val min = config.snippetMinSide
            var left = (lines.minOf { it.left } - pad).coerceAtLeast(0f)
            var right = (lines.maxOf { it.right } + pad).coerceAtMost(1f)
            if (right - left < min) {
                val middle = (left + right) / 2
                left = (middle - min / 2).coerceIn(0f, 1f - min)
                right = left + min
            }
            val top = (lines.minOf { it.top } - pad).coerceIn(0f, 1f - min)
            val tallest = (config.snippetMaxShare / (right - left)).coerceAtLeast(min)
            val bottom = (lines.maxOf { it.bottom } + pad).coerceIn(top + min, 1f).coerceAtMost(top + tallest)
            return SnippetBox(left, top, right, bottom)
        }

        // The middle band leaves this much of the width at each side and fills this share of the size limit.
        private const val MIDDLE_SIDE = 0.06f
        private const val MIDDLE_FILL = 0.6f
    }
}
