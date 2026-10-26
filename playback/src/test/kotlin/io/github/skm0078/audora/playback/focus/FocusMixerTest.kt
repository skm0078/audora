// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/focus/FocusMixerTest.kt
// Purpose: The focus sound sits under the voice, dips while it speaks, fades on a switch and never clips.

package io.github.skm0078.audora.playback.focus

import io.github.skm0078.audora.core.config.FocusSoundConfig
import io.github.skm0078.audora.core.model.FocusSound
import io.github.skm0078.audora.core.model.FocusSoundOptions
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusMixerTest {
    private val config = FocusSoundConfig()
    private val rain = FocusSoundOptions(FocusSound.RAIN, level = 1f)

    @Test
    fun offLeavesTheVoiceUntouched() {
        val mixer = FocusMixer(RATE, config, TEST_BEDS)
        val voice = tone(seconds = 1f)
        val out = voice.copyOf()
        mixer.mix(out, out.size, 1, FocusSoundOptions())
        assertArrayEquals(voice, out)
    }

    @Test
    fun inSilenceTheSoundRisesToItsLevel() {
        val mixer = FocusMixer(RATE, config, TEST_BEDS)
        run(mixer, ShortArray(RATE * 2), rain)
        val bed = run(mixer, ShortArray(RATE), rain)
        // Each sound is about 0.18 RMS of full scale before its gain.
        val expected = config.maxGain * NOISE_RMS
        assertEquals(expected, rms(bed), expected * TOLERANCE)
    }

    @Test
    fun theSoundDipsWhileTheVoiceSpeaksAndComesBackAfter() {
        val mixer = FocusMixer(RATE, config, TEST_BEDS)
        run(mixer, ShortArray(RATE * 2), rain)
        val voice = tone(seconds = 2f)
        val mixed = run(mixer, voice.copyOf(), rain)
        val underVoice = rms(ShortArray(RATE) { i -> (mixed[RATE + i] - voice[RATE + i]).toShort() })
        assertEquals(config.maxGain * NOISE_RMS * config.duckedFraction, underVoice, underVoice * TOLERANCE)

        val after = run(mixer, ShortArray(RATE * 2), rain)
        val full = config.maxGain * NOISE_RMS
        assertEquals(full, rms(after.copyOfRange(RATE, RATE * 2)), full * TOLERANCE)
    }

    @Test
    fun aShortGapBetweenWordsStaysDipped() {
        val mixer = FocusMixer(RATE, config, TEST_BEDS)
        run(mixer, ShortArray(RATE * 2), rain)
        run(mixer, tone(seconds = 1f), rain)
        // A gap shorter than the hold: the sound must not swell between two words.
        val gap = run(mixer, ShortArray(RATE * config.holdMs / 2 / MS), rain)
        val ducked = config.maxGain * NOISE_RMS * config.duckedFraction
        assertTrue("gap rms ${rms(gap)}", rms(gap) < ducked * (1 + TOLERANCE))
    }

    @Test
    fun switchingToOffFadesOutWithinTheSwitchTime() {
        val mixer = FocusMixer(RATE, config, TEST_BEDS)
        run(mixer, ShortArray(RATE * 2), rain)
        val fade = run(mixer, ShortArray(RATE * config.switchMs * FADE_TIMES / MS), FocusSoundOptions())
        val tail = fade.copyOfRange(fade.size - RATE / TAIL_PARTS, fade.size)
        assertTrue("tail ${rms(tail)}", rms(tail) < SILENT_RMS)
        assertEquals(FocusSound.OFF, mixer.sound)
        assertTrue(run(mixer, ShortArray(RATE), FocusSoundOptions()).all { it == 0.toShort() })
    }

    @Test
    fun switchingSoundsFadesThroughSilence() {
        val mixer = FocusMixer(RATE, config, TEST_BEDS)
        run(mixer, ShortArray(RATE * 2), rain)
        run(mixer, ShortArray(RATE * 2), FocusSoundOptions(FocusSound.WIND, level = 1f))
        assertEquals(FocusSound.WIND, mixer.sound)
    }

    @Test
    fun aLoudVoiceNeverWrapsAround() {
        val mixer = FocusMixer(RATE, config, TEST_BEDS)
        val loud = ShortArray(RATE) { i -> if (i % 2 == 0) Short.MAX_VALUE else Short.MIN_VALUE }
        val out = run(mixer, loud.copyOf(), rain)
        for (i in out.indices) assertEquals(loud[i] > 0, out[i] > 0)
    }

    @Test
    fun stereoGetsTheSameSoundInBothChannels() {
        val mixer = FocusMixer(RATE, config, TEST_BEDS)
        val out = ShortArray(RATE * 2 * 2)
        mixer.mix(out, out.size, 2, rain)
        for (frame in out.indices step 2) assertEquals(out[frame], out[frame + 1])
        assertTrue(rms(out) > 0f)
    }

    @Test
    fun aVoiceAtAnotherRateCarriesTheSoundOnAtItsLevel() {
        val mixer = FocusMixer(RATE, config, TEST_BEDS)
        run(mixer, ShortArray(RATE * 2), rain)
        val next = mixer.continuedAt(OTHER_RATE)
        val bed = run(next, ShortArray(OTHER_RATE / 2), rain)
        val full = config.maxGain * NOISE_RMS
        assertEquals(FocusSound.RAIN, next.sound)
        assertEquals(full, rms(bed), full * TOLERANCE)
    }

    @Test
    fun afterAGapTheSoundFadesBackIn() {
        val mixer = FocusMixer(RATE, config, TEST_BEDS)
        run(mixer, ShortArray(RATE * 2), rain)
        mixer.fadeIn()
        val start = run(mixer, ShortArray(RATE / 20), rain)
        assertTrue(rms(start) < config.maxGain * NOISE_RMS / 2)
    }

    @Test
    fun theEndFadesOutInsteadOfStoppingDead() {
        val mixer = FocusMixer(RATE, config, TEST_BEDS)
        run(mixer, ShortArray(RATE * 2), rain)
        val tail = mixer.tail(channels = 2)
        assertEquals(RATE * config.tailMs / MS * 2, tail.size)
        val firstPart = rms(tail.copyOfRange(0, tail.size / 10))
        val lastPart = rms(tail.copyOfRange(tail.size * 9 / 10, tail.size))
        assertTrue(lastPart < firstPart / 4)
        assertEquals(0, mixer.tail(channels = 2).size)
    }

    private fun run(mixer: FocusMixer, samples: ShortArray, options: FocusSoundOptions): ShortArray {
        // In buffers like the audio sink's, so state carries across calls.
        for (from in samples.indices step BUFFER) {
            val chunk = samples.copyOfRange(from, minOf(samples.size, from + BUFFER))
            mixer.mix(chunk, chunk.size, 1, options)
            chunk.copyInto(samples, from)
        }
        return samples
    }

    /** A voice-like level: a 200 Hz tone at a quarter of full scale. */
    private fun tone(seconds: Float) = ShortArray((RATE * seconds).toInt()) { i ->
        (sin(2 * Math.PI * TONE_HZ * i / RATE) * Short.MAX_VALUE * VOICE_LEVEL).toInt().toShort()
    }

    private fun rms(samples: ShortArray): Float =
        sqrt(samples.sumOf { it.toDouble() * it } / samples.size).toFloat() / Short.MAX_VALUE

    private companion object {
        const val RATE = 22_050
        const val OTHER_RATE = 24_000
        const val MS = 1_000
        const val BUFFER = 1_024
        const val NOISE_RMS = 0.19f
        const val TOLERANCE = 0.25f
        const val TONE_HZ = 200
        const val VOICE_LEVEL = 0.25
        const val FADE_TIMES = 2
        const val TAIL_PARTS = 10
        const val SILENT_RMS = 1e-4f
    }
}

/** RMS of [TEST_BEDS]' noise: uniform in ±a has an RMS of a / √3. */
private const val TEST_PEAK = 0.329f

/** A plain noise for every sound but off, about as loud as the real ones (0.19 RMS), so mixing is tested alone. */
internal val TEST_BEDS = FocusBeds { sound, _, seed ->
    if (sound == FocusSound.OFF) {
        null
    } else {
        val random = Xorshift(seed + sound.ordinal)
        NoiseSource { random.white() * TEST_PEAK }
    }
}
