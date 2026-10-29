// File: core/src/test/kotlin/io/github/skm0078/audora/core/stats/ListeningTallyTest.kt
// Purpose: Listening time counts only while reading, by the monotonic clock, split at each hour, to the book and
//          language heard, with the words of each sentence (CF-8, stats screen).

package io.github.skm0078.audora.core.stats

import io.github.skm0078.audora.core.model.Lang
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

    private fun slot(hour: Int, book: String? = BOOK, lang: Lang? = null, day: LocalDate = DAY) =
        ListeningSlot(day, hour, book, lang)

    @Test
    fun `a session counts from the first report to the stop`() {
        clock.at(LocalDateTime.of(2026, 10, 26, 20, 0))
        tally.reading(BOOK)
        assertTrue(tally.drain().isEmpty())
        assertTrue(tally.listening)

        clock.advance(MINUTES_10)
        tally.reading(BOOK)
        assertEquals(mapOf(slot(20) to Heard(MINUTES_10)), tally.drain())
        clock.advance(MINUTES_5)
        tally.stopped()
        assertEquals(mapOf(slot(20) to Heard(MINUTES_5)), tally.drain())
        assertFalse(tally.listening)
        assertTrue("a drain empties it", tally.drain().isEmpty())
    }

    @Test
    fun `time paused is never counted`() {
        clock.at(LocalDateTime.of(2026, 10, 26, 20, 0))
        tally.reading(BOOK)
        clock.advance(MINUTES_5)
        tally.stopped()
        tally.drain()

        clock.advance(MINUTES_10 * 6)
        tally.stopped()
        assertTrue(tally.drain().isEmpty())
        tally.reading(BOOK)
        clock.advance(MINUTES_5)
        tally.stopped()
        assertEquals(mapOf(slot(21) to Heard(MINUTES_5)), tally.drain())
    }

    @Test
    fun `time goes to the language heard before each change, and words to the sentence's`() {
        clock.at(LocalDateTime.of(2026, 10, 26, 20, 0))
        tally.reading(BOOK)
        tally.sentence(BOOK, Lang.EN, words = 12)
        clock.advance(MINUTES_5)
        tally.sentence(BOOK, Lang.EN, words = 8)
        clock.advance(MINUTES_5)
        tally.sentence(BOOK, Lang.HI, words = 5)
        clock.advance(MINUTES_5)
        tally.stopped()

        assertEquals(
            mapOf(slot(20, lang = Lang.EN) to Heard(MINUTES_10, 20), slot(20, lang = Lang.HI) to Heard(MINUTES_5, 5)),
            tally.drain(),
        )
    }

    @Test
    fun `a new book starts its own time`() {
        clock.at(LocalDateTime.of(2026, 10, 26, 20, 0))
        tally.reading(BOOK)
        clock.advance(MINUTES_5)
        tally.reading(OTHER)
        clock.advance(MINUTES_10)
        tally.stopped()

        assertEquals(mapOf(slot(20) to Heard(MINUTES_5), slot(20, OTHER) to Heard(MINUTES_10)), tally.drain())
    }

    @Test
    fun `a sentence without a page report still starts the clock`() {
        clock.at(LocalDateTime.of(2026, 10, 26, 20, 0))
        tally.sentence(BOOK, Lang.BN, words = 0)
        clock.advance(MINUTES_5)
        tally.stopped()

        assertEquals(mapOf(slot(20, lang = Lang.BN) to Heard(MINUTES_5)), tally.drain())
    }

    @Test
    fun `a session over midnight counts on both days, by hour`() {
        clock.at(LocalDateTime.of(2026, 10, 26, 23, 55))
        tally.reading(BOOK)
        clock.advance(MINUTES_10)
        tally.stopped()

        assertEquals(
            mapOf(slot(23) to Heard(MINUTES_5), slot(0, day = DAY.plusDays(1)) to Heard(MINUTES_5)),
            tally.drain(),
        )
    }

    @Test
    fun `moving the phone's clock adds no time`() {
        clock.at(LocalDateTime.of(2026, 10, 26, 20, 0))
        tally.reading(BOOK)
        clock.advance(MINUTES_5)
        clock.jumpWall(hours = 3)
        tally.stopped()

        assertEquals(MINUTES_5, tally.drain().values.sumOf { it.ms })
    }

    @Test
    fun `a stretch longer than a day without a report is capped`() {
        val config = ListeningStatsConfig()
        val capped = ListeningTally(clock, config)
        clock.at(LocalDateTime.of(2026, 10, 20, 12, 0))
        capped.reading(BOOK)
        clock.advance(config.maxSegmentMs * 3)
        capped.stopped()

        assertEquals(config.maxSegmentMs, capped.drain().values.sumOf { it.ms })
    }

    @Test
    fun `split by hour handles long spans, part hours and zero`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val end = LocalDateTime.of(2026, 10, 27, 1, 30).atZone(zone).toInstant().toEpochMilli()
        val parts = ListeningTally.splitByHour(end, HOUR * 2 + MINUTES_10, zone)

        assertEquals(
            mapOf(
                LocalDateTime.of(2026, 10, 26, 23, 0) to MINUTES_10 * 4,
                LocalDateTime.of(2026, 10, 27, 0, 0) to HOUR,
                LocalDateTime.of(2026, 10, 27, 1, 0) to MINUTES_10 * 3,
            ),
            parts,
        )
        assertTrue(ListeningTally.splitByHour(end, 0, zone).isEmpty())
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
        const val BOOK = "book-1"
        const val OTHER = "book-2"
        const val MINUTES_5 = 5 * 60_000L
        const val MINUTES_10 = 10 * 60_000L
        const val HOUR = 60 * 60_000L
    }
}
