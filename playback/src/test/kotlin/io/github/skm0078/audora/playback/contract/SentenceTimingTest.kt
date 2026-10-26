// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/contract/SentenceTimingTest.kt
// Purpose: A sentence's timing round-trips, rejects bad lines, and maps clip time to how far the speech has got.

package io.github.skm0078.audora.playback.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SentenceTimingTest {
    private val timing =
        SentenceTiming(page = 3, charStart = 120, unitIndex = 4, leadMs = 200, speechMs = 1_000, totalMs = 1_500)

    @Test
    fun `round trips`() {
        assertEquals(timing, SentenceTiming.decode(timing.encode()))
    }

    @Test
    fun `word starts round trip, and a line without them is still read`() {
        val timed = timing.copy(wordStartsMs = listOf(0, 180, 520))
        assertEquals(timed, SentenceTiming.decode(timed.encode()))
        assertEquals(emptyList<Long>(), SentenceTiming.decode(timing.encode())?.wordStartsMs)
    }

    @Test
    fun `the word said is the last one started, counted from the end of the cue`() {
        val timed = timing.copy(wordStartsMs = listOf(0, 180, 520))
        val cases = listOf(0L to 0, 200L to 0, 379L to 0, 380L to 1, 719L to 1, 720L to 2, 1_499L to 2)
        cases.forEach { (ms, word) -> assertEquals("at $ms", word, timed.wordAt(ms)) }
        assertNull(timing.wordAt(700))
    }

    @Test
    fun `a missing or malformed line is no timing`() {
        val malformed = listOf(
            null,
            "",
            "3;120;200;1000;1500", // the five-field line from before the unit index
            "3;120;4;x;1000;1500",
            "3;120;4;200;1000;900",
            "-1;0;0;0;0;0",
            "3;120;4;200;1000;1500;0,x", // a broken word start
            "3;120;4;200;1000;1500;0;5",
        )
        malformed.forEach {
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
        val cue = SentenceTiming(0, 0, 0, leadMs = 300, speechMs = 0, totalMs = 700)
        assertEquals(0f, cue.speechFraction(100), EPS)
        assertEquals(1f, cue.speechFraction(400), EPS)
    }

    private companion object {
        const val EPS = 1e-6f
    }
}
