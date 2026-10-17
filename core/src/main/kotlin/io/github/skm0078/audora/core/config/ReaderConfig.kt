// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/ReaderConfig.kt
// Purpose: Tunables for the reader screen: speed presets, sleep timer, page view, OCR quality (Build Spec §2, RI-9).

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
 * @property ocrGoodFrom a scanned page whose OCR confidence (0–100, Tesseract's, weighted by word length) is at least
 *   this reads "OCR quality good" (RI-9). Clean scans land in the 90s; a few misread words pull a page into the 80s.
 * @property ocrFairFrom at least this and below [ocrGoodFrom] reads "fair"; below it, "poor": by then whole lines are
 *   noise and "Skip unreadable text" is doing real work.
 * @property chapterListDepth how many outline levels the chapter list shows (plan item 11): chapters, sections and
 *   subsections; deeper entries are usually figure lists that would bury the chapters.
 * @property timeLeft how "time left at your speed" is estimated (plan item 24).
 */
data class ReaderConfig(
    val speeds: List<Float> = DEFAULT_SPEEDS,
    val defaultSpeed: Float = NORMAL_SPEED,
    val sleepMinutes: List<Int> = DEFAULT_SLEEP_MINUTES,
    val pageMaxPixels: Int = DEFAULT_PAGE_MAX_PIXELS,
    val pageTapSlopPoints: Float = DEFAULT_PAGE_TAP_SLOP_POINTS,
    val speech: SpeechStyleConfig = SpeechStyleConfig(),
    val ocrGoodFrom: Int = DEFAULT_OCR_GOOD_FROM,
    val ocrFairFrom: Int = DEFAULT_OCR_FAIR_FROM,
    val chapterListDepth: Int = DEFAULT_CHAPTER_LIST_DEPTH,
    val timeLeft: TimeLeftConfig = TimeLeftConfig(),
) {
    init {
        require(speeds.isNotEmpty() && speeds == speeds.sorted()) { "speeds must be non-empty and ascending" }
        require(speeds.all { it in speech.minRate..speech.maxRate }) { "speeds must be within the speech range" }
        require(defaultSpeed in speeds) { "defaultSpeed $defaultSpeed must be one of the offered speeds" }
        require(sleepMinutes.all { it > 0 }) { "sleepMinutes must be positive" }
        require(pageMaxPixels > 0) { "pageMaxPixels must be positive" }
        require(pageTapSlopPoints >= 0f) { "pageTapSlopPoints must not be negative" }
        require(chapterListDepth > 0) { "chapterListDepth must be > 0, was $chapterListDepth" }
        require(ocrFairFrom in 0..ocrGoodFrom && ocrGoodFrom <= MAX_OCR_CONFIDENCE) {
            "OCR bands must satisfy 0 <= ocrFairFrom <= ocrGoodFrom <= 100, were $ocrFairFrom and $ocrGoodFrom"
        }
    }

    /** [speed] on the slider: within range and on a step, e.g. for a speed saved by an older version. */
    fun nearestSpeed(speed: Float): Float = speech.clampRate(speed)

    companion object {
        private const val NORMAL_SPEED = 1f
        private val DEFAULT_SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f, 2.5f)
        private val DEFAULT_SLEEP_MINUTES = listOf(15, 30, 60)
        private const val DEFAULT_PAGE_MAX_PIXELS = 4_000_000
        private const val DEFAULT_PAGE_TAP_SLOP_POINTS = 8f
        private const val DEFAULT_OCR_GOOD_FROM = 85
        private const val DEFAULT_OCR_FAIR_FROM = 70
        private const val MAX_OCR_CONFIDENCE = 100
        private const val DEFAULT_CHAPTER_LIST_DEPTH = 3
    }
}
