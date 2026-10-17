// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/contract/SentenceTimingTest.kt
// Purpose: A sentence's timing round-trips, rejects bad lines, and maps clip time to how far the speech has got.

package io.github.skm0078.audora.playback.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SentenceTimingTest {
    private val timing = SentenceTiming(page = 3, charStart = 120, leadMs = 200, speechMs = 1_000, totalMs = 1_500)

    @Test
    fun `round trips`() {
        assertEquals(timing, SentenceTiming.decode(timing.encode()))
    }

    @Test
    fun `a missing or malformed line is no timing`() {
        listOf(null, "", "3;120;200;1000", "3;120;x;1000;1500", "3;120;200;1000;900", "-1;0;0;0;0").forEach {
            assertNull(it, SentenceTiming.decode(it))
        }
    }

    @Test
    fun `clip time maps to the share of the speech heard`() {
        assertEquals(0f, timing.speechFraction(0), EPS)
        assertEquals(0f, timing.speechFraction(200), EPS)
        assertEquals(0.5f, timing.speechFraction(700), EPS)
        assertEquals(1f, timing.speechFraction(1_200), EPS)
        assertEquals(1f, timing.speechFraction(1_499), EPS)
    }

    @Test
    fun `a cue alone has no speech to move through`() {
        val cue = SentenceTiming(0, 0, leadMs = 300, speechMs = 0, totalMs = 700)
        assertEquals(0f, cue.speechFraction(100), EPS)
        assertEquals(1f, cue.speechFraction(400), EPS)
    }

    private companion object {
        const val EPS = 1e-6f
    }
}
