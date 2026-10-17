// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/CueConfig.kt
// Purpose: Tunables for the short sounds playback plays for skipped junk, captions and chapter starts (RI-2b, RI-7b).

package io.github.skm0078.audora.core.config

/**
 * A cue is two soft sine tones, generated at render time (no audio asset, so no licence to track). Quiet and short on
 * purpose: it says "something was here" without startling a listener in bed. Tuned by ear in the final phone session.
 *
 * @property tonesHz the tones in order, each [toneMs] long.
 * @property peakDbfs the loudest sample, in dB below full scale; speech peaks near -3 dBFS, so -20 sits well under it.
 * @property fadeMs each tone fades in and out over this long (a raised cosine), so it never clicks.
 * @property gapAfterMs silence between a cue and the words it introduces.
 * @property sampleRate the rate of a cue played on its own (a skipped unit); a cue before words takes the voice's rate.
 */
data class CueConfig(
    val tonesHz: List<Float> = listOf(DEFAULT_LOW_HZ, DEFAULT_HIGH_HZ),
    val toneMs: Int = DEFAULT_TONE_MS,
    val peakDbfs: Float = DEFAULT_PEAK_DBFS,
    val fadeMs: Int = DEFAULT_FADE_MS,
    val gapAfterMs: Int = DEFAULT_GAP_AFTER_MS,
    val sampleRate: Int = DEFAULT_SAMPLE_RATE,
) {
    init {
        require(tonesHz.isNotEmpty() && tonesHz.all { it > 0f }) { "tonesHz must be positive, were $tonesHz" }
        require(toneMs > 0) { "toneMs must be > 0, was $toneMs" }
        require(peakDbfs < 0f) { "peakDbfs must be below 0, was $peakDbfs" }
        require(fadeMs in 0..toneMs / 2) { "fadeMs must be in 0..toneMs / 2, was $fadeMs" }
        require(gapAfterMs >= 0) { "gapAfterMs must be >= 0, was $gapAfterMs" }
        require(sampleRate > 0) { "sampleRate must be > 0, was $sampleRate" }
        require(tonesHz.all { it < sampleRate / 2f }) { "tones must be below the Nyquist rate of $sampleRate Hz" }
    }

    private companion object {
        const val DEFAULT_LOW_HZ = 660f
        const val DEFAULT_HIGH_HZ = 880f
        const val DEFAULT_TONE_MS = 70
        const val DEFAULT_PEAK_DBFS = -20f
        const val DEFAULT_FADE_MS = 15
        const val DEFAULT_GAP_AFTER_MS = 120
        const val DEFAULT_SAMPLE_RATE = 22_050
    }
}
