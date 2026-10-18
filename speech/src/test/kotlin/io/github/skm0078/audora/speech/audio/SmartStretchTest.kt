// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/audio/SmartStretchTest.kt
// Purpose: Smart speed (plan item 10, ADR-049): exact length, kept pitch, and silences sped up more than consonants.

package io.github.skm0078.audora.speech.audio

import io.github.skm0078.audora.core.config.SmartSpeedConfig
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartStretchTest {
    private val config = SmartSpeedConfig()
    private val stretch = SmartStretch(config)

    @Test
    fun `a factor of one, too close to one, or a very short piece is left as it is`() {
        val voice = tone(ms = 500)
        assertSame(voice, stretch.stretch(voice, 1f))
        assertSame(voice, stretch.stretch(voice, 1.0005f))
        val blip = tone(ms = 30)
        assertSame(blip, stretch.stretch(blip, 2f))
    }

    @Test
    fun `the piece comes out the asked factor shorter`() {
        val rows = listOf(1.1f, 1.5f, 2f, 3f)
        val voice = PcmAudio.concat(listOf(tone(ms = 300), silence(ms = 200), tone(ms = 400, hz = 330.0)))
        rows.forEach { factor ->
            val out = stretch.stretch(voice, factor)
            val expected = voice.samples.size / factor
            assertEquals("factor $factor", expected, out.samples.size.toFloat(), expected * TOLERANCE)
        }
    }

    @Test
    fun `a held tone keeps its pitch`() {
        val out = stretch.stretch(tone(ms = 1_000, hz = 200.0), 2f)
        assertEquals(200.0, frequencyOf(out), 200.0 * TOLERANCE * 5)
    }

    @Test
    fun `silence stays silent`() {
        val out = stretch.stretch(silence(ms = 500), 1.8f)
        assertTrue(out.samples.all { it.toInt() == 0 })
    }

    @Test
    fun `silences go fastest, held sounds next, onsets slowest, and the total is exact`() {
        val rows = listOf(1.2f, 1.5f, 2f)
        val weights = floatArrayOf(0f, 0.5f, 0.5f, 1f, 1f, 0.5f, 0f, 1f)
        rows.forEach { factor ->
            val speeds = stretch.frameSpeeds(weights, factor)
            assertTrue(speeds[0] < speeds[1] && speeds[1] < speeds[3])
            assertEquals(1f + (factor - 1f) * config.consonantShare, speeds[0], 1e-4f)
            assertEquals(weights.size / factor, speeds.sumOf { 1.0 / it }.toFloat(), 1e-3f)
        }
    }

    @Test
    fun `with nothing to spread, or past the frame cap, every frame takes the factor`() {
        assertTrue(stretch.frameSpeeds(floatArrayOf(0f, 0f, 0f), 1.5f).all { it == 1.5f })
        val capped = SmartStretch(config.copy(maxFrameSpeed = 1.6f))
        assertTrue(capped.frameSpeeds(floatArrayOf(0f, 1f, 0f, 1f), 1.5f).all { it == 1.5f })
    }

    @Test
    fun `frames are judged silent, steady or changing`() {
        val plain = SmartStretch(config.copy(smoothFrames = 1))
        val voice = PcmAudio.concat(listOf(silence(ms = 30), tone(ms = 50), silence(ms = 30)))
        val frame = RATE * config.frameMs / 1_000
        val weights = plain.frameWeights(voice.samples, frame)
        assertEquals(config.silenceWeight, weights[0])
        assertEquals(config.transientWeight, weights[3]) // the tone's onset
        assertEquals(config.steadyWeight, weights[5])
        assertEquals(config.transientWeight, weights[7]) // its release
        assertEquals(config.silenceWeight, weights.last())
    }

    private fun tone(ms: Int, hz: Double = 220.0, amplitude: Int = 8_000): PcmAudio {
        val size = RATE * ms / 1_000
        return PcmAudio(
            ShortArray(size) { i ->
                (amplitude * sin(2 * PI * hz * i / RATE)).roundToInt().toShort()
            },
            RATE,
        )
    }

    private fun silence(ms: Int) = PcmAudio.silence(RATE, ms)

    /** Rising zero crossings per second over the middle half, away from the ends. */
    private fun frequencyOf(audio: PcmAudio): Double {
        val samples = audio.samples
        val from = samples.size / 4
        val to = samples.size * 3 / 4
        val rising = (from + 1 until to).count { samples[it - 1] < 0 && samples[it] >= 0 }
        return rising.toDouble() * RATE / (to - from)
    }

    private companion object {
        const val RATE = 22_050
        const val TOLERANCE = 0.01f
    }
}
