// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/audio/PcmAudioTest.kt
// Purpose: Float → 16-bit conversion (clipping, rounding), duration, concatenation and resampling of chunks.

package io.github.skm0078.audora.speech.audio

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
    fun `duration follows sample count and rate`() {
        assertEquals(1_500L, PcmAudio(ShortArray(33_075), 22_050).durationMs)
        assertEquals(0L, PcmAudio.silence(24_000).durationMs)
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
