// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/FocusSoundConfig.kt
// Purpose: Tunables for the focus sound under the voice (plan item 72): loudness, ducking and fades.

package io.github.skm0078.audora.core.config

/**
 * The sound at level 1 peaks around [maxGain] of full scale, well under the voice. While the voice speaks (its
 * envelope above [voiceThreshold] of full scale, held [holdMs] across the gaps between words) the sound dips to
 * [duckedFraction] of its level, within [duckMs]; it comes back over [releaseMs] once the voice stops, so a pause
 * between sentences fills gently instead of pumping. Switching sounds fades out over [switchMs] and back in. When
 * the book's audio ends the sound fades out over [tailMs] instead of stopping dead; after a seek or a stall (a render
 * that fell behind) it fades back in instead of returning at full level.
 */
data class FocusSoundConfig(
    val maxGain: Float = DEFAULT_MAX_GAIN,
    val duckedFraction: Float = DEFAULT_DUCKED_FRACTION,
    val voiceThreshold: Float = DEFAULT_VOICE_THRESHOLD,
    val holdMs: Int = DEFAULT_HOLD_MS,
    val duckMs: Int = DEFAULT_DUCK_MS,
    val releaseMs: Int = DEFAULT_RELEASE_MS,
    val switchMs: Int = DEFAULT_SWITCH_MS,
    val tailMs: Int = DEFAULT_TAIL_MS,
) {
    init {
        require(maxGain in 0f..1f) { "maxGain must be in 0..1, was $maxGain" }
        require(duckedFraction in 0f..1f) { "duckedFraction must be in 0..1, was $duckedFraction" }
        require(voiceThreshold in 0f..1f) { "voiceThreshold must be in 0..1, was $voiceThreshold" }
        require(holdMs >= 0) { "holdMs must be >= 0, was $holdMs" }
        require(minOf(duckMs, releaseMs, switchMs) > 0) { "fade times must be > 0: $duckMs, $releaseMs, $switchMs" }
        require(tailMs >= 0) { "tailMs must be >= 0, was $tailMs" }
    }

    companion object {
        const val DEFAULT_MAX_GAIN = 0.25f
        const val DEFAULT_DUCKED_FRACTION = 0.4f
        const val DEFAULT_VOICE_THRESHOLD = 0.02f
        const val DEFAULT_HOLD_MS = 250
        const val DEFAULT_DUCK_MS = 60
        const val DEFAULT_RELEASE_MS = 700
        const val DEFAULT_SWITCH_MS = 500
        const val DEFAULT_TAIL_MS = 600
    }
}
