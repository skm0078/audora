// File: spikes/playback/src/main/kotlin/io/github/skm0078/audora/spike/playback/engine/FakeSpeechEngine.kt
// Purpose: Renders a unit to speech-like PCM and takes RTF × its length to do it, like a real voice would.

package io.github.skm0078.audora.spike.playback.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** What the render queue needs from any engine; the real SpeechEngine (P1-03) will sit behind the same shape. */
fun interface UnitRenderer {
    /** Returns 16-bit mono PCM. Blocks for the render time; throws [InterruptedException] when cancelled. */
    fun render(unit: FakeUnit): ShortArray
}

class FakeSpeechEngine(
    private val sampleRateHz: Int,
    private val realTimeFactor: Double,
    private val clock: RenderClock,
) : UnitRenderer {
    init {
        require(sampleRateHz > 0) { "sampleRateHz must be positive, was $sampleRateHz" }
        require(realTimeFactor > 0) { "realTimeFactor must be positive, was $realTimeFactor" }
    }

    override fun render(unit: FakeUnit): ShortArray {
        val started = clock.nowNanos()
        val pcm = BabbleSynth.synthesize(unit, sampleRateHz)
        val target = (unit.durationMs * NANOS_PER_MILLI * realTimeFactor).toLong()
        val remaining = target - (clock.nowNanos() - started)
        clock.spend(remaining)
        return pcm
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}

/**
 * Syllable-like tone bursts with a falling pitch per sentence, so a listener can tell sentences apart
 * and hear any gap or stall. Quiet on purpose: this plays for an hour.
 */
internal object BabbleSynth {
    private const val SYLLABLE_MS = 240
    private const val VOICED_MS = 170
    private const val RAMP_MS = 15
    private const val BASE_PITCH_HZ = 150.0
    private const val PITCH_STEP_HZ = 9.0
    private const val PITCH_STEPS = 8
    private const val HARMONICS = 4
    private const val AMPLITUDE = 0.18
    private const val MILLIS_PER_SECOND = 1_000L

    fun synthesize(unit: FakeUnit, sampleRateHz: Int): ShortArray {
        val sampleCount = (unit.durationMs * sampleRateHz / MILLIS_PER_SECOND).toInt()
        val samplesPerSyllable = SYLLABLE_MS * sampleRateHz / MILLIS_PER_SECOND.toInt()
        val voicedSamples = VOICED_MS * sampleRateHz / MILLIS_PER_SECOND.toInt()
        val rampSamples = RAMP_MS * sampleRateHz / MILLIS_PER_SECOND.toInt()
        val sentenceLift = (unit.index % PITCH_STEPS) * PITCH_STEP_HZ
        val pcm = ShortArray(sampleCount)
        for (i in 0 until sampleCount) {
            val syllable = i / samplesPerSyllable
            val offset = i % samplesPerSyllable
            if (offset >= voicedSamples) continue
            val pitch = BASE_PITCH_HZ + sentenceLift - (syllable % PITCH_STEPS) * PITCH_STEP_HZ / 2
            val t = i.toDouble() / sampleRateHz
            var value = 0.0
            for (h in 1..HARMONICS) value += sin(2 * PI * pitch * h * t) / h
            val envelope = envelope(offset, voicedSamples, rampSamples)
            val sample = (value * envelope * AMPLITUDE * Short.MAX_VALUE).toInt()
            pcm[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return pcm
    }

    /** Raised-cosine fade in and out, so bursts do not click. */
    private fun envelope(offset: Int, voiced: Int, ramp: Int): Double = when {
        ramp <= 0 -> 1.0
        offset < ramp -> (1 - cos(PI * offset / ramp)) / 2
        offset > voiced - ramp -> (1 - cos(PI * (voiced - offset) / ramp)) / 2
        else -> 1.0
    }
}
