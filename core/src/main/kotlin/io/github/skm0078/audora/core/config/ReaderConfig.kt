// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/ReaderConfig.kt
// Purpose: Tunables for the reader screen: speed steps, sleep-timer choices, page view (Build Spec §2, P1-05, P4-03).

package io.github.skm0078.audora.core.config

/**
 * @property speeds playback speeds offered, ascending, within [MIN_SPEED]..[MAX_SPEED]. Applied by the player
 *   (Sonic time-stretch, C6), so a change is heard at once and nothing is synthesized again.
 * @property defaultSpeed used until the listener picks one; must be one of [speeds].
 * @property sleepMinutes sleep-timer durations offered, besides "end of page".
 * @property pageMaxPixels cap on a rendered page's bitmap (4 bytes a pixel), for very tall pages; a phone-width
 *   A4 page is about 1.7 million pixels.
 * @property pageTapSlopPoints how far from a line of text, in PDF points, a tap on the page still picks its
 *   sentence: about the gap between two lines of body text.
 */
data class ReaderConfig(
    val speeds: List<Float> = DEFAULT_SPEEDS,
    val defaultSpeed: Float = NORMAL_SPEED,
    val sleepMinutes: List<Int> = DEFAULT_SLEEP_MINUTES,
    val pageMaxPixels: Int = DEFAULT_PAGE_MAX_PIXELS,
    val pageTapSlopPoints: Float = DEFAULT_PAGE_TAP_SLOP_POINTS,
) {
    init {
        require(speeds.isNotEmpty() && speeds == speeds.sorted()) { "speeds must be non-empty and ascending" }
        require(speeds.all { it in MIN_SPEED..MAX_SPEED }) { "speeds must be within $MIN_SPEED..$MAX_SPEED" }
        require(defaultSpeed in speeds) { "defaultSpeed $defaultSpeed must be one of the offered speeds" }
        require(sleepMinutes.all { it > 0 }) { "sleepMinutes must be positive" }
        require(pageMaxPixels > 0) { "pageMaxPixels must be positive" }
        require(pageTapSlopPoints >= 0f) { "pageTapSlopPoints must not be negative" }
    }

    /** The offered speed nearest to [speed], e.g. for a speed saved by an older version with other steps. */
    fun nearestSpeed(speed: Float): Float = speeds.minBy { kotlin.math.abs(it - speed) }

    companion object {
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 3f
        private const val NORMAL_SPEED = 1f
        private val DEFAULT_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)
        private val DEFAULT_SLEEP_MINUTES = listOf(15, 30, 60)
        private const val DEFAULT_PAGE_MAX_PIXELS = 4_000_000
        private const val DEFAULT_PAGE_TAP_SLOP_POINTS = 8f
    }
}
