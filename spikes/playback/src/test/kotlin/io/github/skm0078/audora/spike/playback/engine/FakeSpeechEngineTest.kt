// File: spikes/playback/src/test/kotlin/io/github/skm0078/audora/spike/playback/engine/FakeSpeechEngineTest.kt
// Purpose: The fake engine must take RTF × audio length to render, which is the whole point of the spike.

package io.github.skm0078.audora.spike.playback.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeSpeechEngineTest {
    private class FakeClock : RenderClock {
        var now = 0L
        val spent = mutableListOf<Long>()

        override fun nowNanos() = now

        override fun spend(nanos: Long) {
            spent += nanos
            now += nanos.coerceAtLeast(0)
        }
    }

    @Test
    fun `slow engine spends rtf times the audio length`() {
        val clock = FakeClock()
        val engine = FakeSpeechEngine(sampleRateHz = 8_000, realTimeFactor = 1.5, clock = clock)

        engine.render(FakeUnit(index = 0, page = 0, durationMs = 2_000))

        assertEquals(listOf(3_000_000_000L), clock.spent)
    }

    @Test
    fun `pcm length matches the unit duration`() {
        val engine = FakeSpeechEngine(sampleRateHz = 8_000, realTimeFactor = 0.1, clock = FakeClock())

        val pcm = engine.render(FakeUnit(index = 3, page = 0, durationMs = 1_500))

        assertEquals(12_000, pcm.size)
        assertTrue("expected audible samples", pcm.any { it.toInt() != 0 })
    }
}
