// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/focus/FocusNoiseTest.kt
// Purpose: Every focus sound stays in range, is about as loud as the others, and repeats for the same seed.

package io.github.skm0078.audora.playback.focus

import io.github.skm0078.audora.core.model.FocusSound
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusNoiseTest {
    private val sounds = FocusSound.entries - FocusSound.OFF

    @Test
    fun offMakesNoSource() {
        assertNull(FocusNoise.of(FocusSound.OFF, RATE, SEED))
    }

    @Test
    fun everySoundStaysInRangeAndIsAboutAsLoudAsTheOthers() {
        for (rate in listOf(16_000, 22_050, 24_000, 44_100)) {
            for (sound in sounds) {
                val samples = render(sound, rate)
                val rms = sqrt(samples.sumOf { it.toDouble() * it } / samples.size)
                assertTrue("$sound @ $rate out of range", samples.all { it in -1f..1f })
                assertTrue("$sound @ $rate rms $rms", rms in MIN_RMS..MAX_RMS)
            }
        }
    }

    @Test
    fun theSameSeedGivesTheSameSound() {
        for (sound in sounds) assertEquals(render(sound, RATE).toList(), render(sound, RATE).toList())
    }

    @Test
    fun brownNoiseNeverDriftsOff() {
        val samples = render(FocusSound.BROWN_NOISE, RATE, seconds = 60)
        val mean = samples.average()
        assertTrue("mean $mean", abs(mean) < MAX_DRIFT)
    }

    private fun render(sound: FocusSound, rate: Int, seconds: Int = 10): FloatArray {
        val source = checkNotNull(FocusNoise.of(sound, rate, SEED))
        return FloatArray(rate * seconds) { source.next() }
    }

    private companion object {
        const val RATE = 22_050
        const val SEED = 7L
        const val MIN_RMS = 0.1
        const val MAX_RMS = 0.35
        const val MAX_DRIFT = 0.05
    }
}
