// File: app/src/test/kotlin/io/github/skm0078/audora/widget/WidgetTimeLeftTest.kt
// Purpose: The widget's time left (CF-7) counts down by the time listened after the reader's estimate, never twice
//          for the same span, survives a reboot, and is written by the reader only now and then while reading.

package io.github.skm0078.audora.widget

import io.github.skm0078.audora.playback.controls.ListenedSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetTimeLeftTest {
    private val estimate = WidgetTimeLeft(BOOK, ms = 3_600_000, atElapsedMs = 100_000)

    @Test
    fun `only the listening after the estimate counts`() {
        val after = estimate.afterListening(BOOK, ListenedSpan(40_000, 400_000), nowElapsedMs = 400_000)
        assertEquals(3_600_000 - 300_000, after.ms)
        assertEquals(400_000, after.atElapsedMs)
    }

    @Test
    fun `the same span reported twice is subtracted once`() {
        val span = ListenedSpan(100_000, 400_000)
        val once = estimate.afterListening(BOOK, span, 400_000)
        assertEquals(once, once.afterListening(BOOK, span, 400_000))
    }

    @Test
    fun `another book's listening changes nothing`() {
        assertSame(estimate, estimate.afterListening("other", ListenedSpan(100_000, 400_000), 400_000))
    }

    @Test
    fun `it never goes below nothing left`() {
        val after = estimate.afterListening(BOOK, ListenedSpan(100_000, 100_000 + 5_000_000), 5_100_000)
        assertEquals(0, after.ms)
    }

    @Test
    fun `after a reboot the clock restarts and the whole span counts`() {
        val after = estimate.afterListening(BOOK, ListenedSpan(2_000, 62_000), nowElapsedMs = 62_000)
        assertEquals(3_600_000 - 60_000, after.ms)
        assertEquals(62_000, after.atElapsedMs)
    }

    @Test
    fun `the reader writes at a pause, while paused, and every interval while reading`() {
        val schedule = WidgetEstimateSchedule(intervalMs = 300_000)
        assertTrue("the first estimate", schedule.due(BOOK, reading = true, now = 0))
        assertFalse("not every sentence", schedule.due(BOOK, reading = true, now = 299_999))
        assertTrue("five minutes on", schedule.due(BOOK, reading = true, now = 300_000))
        assertTrue("a pause", schedule.due(BOOK, reading = false, now = 300_001))
        assertTrue("a jump while paused", schedule.due(BOOK, reading = false, now = 300_002))
        assertFalse(schedule.due(BOOK, reading = true, now = 300_003))
        assertTrue("another book at once", schedule.due("other", reading = true, now = 300_004))
    }

    private companion object {
        const val BOOK = "book-1"
    }
}
