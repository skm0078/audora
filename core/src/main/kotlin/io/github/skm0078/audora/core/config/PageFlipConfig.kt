// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/PageFlipConfig.kt
// Purpose: Tunables for turning pages: swipe, edge taps and the pages drawn ahead (ADR 2026-10-28-page-flip), and
//          how long a curl or slide takes and how it is shaded (ADR 2026-10-29-page-curl).

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
 * @property curlMs a whole curl at normal turn speed: long enough to see the paper turn, short enough not to wait.
 * @property slideMs a whole slide at normal turn speed.
 * @property readingTurnMs a turn made by reading aloud, whatever the turn speed: quicker and softer, so the eye sees
 *   the page go while the voice carries on.
 * @property readingLift how far the corner rises in a turn no finger makes, as a share of the page's height: enough
 *   for the page to curl rather than slide flat.
 * @property backAlpha how much of the page's print shows through the back of the turned-over paper, as on thin paper.
 * @property shadowShare how wide the shadows either side of the fold are, as a share of the page's width.
 */
data class PageFlipConfig(
    val drawNext: Boolean = true,
    val drawPrevious: Boolean = true,
    val edgeShare: Float = DEFAULT_EDGE_SHARE,
    val turnShare: Float = DEFAULT_TURN_SHARE,
    val flingDpPerSecond: Float = DEFAULT_FLING_DP_PER_SECOND,
    val edgeResistance: Float = DEFAULT_EDGE_RESISTANCE,
    val curlMs: Int = DEFAULT_CURL_MS,
    val slideMs: Int = DEFAULT_SLIDE_MS,
    val readingTurnMs: Int = DEFAULT_READING_TURN_MS,
    val readingLift: Float = DEFAULT_READING_LIFT,
    val backAlpha: Float = DEFAULT_BACK_ALPHA,
    val shadowShare: Float = DEFAULT_SHADOW_SHARE,
) {
    init {
        require(edgeShare in 0f..MAX_EDGE_SHARE) { "edgeShare must be in 0..0.5, was $edgeShare" }
        require(turnShare > 0f && turnShare < 1f) { "turnShare must be in (0, 1), was $turnShare" }
        require(flingDpPerSecond > 0f) { "flingDpPerSecond must be positive, was $flingDpPerSecond" }
        require(edgeResistance in 0f..1f) { "edgeResistance must be in 0..1, was $edgeResistance" }
        require(curlMs > 0 && slideMs > 0 && readingTurnMs > 0) {
            "turn durations must be positive, were $curlMs, $slideMs and $readingTurnMs"
        }
        require(readingLift in 0f..MAX_LIFT) { "readingLift must be in 0..0.5, was $readingLift" }
        require(backAlpha in 0f..1f) { "backAlpha must be in 0..1, was $backAlpha" }
        require(shadowShare > 0f && shadowShare < MAX_SHADOW_SHARE) {
            "shadowShare must be in (0, 0.5), was $shadowShare"
        }
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
        const val DEFAULT_CURL_MS = 400
        const val DEFAULT_SLIDE_MS = 200
        const val DEFAULT_READING_TURN_MS = 250
        const val DEFAULT_READING_LIFT = 0.12f
        const val DEFAULT_BACK_ALPHA = 0.12f
        const val DEFAULT_SHADOW_SHARE = 0.06f
        const val MAX_LIFT = 0.5f
        const val MAX_SHADOW_SHARE = 0.5f
    }
}
