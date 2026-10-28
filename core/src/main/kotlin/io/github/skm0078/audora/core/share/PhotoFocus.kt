// File: core/src/main/kotlin/io/github/skm0078/audora/core/share/PhotoFocus.kt
// Purpose: Which part of the listener's own picture a card shows when the picture is cropped to fill it, moved by a
//          drag on the preview or a step from TalkBack (ADR 2026-10-28-photo-position).

package io.github.skm0078.audora.core.share

import kotlin.math.max

/** A step for TalkBack's "Move the picture" actions: the way the picture moves. */
enum class PhotoNudge { LEFT, RIGHT, UP, DOWN }

/** A width and a height in any one unit. */
data class Extent(val width: Float, val height: Float) {
    val isEmpty: Boolean get() = width <= 0f || height <= 0f
}

/**
 * The point of the picture kept in view, as fractions across and down the part that does not fit (0 shows its left or
 * top edge, 1 its right or bottom edge). A picture cropped to fill a card overflows on one side only, so only that
 * fraction ever moves.
 */
data class PhotoFocus(val x: Float = MIDDLE, val y: Float = MIDDLE) {
    init {
        require(x in 0f..1f && y in 0f..1f) { "A focus off the picture: $this" }
    }

    /**
     * After a drag of [dx], [dy] on [frame] showing an [image]-sized picture cropped to fill it, the drag and the
     * frame in the same units: the picture follows the finger, stopped at its edges.
     */
    fun dragged(dx: Float, dy: Float, frame: Extent, image: Extent): PhotoFocus {
        if (frame.isEmpty || image.isEmpty) return this
        val scale = max(frame.width / image.width, frame.height / image.height)
        val overflowX = image.width * scale - frame.width
        val overflowY = image.height * scale - frame.height
        // A finger moving right pulls the picture right, so more of its left side shows.
        return PhotoFocus(slid(x, dx, overflowX), slid(y, dy, overflowY))
    }

    /** A TalkBack step: [NUDGE] of the hidden part, the way a drag that way would move it. */
    fun nudged(way: PhotoNudge): PhotoFocus = when (way) {
        PhotoNudge.LEFT -> copy(x = (x + NUDGE).coerceAtMost(1f))
        PhotoNudge.RIGHT -> copy(x = (x - NUDGE).coerceAtLeast(0f))
        PhotoNudge.UP -> copy(y = (y + NUDGE).coerceAtMost(1f))
        PhotoNudge.DOWN -> copy(y = (y - NUDGE).coerceAtLeast(0f))
    }

    companion object {
        private const val MIDDLE = 0.5f

        /** A fifth of the hidden part per step: from the middle, three steps reach an edge. */
        const val NUDGE = 0.2f

        val CENTRE = PhotoFocus()

        private fun slid(at: Float, by: Float, overflow: Float): Float =
            if (overflow <= 0f) at else (at - by / overflow).coerceIn(0f, 1f)
    }
}
