// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/ReaderConfig.kt
// Purpose: Tunables for the reader screen: speed presets, sleep-timer choices, page view (Build Spec §2, P1-05, P4-03).

package io.github.skm0078.audora.core.config

/**
 * @property speeds one-tap speed presets beside the slider, ascending, within [speech]'s range. The voice speaks at
 *   the speed itself (ADR-033), so the sentence playing restarts at the new speed.
 * @property defaultSpeed used until the listener picks one; must be one of [speeds].
 * @property speech the speed slider's range and step, and the pace timings.
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
    val speech: SpeechStyleConfig = SpeechStyleConfig(),
) {
    init {
        require(speeds.isNotEmpty() && speeds == speeds.sorted()) { "speeds must be non-empty and ascending" }
        require(speeds.all { it in speech.minRate..speech.maxRate }) { "speeds must be within the speech range" }
        require(defaultSpeed in speeds) { "defaultSpeed $defaultSpeed must be one of the offered speeds" }
        require(sleepMinutes.all { it > 0 }) { "sleepMinutes must be positive" }
        require(pageMaxPixels > 0) { "pageMaxPixels must be positive" }
        require(pageTapSlopPoints >= 0f) { "pageTapSlopPoints must not be negative" }
    }

    /** [speed] on the slider: within range and on a step, e.g. for a speed saved by an older version. */
    fun nearestSpeed(speed: Float): Float = speech.clampRate(speed)

    companion object {
        private const val NORMAL_SPEED = 1f
        private val DEFAULT_SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f, 2.5f)
        private val DEFAULT_SLEEP_MINUTES = listOf(15, 30, 60)
        private const val DEFAULT_PAGE_MAX_PIXELS = 4_000_000
        private const val DEFAULT_PAGE_TAP_SLOP_POINTS = 8f
    }
}
