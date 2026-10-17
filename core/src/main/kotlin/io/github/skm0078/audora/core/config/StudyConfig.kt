// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/StudyConfig.kt
// Purpose: Tunables for the study modes (plan item 31): how long the gap to say a sentence back lasts.

package io.github.skm0078.audora.core.config

/**
 * The gap after a sentence ("Leave a gap to say it back") is the sentence's own length times [gapFactor], kept
 * within [minGapMs] and [maxGapMs]: language apps give about the sentence's length (shadowing), a floor so a
 * one-word heading still leaves time to speak, and a cap so a 40-second sentence does not leave dead air that
 * sounds like a fault.
 */
data class StudyConfig(
    val gapFactor: Float = DEFAULT_GAP_FACTOR,
    val minGapMs: Int = DEFAULT_MIN_GAP_MS,
    val maxGapMs: Int = DEFAULT_MAX_GAP_MS,
) {
    init {
        require(gapFactor > 0f) { "gapFactor must be > 0, was $gapFactor" }
        require(minGapMs in 0..maxGapMs) { "need 0 <= minGapMs <= maxGapMs, was $minGapMs..$maxGapMs" }
    }

    /** The gap after a sentence that took [spokenMs] to say. */
    fun gapMs(spokenMs: Long): Int =
        (spokenMs * gapFactor).toLong().coerceIn(minGapMs.toLong(), maxGapMs.toLong()).toInt()

    companion object {
        const val DEFAULT_GAP_FACTOR = 1f
        const val DEFAULT_MIN_GAP_MS = 1_500
        const val DEFAULT_MAX_GAP_MS = 20_000
    }
}
