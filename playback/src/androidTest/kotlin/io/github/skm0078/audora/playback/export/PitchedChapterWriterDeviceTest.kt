// File: playback/src/androidTest/kotlin/io/github/skm0078/audora/playback/export/PitchedChapterWriterDeviceTest.kt
// Purpose: ADR-067 on a device: a saved file at another pitch keeps its length and moves its frequency.

package io.github.skm0078.audora.playback.export

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.audio.PcmAudio
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PitchedChapterWriterDeviceTest {
    @Test
    fun the_voices_own_pitch_writes_straight_through() {
        val writer = CollectingWriter()
        assertSame(writer, writer.atPitch(1f))
    }

    @Test
    fun a_higher_pitch_keeps_the_length_and_raises_the_frequency() {
        val (samples, frequency) = shifted(pitch = 1.2f)
        assertWithin(SECONDS * RATE, samples, LENGTH_TOLERANCE, "length")
        assertWithin(TONE_HZ * 1.2, frequency, FREQUENCY_TOLERANCE, "frequency")
    }

    @Test
    fun a_deeper_pitch_keeps_the_length_and_lowers_the_frequency() {
        val (samples, frequency) = shifted(pitch = 0.8f)
        assertWithin(SECONDS * RATE, samples, LENGTH_TOLERANCE, "length")
        assertWithin(TONE_HZ * 0.8, frequency, FREQUENCY_TOLERANCE, "frequency")
    }

    @Test
    fun abort_aborts_the_file_underneath() {
        val writer = CollectingWriter()
        val pitched = writer.atPitch(1.1f)
        pitched.write(tone(CHUNK))
        pitched.abort()
        assertTrue(writer.aborted)
    }

    /** One second of a tone written in sentence-sized pieces at [pitch]: how many samples came out, at what Hz. */
    private fun shifted(pitch: Float): Pair<Int, Double> {
        val writer = CollectingWriter()
        val pitched = writer.atPitch(pitch)
        val all = tone(SECONDS * RATE)
        all.samples.toList().chunked(CHUNK).forEach { chunk ->
            assertTrue(pitched.write(PcmAudio(chunk.toShortArray(), RATE)) is Outcome.Success)
        }
        assertTrue(pitched.finish() is Outcome.Success)
        val out = writer.samples.toShortArray()
        return out.size to frequencyOf(out)
    }

    private fun tone(count: Int) = PcmAudio(
        ShortArray(count) { (AMPLITUDE * sin(2 * PI * TONE_HZ * it / RATE)).toInt().toShort() },
        RATE,
    )

    /** Rising zero crossings per second over the middle half, away from Sonic's start-up and tail. */
    private fun frequencyOf(samples: ShortArray): Double {
        val from = samples.size / 4
        val to = samples.size * 3 / 4
        val rising = (from + 1 until to).count { samples[it - 1] < 0 && samples[it] >= 0 }
        return rising * RATE.toDouble() / (to - from)
    }

    private fun assertWithin(expected: Number, actual: Number, tolerance: Double, what: String) {
        val e = expected.toDouble()
        assertTrue("$what $actual, expected $e ± ${tolerance * 100}%", abs(actual.toDouble() - e) <= e * tolerance)
    }

    private class CollectingWriter : ChapterAudioWriter {
        val samples = ArrayList<Short>()
        var aborted = false
        override val sampleRate: Int = RATE

        override fun write(audio: PcmAudio): Outcome<Unit, ExportError.Storage> {
            assertEquals(RATE, audio.sampleRate)
            audio.samples.forEach(samples::add)
            return Outcome.Success(Unit)
        }

        override fun finish(): Outcome<ExportedChapter, ExportError.Storage> =
            Outcome.Success(ExportedChapter(AudioChapter(1, 0, 0), "test.m4a", samples.size * 1000L / RATE))

        override fun abort() {
            aborted = true
        }
    }

    private companion object {
        const val RATE = 22_050
        const val SECONDS = 1
        const val CHUNK = 2_205
        const val TONE_HZ = 200.0
        const val AMPLITUDE = 8_000.0
        const val LENGTH_TOLERANCE = 0.02
        const val FREQUENCY_TOLERANCE = 0.05
    }
}
