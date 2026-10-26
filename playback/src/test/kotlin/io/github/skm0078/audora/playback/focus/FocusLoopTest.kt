// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/focus/FocusLoopTest.kt
// Purpose: Recorded loops play round and round without a click, fade in once decoded, and resample cleanly (Q-1).

package io.github.skm0078.audora.playback.focus

import io.github.skm0078.audora.core.model.FocusSound
import java.io.File
import java.nio.ShortBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusLoopTest {
    @Test
    fun aLoopIsSilentUntilDecodedThenFadesIn() {
        var pcm: ShortArray? = null
        val source = LoopSource({ pcm }, fadeFrames = 4)
        assertEquals(0f, source.next())
        pcm = ShortArray(8) { Short.MAX_VALUE }
        val heard = List(6) { source.next() }
        assertEquals(listOf(0.25f, 0.5f, 0.75f, 1f, 1f, 1f), heard)
    }

    @Test
    fun aLoopGoesRoundFromItsEndToItsStart() {
        val loop = shortArrayOf(1, 2, 3)
        val source = LoopSource({ loop }, fadeFrames = 0)
        val heard = List(7) { (source.next() * Short.MAX_VALUE).toInt() }
        assertEquals(listOf(1, 2, 3, 1, 2, 3, 1), heard)
    }

    @Test
    fun resamplingKeepsATonesPitchAndLevel() {
        for ((from, to) in listOf(48_000 to 24_000, 48_000 to 22_050, 48_000 to 44_100, 22_050 to 48_000)) {
            val input = tone(HZ, from, seconds = 1)
            val output = FocusResampler.resample(input, from, to)
            assertEquals("$from→$to length", to, output.size)
            assertEquals("$from→$to level", rms(input), rms(output), rms(input) * LEVEL_TOLERANCE)
            assertEquals("$from→$to pitch", HZ.toDouble(), zeroCrossings(output) / 2.0, HZ * PITCH_TOLERANCE)
        }
    }

    @Test
    fun goingDownRemovesWhatTheLowerRateCannotHold() {
        // 15 kHz fits 48 kHz but not 24 kHz (12 kHz top): it must not fold back as a 9 kHz whistle.
        val output = FocusResampler.resample(tone(15_000, 48_000, seconds = 1), 48_000, 24_000)
        assertTrue("left ${rms(output)}", rms(output) < ALIAS_LIMIT)
    }

    @Test
    fun aResampledLoopStillJoinsWithoutAClick() {
        // A whole number of cycles loops cleanly; the resampled copy must too, across its end.
        val output = FocusResampler.resample(tone(HZ, 48_000, seconds = 1), 48_000, 24_000)
        val seam = abs(output.first() - output.last())
        // The steepest step a clean 440 Hz sine at half scale takes at 24 kHz; a click would be far bigger.
        val steepest = 2 * PI * HZ / 24_000 * HALF * Short.MAX_VALUE
        assertTrue("seam $seam vs $steepest", seam <= steepest * SEAM_MARGIN)
    }

    @Test
    fun theSameRateIsACopy() {
        val input = shortArrayOf(5, -5, 7)
        assertArrayEquals(input, FocusResampler.resample(input, 24_000, 24_000))
    }

    @Test
    fun stereoIsAveragedToMono() {
        val builder = PcmBuilder()
        builder.addFrames(ShortBuffer.wrap(shortArrayOf(10, 20, -4, 0)), channels = 2)
        builder.addFrames(ShortBuffer.wrap(shortArrayOf(7)), channels = 1)
        assertArrayEquals(shortArrayOf(15, -2, 7), builder.build())
    }

    @Test
    fun everyRecordedSoundHasItsLoopAndCredit() {
        val credits = File(ASSETS, "${FocusLoopAssets.FOLDER}/CREDITS.md").readText()
        for (sound in FocusSound.entries) {
            val path = FocusLoopAssets.pathOf(sound) ?: continue
            val file = File(ASSETS, path)
            assertTrue("$path missing", file.isFile)
            assertTrue("$path is ${file.length()} bytes", file.length() in 1..MAX_LOOP_BYTES)
            assertTrue("$path not credited", file.name in credits)
        }
    }

    private fun tone(hz: Int, rate: Int, seconds: Int) = ShortArray(rate * seconds) { i ->
        (sin(2 * PI * hz * i / rate) * Short.MAX_VALUE * HALF).toInt().toShort()
    }

    private fun rms(samples: ShortArray): Double =
        sqrt(samples.sumOf { it.toDouble() * it } / samples.size) / Short.MAX_VALUE

    private fun zeroCrossings(samples: ShortArray): Int =
        (1 until samples.size).count { i -> (samples[i - 1] < 0) != (samples[i] < 0) }

    private companion object {
        const val HZ = 440
        const val HALF = 0.5
        const val LEVEL_TOLERANCE = 0.02
        const val PITCH_TOLERANCE = 0.01
        const val ALIAS_LIMIT = 0.01
        const val MAX_LOOP_BYTES = 700_000L
        const val SEAM_MARGIN = 1.05

        /** Unit tests run in the module's folder. */
        val ASSETS = File("src/main/assets")
    }
}
