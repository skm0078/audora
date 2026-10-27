// File: core/src/test/kotlin/io/github/skm0078/audora/core/stats/ListeningTallyTest.kt
// Purpose: Listening time counts only while reading, by the monotonic clock, split at local midnight (CF-8).

package io.github.skm0078.audora.core.stats

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningTallyTest {
    private val clock = FakeClock()
    private val tally = ListeningTally(clock)

    @Test
    fun `a session counts from the first report to the stop`() {
        clock.at(LocalDateTime.of(2026, 10, 26, 20, 0))
        assertTrue(tally.reading().isEmpty())
        assertTrue(tally.listening)

        clock.advance(MINUTES_10)
        assertEquals(mapOf(DAY to MINUTES_10), tally.reading())
        clock.advance(MINUTES_5)
        assertEquals(mapOf(DAY to MINUTES_5), tally.stopped())
        assertFalse(tally.listening)
    }

    @Test
    fun `time paused is never counted`() {
        clock.at(LocalDateTime.of(2026, 10, 26, 20, 0))
        tally.reading()
        clock.advance(MINUTES_5)
        tally.stopped()

        clock.advance(MINUTES_10 * 6)
        assertTrue(tally.stopped().isEmpty())
        tally.reading()
        clock.advance(MINUTES_5)
        assertEquals(mapOf(DAY to MINUTES_5), tally.stopped())
    }

    @Test
    fun `a session over midnight counts on both days`() {
        clock.at(LocalDateTime.of(2026, 10, 26, 23, 55))
        tally.reading()
        clock.advance(MINUTES_10)

        assertEquals(mapOf(DAY to MINUTES_5, DAY.plusDays(1) to MINUTES_5), tally.stopped())
    }

    @Test
    fun `moving the phone's clock adds no time`() {
        clock.at(LocalDateTime.of(2026, 10, 26, 20, 0))
        tally.reading()
        clock.advance(MINUTES_5)
        clock.jumpWall(hours = 3)

        assertEquals(MINUTES_5, tally.stopped().values.sum())
    }

    @Test
    fun `a stretch longer than a day without a report is capped`() {
        val config = ListeningStatsConfig()
        val capped = ListeningTally(clock, config)
        clock.at(LocalDateTime.of(2026, 10, 20, 12, 0))
        capped.reading()
        clock.advance(config.maxSegmentMs * 3)

        assertEquals(config.maxSegmentMs, capped.stopped().values.sum())
    }

    @Test
    fun `split by day handles long spans and zero`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val end = LocalDateTime.of(2026, 10, 28, 1, 0).atZone(zone).toInstant().toEpochMilli()
        val parts = ListeningTally.splitByDay(end, HOUR * 26, zone)

        assertEquals(
            mapOf(DAY to HOUR, DAY.plusDays(1) to HOUR * 24, DAY.plusDays(2) to HOUR),
            parts,
        )
        assertTrue(ListeningTally.splitByDay(end, 0, zone).isEmpty())
    }

    /** Elapsed and wall time move together unless [jumpWall] moves the wall clock alone. */
    private class FakeClock : ListeningClock {
        private val zone = ZoneId.of("Asia/Kolkata")
        private var elapsed = 1_000L
        private var wall = 0L

        fun at(time: LocalDateTime) {
            wall = time.atZone(zone).toInstant().toEpochMilli()
        }

        fun advance(ms: Long) {
            elapsed += ms
            wall += ms
        }

        fun jumpWall(hours: Long) {
            wall += hours * HOUR
        }

        override fun elapsedMillis() = elapsed

        override fun wallMillis() = wall

        override fun zone(): ZoneId = zone
    }

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 10, 26)
        const val MINUTES_5 = 5 * 60_000L
        const val MINUTES_10 = 10 * 60_000L
        const val HOUR = 60 * 60_000L
    }
}
