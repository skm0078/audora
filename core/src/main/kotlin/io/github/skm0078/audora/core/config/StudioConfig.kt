// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/StudioConfig.kt
// Purpose: Tunables for studio mode: rendering a book ahead while the phone charges (plan item 6, ADR-039).

package io.github.skm0078.audora.core.config

/**
 * @property hoursAhead audio rendered ahead of the listener's place, in listening hours. About 60–80 MB per hour
 *   (stored losslessly); Kokoro renders at about a third of real time on a test phone, so a night on the charger fills
 *   two to three hours and the next charge carries on.
 * @property maxBytes all books' studio audio together stays under this; the books opened longest ago go first.
 * @property minFreeBytes rendering pauses for good when the phone has less free storage than this, so studio mode
 *   never fills the phone.
 * @property keepBehindPages pages before the listener's page that are kept, so skipping back still plays studio audio.
 * @property renderRetryLimit consecutive sentences that may fail to render (each is left to live rendering) before
 *   the run stops: a broken voice stops instead of burning a night of battery.
 * @property waitPollMs while waiting (for the charger, or for other audio to stop) the run checks again this often;
 *   plugging in wakes it at once.
 * @property maxRunMs the run's wake lock is held at most this long; Android 15+ also stops long foreground jobs after
 *   6 hours a day.
 * @property blockSamples the lossless codec predicts and codes audio in blocks of this many samples.
 * @property measureMs the voice renders this long before the time left is shown: the first sentences include loading
 *   the model, which would make the estimate far too slow.
 * @property progressEveryMs the notification and the library card update at most this often while sentences render;
 *   a finished page or a change of wait always updates them.
 */
data class StudioConfig(
    val hoursAhead: Int = 6,
    val maxBytes: Long = 2L * BYTES_PER_GIB,
    val minFreeBytes: Long = BYTES_PER_GIB / 2,
    val keepBehindPages: Int = 2,
    val renderRetryLimit: Int = 3,
    val waitPollMs: Long = 30_000L,
    val maxRunMs: Long = 21_600_000L, // 6 h
    val blockSamples: Int = 4_096,
    val measureMs: Long = 20_000L,
    val progressEveryMs: Long = 2_000L,
) {
    init {
        require(hoursAhead >= 1) { "hoursAhead must be >= 1, was $hoursAhead" }
        require(maxBytes > 0) { "maxBytes must be > 0, was $maxBytes" }
        require(minFreeBytes >= 0) { "minFreeBytes must be >= 0, was $minFreeBytes" }
        require(keepBehindPages >= 0) { "keepBehindPages must be >= 0, was $keepBehindPages" }
        require(renderRetryLimit >= 1) { "renderRetryLimit must be >= 1, was $renderRetryLimit" }
        require(waitPollMs > 0) { "waitPollMs must be > 0, was $waitPollMs" }
        require(maxRunMs > 0) { "maxRunMs must be > 0, was $maxRunMs" }
        require(measureMs >= 0) { "measureMs must be >= 0, was $measureMs" }
        require(progressEveryMs >= 0) { "progressEveryMs must be >= 0, was $progressEveryMs" }
        require(blockSamples in MIN_BLOCK..MAX_BLOCK) {
            "blockSamples must be in $MIN_BLOCK..$MAX_BLOCK, was $blockSamples"
        }
    }

    val aheadMs: Long get() = hoursAhead * MS_PER_HOUR

    private companion object {
        const val BYTES_PER_GIB = 1L shl 30
        const val MS_PER_HOUR = 3_600_000L
        const val MIN_BLOCK = 16
        const val MAX_BLOCK = 65_535
    }
}
