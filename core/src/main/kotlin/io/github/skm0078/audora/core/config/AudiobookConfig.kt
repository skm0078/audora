// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/AudiobookConfig.kt
// Purpose: Tunables for exporting a book as offline audiobook files, one per chapter (ADR-034).

package io.github.skm0078.audora.core.config

/**
 * @property pagesPerPartWithoutChapters a book whose outline gives no chapters is cut into parts of this many
 *   pages, so no single file runs for hours and an audiobook player can still skip between parts.
 * @property aacBitRate encoder bit rate in bits per second. Mono speech at 64 kbit/s AAC-LC is close to
 *   transparent; about 29 MB per hour of audio.
 * @property renderRetryLimit consecutive sentences that may fail to render (each is skipped) before the export
 *   stops: one bad sentence is skipped, a broken voice stops instead of writing hours of silence (as playback).
 * @property codecTimeoutUs how long one encoder buffer request may wait, in microseconds.
 * @property codecDrainAttempts timed-out encoder requests tolerated while flushing the end of a file before the
 *   encoder is declared stuck; bounds the wait to `codecTimeoutUs * codecDrainAttempts`.
 * @property folderNameMaxChars the book's folder name is cut to this length; file systems and players choke on
 *   very long paths, and some PDF titles are whole sentences.
 * @property maxExportMs the export's wake lock is held at most this long, so a stuck export cannot drain the
 *   battery; Android 15+ also stops long foreground jobs after 6 hours a day.
 * @property progressStepPercent the progress notification is updated when the export moves on by this much.
 * @property wakeLockMs the export's wake lock times out after this long and is renewed on every finished page, so it
 *   is held exactly while pages keep coming: a stuck export releases it within this time, a long book never does.
 */
data class AudiobookConfig(
    val pagesPerPartWithoutChapters: Int = 10,
    val aacBitRate: Int = 64_000,
    val renderRetryLimit: Int = 3,
    val codecTimeoutUs: Long = 10_000,
    val codecDrainAttempts: Int = 500,
    val folderNameMaxChars: Int = 80,
    val maxExportMs: Long = 21_600_000L, // 6 h
    val progressStepPercent: Int = 1,
    val wakeLockMs: Long = 1_800_000L, // 30 min: far beyond one page, even a slow OCR page on a slow phone.
) {
    init {
        require(pagesPerPartWithoutChapters >= 1) {
            "pagesPerPartWithoutChapters must be >= 1, was $pagesPerPartWithoutChapters"
        }
        require(aacBitRate > 0) { "aacBitRate must be > 0, was $aacBitRate" }
        require(renderRetryLimit >= 1) { "renderRetryLimit must be >= 1, was $renderRetryLimit" }
        require(codecTimeoutUs > 0) { "codecTimeoutUs must be > 0, was $codecTimeoutUs" }
        require(codecDrainAttempts >= 1) { "codecDrainAttempts must be >= 1, was $codecDrainAttempts" }
        require(folderNameMaxChars >= 1) { "folderNameMaxChars must be >= 1, was $folderNameMaxChars" }
        require(maxExportMs > 0) { "maxExportMs must be > 0, was $maxExportMs" }
        require(wakeLockMs > 0) { "wakeLockMs must be > 0, was $wakeLockMs" }
        require(progressStepPercent in 1..PERCENT) {
            "progressStepPercent must be in 1..$PERCENT, was $progressStepPercent"
        }
    }

    private companion object {
        const val PERCENT = 100
    }
}
