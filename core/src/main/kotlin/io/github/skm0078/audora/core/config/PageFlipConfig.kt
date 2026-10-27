// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/PageFlipConfig.kt
// Purpose: Tunables for turning PDF pages by hand: swipe, edge taps and the pages drawn ahead (ADR
//          2026-10-28-page-flip).

package io.github.skm0078.audora.core.config

import kotlin.math.abs
import kotlin.math.sign

/**
 * @property drawNext draw the next page in the background once the shown one is on screen, so a swipe forward lands
 *   on a finished page. Costs one more page bitmap in memory (about 6.6 MB for a phone-width A4 page).
 * @property drawPrevious the same for the page before: a swipe back is instant too. Another page bitmap; turn it off
 *   (keep [drawNext]) for the lighter "next page only" option.
 * @property edgeShare a tap in this fraction of the page's width at either side, away from any sentence, turns the
 *   page: the outer fifth, as e-readers do.
 * @property turnShare a swipe past this fraction of the page's width turns it when let go.
 * @property flingDpPerSecond a quicker flick than this turns the page even if it moved less than [turnShare].
 * @property edgeResistance how far the page follows the finger past the first or last page: a give that says "no
 *   further", not a turn.
 */
data class PageFlipConfig(
    val drawNext: Boolean = true,
    val drawPrevious: Boolean = true,
    val edgeShare: Float = DEFAULT_EDGE_SHARE,
    val turnShare: Float = DEFAULT_TURN_SHARE,
    val flingDpPerSecond: Float = DEFAULT_FLING_DP_PER_SECOND,
    val edgeResistance: Float = DEFAULT_EDGE_RESISTANCE,
) {
    init {
        require(edgeShare in 0f..MAX_EDGE_SHARE) { "edgeShare must be in 0..0.5, was $edgeShare" }
        require(turnShare > 0f && turnShare < 1f) { "turnShare must be in (0, 1), was $turnShare" }
        require(flingDpPerSecond > 0f) { "flingDpPerSecond must be positive, was $flingDpPerSecond" }
        require(edgeResistance in 0f..1f) { "edgeResistance must be in 0..1, was $edgeResistance" }
    }

    /** -1 for a tap at [x] (a fraction of the page's width) in the left edge, 1 in the right, 0 in between. */
    fun edgeTurn(x: Float): Int = when {
        x < edgeShare -> -1
        x > 1f - edgeShare -> 1
        else -> 0
    }

    /**
     * The turn a swipe makes when let go: [dragPx] is how far the page moved (negative: to the left, towards the next
     * page) of [widthPx], [velocityPx] the finger's speed then, [flingPx] [flingDpPerSecond] in pixels. 1 for the
     * next page, -1 for the previous, 0 to settle back. A flick back the other way always settles back.
     */
    fun swipeTurn(dragPx: Float, velocityPx: Float, widthPx: Float, flingPx: Float): Int {
        if (dragPx == 0f || widthPx <= 0f) return 0
        val way = if (dragPx < 0f) 1 else -1
        val flickedBack = abs(velocityPx) >= flingPx && sign(velocityPx) == sign(dragPx) * -1
        val far = abs(dragPx) >= widthPx * turnShare
        val flung = abs(velocityPx) >= flingPx && sign(velocityPx) == sign(dragPx)
        return if (!flickedBack && (far || flung)) way else 0
    }

    private companion object {
        const val DEFAULT_EDGE_SHARE = 0.2f
        const val DEFAULT_TURN_SHARE = 0.25f
        const val DEFAULT_FLING_DP_PER_SECOND = 600f
        const val DEFAULT_EDGE_RESISTANCE = 0.3f
        const val MAX_EDGE_SHARE = 0.5f
    }
}
