// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/session/ShakeDetectorTest.kt
// Purpose: Shake to extend (plan item 37): three hard jolts make a shake; bumps, slow jolts and echoes do not.

package io.github.skm0078.audora.playback.session

import io.github.skm0078.audora.core.config.SleepConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class ShakeDetectorTest {
    private val detector = ShakeDetector(SleepConfig(shakesNeeded = 3, shakeWindowMs = 1_500, shakeCooldownMs = 3_000))

    /** A sample of [g] times gravity along one axis. */
    private fun sample(g: Float, atMillis: Long) = detector.onSample(0f, 0f, g * 9.80665f, atMillis)

    @Test
    fun `three hard jolts in the window are one shake, then a pause before the next`() {
        val shakes = listOf(0L, 300L, 600L, 900L, 1_200L, 1_500L).map { sample(3f, it) }
        assertEquals(listOf(false, false, true, false, false, false), shakes)
        assertEquals(listOf(false, false, true), listOf(4_000L, 4_200L, 4_400L).map { sample(3f, it) })
    }

    @Test
    fun `lying still, walking and slow jolts are no shake`() {
        assertEquals(false, (0 until 50).map { sample(1f, it * 20L) }.any { it })
        assertEquals(false, (0 until 50).map { sample(1.3f, it * 20L) }.any { it })
        assertEquals(listOf(false, false, false), listOf(0L, 1_000L, 2_000L).map { sample(3f, it) })
    }
}
