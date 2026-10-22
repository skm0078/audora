// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/session/SentenceTimingsTest.kt
// Purpose: The timings record keeps the newest sentences, replaces a re-render and tells its listener.

package io.github.skm0078.audora.playback.session

import io.github.skm0078.audora.playback.contract.SentenceTiming
import io.github.skm0078.audora.playback.queue.UnitKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SentenceTimingsTest {
    @Test
    fun `keeps the newest sentences up to its capacity`() {
        val timings = SentenceTimings(capacity = 2)
        (0..2).forEach { timings.record(UnitKey(0, it), timing(it)) }

        assertNull(timings.of(UnitKey(0, 0)))
        assertEquals(timing(2), timings.of(UnitKey(0, 2)))
    }

    @Test
    fun `a re-render replaces the entry and the listener hears of each`() {
        val heard = ArrayList<UnitKey>()
        val timings = SentenceTimings().apply { onRecorded = { heard += it } }

        timings.record(UnitKey(1, 4), timing(1))
        timings.record(UnitKey(1, 4), timing(2))

        assertEquals(timing(2), timings.of(UnitKey(1, 4)))
        assertEquals(listOf(UnitKey(1, 4), UnitKey(1, 4)), heard)
    }

    private fun timing(speechMs: Int) = SentenceTiming(0, 0, 0, 0, speechMs.toLong(), speechMs.toLong())
}
