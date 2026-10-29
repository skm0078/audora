// File: app/src/test/kotlin/io/github/skm0078/audora/privacy/LockClockTest.kt
// Purpose: The lock screen's clock wakes at each minute's turn, never spins and never sleeps past it.

package io.github.skm0078.audora.privacy

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class LockClockTest {
    private fun at(hour: Int, minute: Int, second: Int, nanos: Int = 0): ZonedDateTime =
        ZonedDateTime.of(2026, 10, 28, hour, minute, second, nanos, ZoneId.of("Asia/Kolkata"))

    @Test
    fun `it wakes at the start of the next minute`() {
        assertEquals(30_000L, untilNextMinuteMs(at(10, 42, 30)))
        assertEquals(500L, untilNextMinuteMs(at(10, 42, 59, 500_000_000)))
    }

    @Test
    fun `on the minute itself it waits the whole minute`() {
        assertEquals(60_000L, untilNextMinuteMs(at(10, 42, 0)))
    }

    @Test
    fun `it never returns zero, so the loop cannot spin`() {
        assertEquals(1L, untilNextMinuteMs(at(23, 59, 59, 999_999_999)))
    }

    @Test
    fun `midnight rolls over to the next day`() {
        assertEquals(15_000L, untilNextMinuteMs(at(23, 59, 45)))
    }
}
