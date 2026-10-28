// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/QuoteVideoConfig.kt
// Purpose: Tunables for a quote video: a share card read aloud with the word lit as it is said (ADR
//          2026-10-28-quote-video).

package io.github.skm0078.audora.core.config

/**
 * @property widthPx the video's width; 720 x 1280 is the story shape every app shows full screen, at a size every
 *   phone's hardware H.264 encoder takes (most cap at 1080p, and some at 720p for a second stream).
 * @property heightPx the video's height.
 * @property framesPerSecond 30: smooth enough for a highlight that moves word by word, half the work of 60.
 * @property maxDurationMs a video stops here even if the reading runs on: one minute, the limit most apps take for a
 *   story or a status. A 60-word quote reads in about 25 s.
 * @property videoBitRate H.264 bit rate in bits per second; a still card with one moving highlight looks clean at
 *   2.5 Mbit/s, about 19 MB a minute.
 * @property keyFrameIntervalS a key frame this often, so an app that trims or seeks the video lands on a picture.
 * @property aacBitRate the voice's AAC-LC bit rate; mono speech at 64 kbit/s, as for audiobooks.
 * @property codecTimeoutUs how long one encoder buffer request may wait, in microseconds.
 * @property codecDrainAttempts timed-out requests tolerated while flushing the end of a stream before the encoder is
 *   declared stuck; bounds the wait to `codecTimeoutUs * codecDrainAttempts`.
 * @property sentenceGapMs the breath between two sentences of a quote, each rendered on its own.
 */
data class QuoteVideoConfig(
    val widthPx: Int = 720,
    val heightPx: Int = 1280,
    val framesPerSecond: Int = 30,
    val maxDurationMs: Long = 60_000,
    val videoBitRate: Int = 2_500_000,
    val keyFrameIntervalS: Int = 1,
    val aacBitRate: Int = 64_000,
    val codecTimeoutUs: Long = 10_000,
    val codecDrainAttempts: Int = 500,
    val sentenceGapMs: Int = 250,
) {
    init {
        // H.264 encoders want even sides (4:2:0 chroma is subsampled by two both ways).
        require(widthPx > 0 && widthPx % 2 == 0) { "widthPx must be even and > 0, was $widthPx" }
        require(heightPx > 0 && heightPx % 2 == 0) { "heightPx must be even and > 0, was $heightPx" }
        require(framesPerSecond in 1..MAX_FPS) { "framesPerSecond must be in 1..$MAX_FPS, was $framesPerSecond" }
        require(maxDurationMs > 0) { "maxDurationMs must be > 0, was $maxDurationMs" }
        require(videoBitRate > 0) { "videoBitRate must be > 0, was $videoBitRate" }
        require(keyFrameIntervalS >= 1) { "keyFrameIntervalS must be >= 1, was $keyFrameIntervalS" }
        require(aacBitRate > 0) { "aacBitRate must be > 0, was $aacBitRate" }
        require(codecTimeoutUs > 0) { "codecTimeoutUs must be > 0, was $codecTimeoutUs" }
        require(codecDrainAttempts >= 1) { "codecDrainAttempts must be >= 1, was $codecDrainAttempts" }
        require(sentenceGapMs >= 0) { "sentenceGapMs must be >= 0, was $sentenceGapMs" }
    }

    companion object {
        private const val MAX_FPS = 60
        private const val SHARP_WIDTH = 1080
        private const val SHARP_HEIGHT = 1920
        private const val SHARP_BIT_RATE = 5_000_000

        /**
         * The listener's "Sharp" choice: 1080 x 1920 at twice the bit rate. About 2.25 times the pixels to draw and
         * encode, and an 8 MB frame instead of 3.7 MB; a few phones' encoders refuse a second 1080p stream.
         */
        fun sharp(): QuoteVideoConfig =
            QuoteVideoConfig(widthPx = SHARP_WIDTH, heightPx = SHARP_HEIGHT, videoBitRate = SHARP_BIT_RATE)
    }
}
