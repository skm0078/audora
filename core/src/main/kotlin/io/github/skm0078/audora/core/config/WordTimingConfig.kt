// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/WordTimingConfig.kt
// Purpose: Tunables for timing the word highlight from the voice's own audio (CF-10, ADR 2026-10-26-word-timings).

package io.github.skm0078.audora.core.config

/**
 * The voice gives no word times (sherpa-onnx returns samples only), so they are read from its audio: its pauses are
 * found and the sentence's punctuation is pinned to them, and the letters share only the time between two pauses.
 *
 * @property frameMs the loudness is measured over frames this long.
 * @property minPauseMs a quiet run inside the speech at least this long is a pause (a word's own stop consonant is
 *   shorter; a comma's pause, the voice's or the clause pause Audora inserts, is longer).
 * @property quietFraction a frame is quiet below this share of the speech's typical loud frame ([loudPercentile]).
 * @property quietFloor and always below this 16-bit level, so near-silent hiss between words counts as quiet.
 * @property loudPercentile the speech's typical loud frame is this percentile of its frames' loudness.
 * @property toleranceFraction a punctuation mark is pinned only to a pause within this share of the speech of where
 *   the letters put it ([minToleranceMs] at least): a breath elsewhere never pulls the highlight away.
 */
data class WordTimingConfig(
    val frameMs: Int = DEFAULT_FRAME_MS,
    val minPauseMs: Int = DEFAULT_MIN_PAUSE_MS,
    val quietFraction: Float = DEFAULT_QUIET_FRACTION,
    val quietFloor: Int = DEFAULT_QUIET_FLOOR,
    val loudPercentile: Float = DEFAULT_LOUD_PERCENTILE,
    val toleranceFraction: Float = DEFAULT_TOLERANCE_FRACTION,
    val minToleranceMs: Int = DEFAULT_MIN_TOLERANCE_MS,
) {
    init {
        require(frameMs > 0) { "frameMs must be > 0, was $frameMs" }
        require(minPauseMs >= frameMs) { "minPauseMs must be >= frameMs, was $minPauseMs" }
        require(quietFraction in 0f..1f) { "quietFraction must be in 0..1, was $quietFraction" }
        require(quietFloor >= 0) { "quietFloor must be >= 0, was $quietFloor" }
        require(loudPercentile in 0f..1f) { "loudPercentile must be in 0..1, was $loudPercentile" }
        require(toleranceFraction in 0f..1f) { "toleranceFraction must be in 0..1, was $toleranceFraction" }
        require(minToleranceMs >= 0) { "minToleranceMs must be >= 0, was $minToleranceMs" }
    }

    companion object {
        const val DEFAULT_FRAME_MS = 10
        const val DEFAULT_MIN_PAUSE_MS = 70
        const val DEFAULT_QUIET_FRACTION = 0.08f
        const val DEFAULT_QUIET_FLOOR = 50
        const val DEFAULT_LOUD_PERCENTILE = 0.9f
        const val DEFAULT_TOLERANCE_FRACTION = 0.25f
        const val DEFAULT_MIN_TOLERANCE_MS = 250
    }
}
