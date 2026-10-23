// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/audio/Earcon.kt
// Purpose: Generates the soft two-tone cue playback plays for skipped text, captions and chapter starts.

package io.github.skm0078.audora.speech.audio

import io.github.skm0078.audora.core.config.CueConfig
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** Pure and cheap (a few thousand samples); results are cached per sample rate, so call it from any thread. */
class Earcon(private val config: CueConfig = CueConfig()) {
    private val cache = ConcurrentHashMap<Int, PcmAudio>()

    /** The cue alone at [sampleRate] (default: the config's own rate). */
    fun cue(sampleRate: Int = config.sampleRate): PcmAudio = cache.getOrPut(sampleRate) { generate(sampleRate) }

    /** The cue, a short gap, then [audio], at [audio]'s rate. */
    fun before(audio: PcmAudio): PcmAudio =
        PcmAudio.concat(listOf(cue(audio.sampleRate).withSilenceAfter(config.gapAfterMs), audio))

    private fun generate(sampleRate: Int): PcmAudio {
        require(config.tonesHz.all { it < sampleRate / 2f }) { "tones must be below the Nyquist rate of $sampleRate" }
        val toneSamples = config.toneMs * sampleRate / MS_PER_SECOND
        val fadeSamples = config.fadeMs * sampleRate / MS_PER_SECOND
        val peak = PCM16_MAX * DB_BASE.pow(config.peakDbfs / DB_PER_DECADE)
        val samples = ShortArray(toneSamples * config.tonesHz.size)
        config.tonesHz.forEachIndexed { tone, hz ->
            for (i in 0 until toneSamples) {
                val wave = sin(2 * PI * hz * i / sampleRate)
                samples[tone * toneSamples + i] = (peak * envelope(i, toneSamples, fadeSamples) * wave).roundToInt()
                    .toShort()
            }
        }
        return PcmAudio(samples, sampleRate)
    }

    /** Raised-cosine fade in and out over [fade] samples, flat between: no click at either end. */
    private fun envelope(i: Int, length: Int, fade: Int): Double {
        if (fade == 0) return 1.0
        val edge = minOf(i, length - 1 - i)
        return if (edge >= fade) 1.0 else (1 - cos(PI * edge / fade)) / 2
    }

    private companion object {
        const val MS_PER_SECOND = 1_000
        const val PCM16_MAX = 32_767.0
        const val DB_BASE = 10.0
        const val DB_PER_DECADE = 20.0
    }
}
