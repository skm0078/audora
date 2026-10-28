// File: core/src/main/kotlin/io/github/skm0078/audora/core/share/VideoTimeline.kt
// Purpose: Which frames a quote video has and which word each one lights (ADR 2026-10-28-quote-video).

package io.github.skm0078.audora.core.share

import io.github.skm0078.audora.core.config.QuoteVideoConfig

/**
 * The frames of a video of [audioMs] of reading, cut at [QuoteVideoConfig.maxDurationMs], and the word lit in each:
 * [wordStartsMs] are the reading's word starts (ascending, from the voice's own audio, ADR
 * 2026-10-26-word-timings), so a frame lights the last word that has started by its time and none before the first.
 */
class VideoTimeline(audioMs: Long, private val wordStartsMs: List<Long>, private val config: QuoteVideoConfig) {
    init {
        require(audioMs > 0) { "audioMs must be > 0, was $audioMs" }
        require(wordStartsMs.zipWithNext().all { (a, b) -> a <= b }) { "wordStartsMs must ascend" }
    }

    /** The video's length: the reading's, or the cap. */
    val durationMs: Long = minOf(audioMs, config.maxDurationMs)

    /** True when the reading ran past the cap and its end is left out. */
    val isCut: Boolean = audioMs > config.maxDurationMs

    /** Enough frames to cover [durationMs]; the last may run a little past the audio, never short of it. */
    val frameCount: Int = ((durationMs * config.framesPerSecond + MS_PER_S - 1) / MS_PER_S).toInt()

    /** When [frame] is shown, in microseconds from the start. */
    fun timeUs(frame: Int): Long {
        require(frame in 0 until frameCount) { "frame $frame outside 0 until $frameCount" }
        return frame * US_PER_S / config.framesPerSecond
    }

    /** The word lit in [frame]: an index into [wordStartsMs], or [NO_WORD] before the first word is said. */
    fun wordAt(frame: Int): Int {
        val ms = timeUs(frame) / US_PER_MS
        // The last start at or before ms: binary search on the ascending starts.
        var low = 0
        var high = wordStartsMs.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (wordStartsMs[middle] <= ms) low = middle + 1 else high = middle
        }
        return low - 1
    }

    companion object {
        const val NO_WORD = -1
        private const val MS_PER_S = 1_000L
        private const val US_PER_S = 1_000_000L
        private const val US_PER_MS = 1_000L
    }
}
