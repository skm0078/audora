// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/audio/SmartStretchTest.kt
// Purpose: Smart speed (plan item 10, ADR-049): exact length, kept pitch, and silences sped up more than consonants.

package io.github.skm0078.audora.speech.audio

import io.github.skm0078.audora.core.config.SmartSpeedConfig
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
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
    fun `the piece comes out the asked factor shorter, to the sample, at every voice rate`() {
        val factors = listOf(1.1f, 1.5f, 2f, 3f)
        listOf(16_000, 22_050, 24_000).forEach { rate ->
            // 307 ms and 413 ms: the last frame is a partial one, which must count for its real length.
            val voice = PcmAudio.concat(
                listOf(tone(ms = 307, rate = rate), silence(ms = 200, rate = rate), tone(ms = 413, 330.0, rate = rate)),
            )
            factors.forEach { factor ->
                val out = stretch.stretch(voice, factor)
                val expected = voice.samples.size / factor
                assertEquals("$rate Hz x$factor", expected, out.samples.size.toFloat(), 1f)
            }
        }
    }

    @Test
    fun `the level is kept and the piece ends at zero, with no click into the pause after`() {
        val voice = tone(ms = 600)
        val out = stretch.stretch(voice, 2f)
        assertEquals(rms(voice.samples, 0, voice.samples.size), rms(out.samples, 0, out.samples.size - 200), 0.05)
        assertEquals(0, out.samples.last().toInt())
    }

    @Test
    fun `a low voice keeps its pitch and its harmonics`() {
        listOf(1.33f, 2f).forEach { factor ->
            val out = stretch.stretch(tone(ms = 1_000, hz = 80.0), factor)
            assertEquals("x$factor", 80.0, frequencyOf(out), 80.0 * TOLERANCE * 5)
        }
        // A 5-harmonic 80 or 85 Hz voice: with too short an alignment search (5 ms) over 1% of its energy warbles off
        // the harmonic lines at 1.33x; at 8 ms it stays under that.
        listOf(80.0, 85.0).forEach { hz ->
            listOf(1.33f, 1.5f, 2f).forEach { factor ->
                val out = stretch.stretch(harmonicTone(ms = 1_000, hz = hz), factor)
                assertTrue("$hz Hz x$factor", offHarmonicShare(out.samples, hz) < MAX_OFF_HARMONIC)
            }
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
    fun `with nothing to spread every frame takes the factor`() {
        assertTrue(stretch.frameSpeeds(floatArrayOf(0f, 0f, 0f), 1.5f).all { it == 1.5f })
        val atTheFloor = SmartStretch(config.copy(maxFrameSpeed = 1.1f))
        assertTrue(atTheFloor.frameSpeeds(floatArrayOf(0f, 1f, 0f, 1f), 1.5f).all { it == 1.5f })
    }

    @Test
    fun `with too many protected frames for the cap the rest stay at the cap, the protected ones share what is left`() {
        // 2 protected frames and 2 others capped at 1.6x: the others take 2/1.6 of the 4/1.5 frame-times left.
        val capped = SmartStretch(config.copy(maxFrameSpeed = 1.6f))
        val speeds = capped.frameSpeeds(floatArrayOf(0f, 1f, 0f, 1f), 1.5f)
        val protectedSpeed = 2 / (4 / 1.5f - 2 / 1.6f)
        listOf(protectedSpeed, 1.6f, protectedSpeed, 1.6f).forEachIndexed { i, expected ->
            assertEquals("frame $i", expected, speeds[i], 1e-5f)
        }
        assertTrue(protectedSpeed > 1f + 0.5f * config.consonantShare && protectedSpeed < 1.5f)
        assertEquals(4 / 1.5f, speeds.sumOf { 1.0 / it }.toFloat(), 1e-4f)
        // Most frames protected (3 of 4) at 2x: still graded, never an even 2x.
        val mostly = stretch.frameSpeeds(floatArrayOf(0f, 0f, 0f, 1f), 2f)
        assertEquals(config.maxFrameSpeed, mostly[3], 1e-4f)
        assertTrue(mostly[0] < 2f)
        assertEquals(4 / 2f, mostly.sumOf { 1.0 / it }.toFloat(), 1e-4f)
    }

    @Test
    fun `frames are judged silent, steady or changing`() {
        val plain = SmartStretch(config.copy(onsetGuardFrames = 0))
        val voice = PcmAudio.concat(listOf(silence(ms = 30), tone(ms = 70), silence(ms = 30)))
        val weights = plain.frameWeights(voice.samples, FRAME)
        assertEquals(config.silenceWeight, weights[0])
        assertEquals(config.transientWeight, weights[3]) // the tone's onset
        assertEquals(config.steadyWeight, weights[4])
        assertEquals(config.transientWeight, weights[9]) // its release
        assertEquals(config.silenceWeight, weights.last())
    }

    @Test
    fun `an onset is spared with a guard frame either side, not averaged away`() {
        val voice = PcmAudio.concat(listOf(silence(ms = 30), tone(ms = 70), silence(ms = 30)))
        val weights = stretch.frameWeights(voice.samples, FRAME)
        assertEquals(config.transientWeight, weights[4]) // guarded after the onset
        assertEquals(config.steadyWeight, weights[5])
        assertEquals(config.transientWeight, weights[8]) // guarded before the release
        assertEquals(config.silenceWeight, weights[2]) // silence stays silence

        val speeds = stretch.frameSpeeds(weights, 2f)
        val floor = 1f + config.consonantShare
        assertEquals(floor, speeds[3], 1e-4f)
        assertTrue(speeds[0] > speeds[5] && speeds[5] > speeds[4])
    }

    @Test
    fun `silence is judged against the piece's own level, so a quiet voice keeps its sounds`() {
        val quiet = PcmAudio.concat(listOf(silence(ms = 30), tone(ms = 70, amplitude = 80), silence(ms = 30)))
        val weights = stretch.frameWeights(quiet.samples, FRAME)
        assertEquals(config.silenceWeight, weights[0])
        assertEquals(config.steadyWeight, weights[5]) // -52 dBFS, still a held sound
    }

    private fun tone(ms: Int, hz: Double = 220.0, amplitude: Int = 8_000, rate: Int = RATE): PcmAudio {
        val size = rate * ms / 1_000
        return PcmAudio(
            ShortArray(size) { i ->
                (amplitude * sin(2 * PI * hz * i / rate)).roundToInt().toShort()
            },
            rate,
        )
    }

    private fun silence(ms: Int, rate: Int = RATE) = PcmAudio.silence(rate, ms)

    /** Five harmonics of [hz], the n-th at 1/n of the first's amplitude. */
    private fun harmonicTone(ms: Int, hz: Double): PcmAudio {
        val size = RATE * ms / 1_000
        return PcmAudio(
            ShortArray(size) { i ->
                (1..HARMONICS).sumOf { h -> HARMONIC_AMPLITUDE / h * sin(2 * PI * hz * h * i / RATE) }.roundToInt()
                    .toShort()
            },
            RATE,
        )
    }

    /**
     * The share of [samples]' energy more than 4 Hz from [hz]'s first five harmonics: a Hann-windowed DFT at the bins
     * near each harmonic, against the whole energy (Parseval; one-sided, so half of N times the sum of squares).
     */
    private fun offHarmonicShare(samples: ShortArray, hz: Double): Double {
        val n = samples.size
        val windowed = DoubleArray(n) { i -> samples[i] * (0.5 - 0.5 * cos(2 * PI * i / n)) }
        val total = windowed.sumOf { it * it } * n / 2
        val binHz = RATE.toDouble() / n
        val near = (0..(hz * HARMONICS + HARMONIC_BAND_HZ).div(binHz).toInt()).filter { k ->
            (1..HARMONICS).any { h -> abs(k * binHz - hz * h) < HARMONIC_BAND_HZ }
        }
        val on = near.sumOf { k ->
            var re = 0.0
            var im = 0.0
            for (i in 0 until n) {
                val angle = 2 * PI * k * i / n
                re += windowed[i] * cos(angle)
                im -= windowed[i] * sin(angle)
            }
            re * re + im * im
        }
        return 1 - on / total
    }

    /** RMS of [samples] from..until, relative to full scale. */
    private fun rms(samples: ShortArray, from: Int, until: Int): Double =
        sqrt((from until until).sumOf { samples[it].toDouble() * samples[it] } / (until - from)) / FULL_SCALE

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
        const val FRAME = RATE / 100 // 10 ms
        const val TOLERANCE = 0.01f
        const val FULL_SCALE = 32_768.0
        const val HARMONICS = 5
        const val HARMONIC_AMPLITUDE = 8_000.0
        const val HARMONIC_BAND_HZ = 4.0
        const val MAX_OFF_HARMONIC = 0.01
    }
}
