// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/audio/PcmAudioTest.kt
// Purpose: Float → 16-bit conversion (clipping, rounding), duration, concatenation and resampling of chunks.

package io.github.skm0078.audora.speech.audio

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PcmAudioTest {
    @Test
    fun `floats map to 16-bit with clipping and rounding`() {
        val audio = PcmAudio.fromFloats(floatArrayOf(0f, 1f, -1f, 2f, -3f, 0.5f, 1e-6f), 22_050)

        assertArrayEquals(shortArrayOf(0, 32_767, -32_767, 32_767, -32_767, 16_384, 0), audio.samples)
        assertEquals(22_050, audio.sampleRate)
    }

    @Test
    fun `a shifted tone is higher by the ratio and shorter by it, at the same sample rate`() {
        val rate = 24_000
        val tone = PcmAudio(ShortArray(rate) { i -> (10_000 * sin(2 * PI * 200 * i / rate)).toInt().toShort() }, rate)
        val ratio = 1.12f

        val higher = tone.pitched(ratio)

        assertEquals(rate, higher.sampleRate)
        assertEquals((rate / ratio).toDouble(), higher.samples.size.toDouble(), 2.0)
        // Rising zero crossings per second: 200 Hz before, 224 after.
        val crossings = (1 until higher.samples.size).count { higher.samples[it - 1] < 0 && higher.samples[it] >= 0 }
        assertEquals(200.0 * ratio, crossings / (higher.samples.size.toDouble() / rate), 2.0)
        assertSame(tone, tone.pitched(1f))
    }

    @Test
    fun `duration follows sample count and rate`() {
        assertEquals(1_500L, PcmAudio(ShortArray(33_075), 22_050).durationMs)
        assertEquals(0L, PcmAudio.silence(24_000).durationMs)
    }

    @Test
    fun `a pause is silence of the given length after the sound`() {
        val audio = PcmAudio(shortArrayOf(5, -5), 16_000)

        val paused = audio.withSilenceAfter(250)

        assertEquals(2 + 4_000, paused.samples.size)
        assertArrayEquals(shortArrayOf(5, -5, 0, 0), paused.samples.copyOf(4))
        assertEquals(0, paused.samples.drop(2).count { it != 0.toShort() })
        assertEquals(16_000, paused.sampleRate)
        assertSame(audio, audio.withSilenceAfter(0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero sample rate is rejected`() {
        PcmAudio(ShortArray(1), 0)
    }

    @Test
    fun `concat joins chunks in order`() {
        val joined = PcmAudio.concat(
            listOf(
                PcmAudio(shortArrayOf(1, 2), 16_000),
                PcmAudio(shortArrayOf(), 16_000),
                PcmAudio(shortArrayOf(3), 16_000),
            ),
        )

        assertArrayEquals(shortArrayOf(1, 2, 3), joined.samples)
        assertEquals(16_000, joined.sampleRate)
    }

    @Test
    fun `concat of one chunk returns it unchanged`() {
        val only = PcmAudio(shortArrayOf(7), 16_000)

        assertSame(only, PcmAudio.concat(listOf(only)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `concat refuses mixed sample rates`() {
        PcmAudio.concat(listOf(PcmAudio(shortArrayOf(1), 16_000), PcmAudio(shortArrayOf(2), 24_000)))
    }

    @Test
    fun `upsampling interpolates between neighbours`() {
        val up = PcmAudio(shortArrayOf(0, 100, 200, 100), 8_000).resampled(16_000)

        assertArrayEquals(shortArrayOf(0, 50, 100, 150, 200, 150, 100, 100), up.samples)
        assertEquals(16_000, up.sampleRate)
    }

    @Test
    fun `resampling keeps the duration`() {
        val audio = PcmAudio(ShortArray(16_000), 16_000)

        assertEquals(22_050, audio.resampled(22_050).samples.size)
        assertEquals(0, PcmAudio.silence(16_000).resampled(24_000).samples.size)
    }

    @Test
    fun `resampling to the same rate returns it unchanged`() {
        val audio = PcmAudio(shortArrayOf(1, 2, 3), 16_000)

        assertSame(audio, audio.resampled(16_000))
    }
}
