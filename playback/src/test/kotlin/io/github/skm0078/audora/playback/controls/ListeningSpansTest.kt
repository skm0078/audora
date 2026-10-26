// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/controls/ListeningSpansTest.kt
// Purpose: Reading time reaches the widget (CF-7) in spans that never overlap: one per pause, one per interval.

package io.github.skm0078.audora.playback.controls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningSpansTest {
    private var now = 10_000L
    private val spans = ListeningSpans(intervalMs = INTERVAL, clock = { now })

    @Test
    fun `a pause reports the time since reading started`() {
        spans.onReading()
        now += 42_000
        assertEquals(ListenedSpan(10_000, 52_000), spans.onStopped())
        assertFalse(spans.reading)
        assertNull("a second stop reports nothing", spans.onStopped())
    }

    @Test
    fun `long reading reports every interval, then the rest at the pause`() {
        spans.onReading()
        now += INTERVAL - 1
        assertNull("not before the interval", spans.onTick())
        now += 1
        assertEquals(ListenedSpan(10_000, 10_000 + INTERVAL), spans.onTick())
        assertTrue(spans.reading)
        now += 7_000
        assertEquals(ListenedSpan(10_000 + INTERVAL, 17_000 + INTERVAL), spans.onStopped())
    }

    @Test
    fun `starting twice keeps the first start, and ticks while stopped report nothing`() {
        assertNull(spans.onTick())
        spans.onReading()
        now += 1_000
        spans.onReading()
        now += 1_000
        assertEquals(ListenedSpan(10_000, 12_000), spans.onStopped())
    }

    private companion object {
        const val INTERVAL = 300_000L
    }
}
