// File: core/src/test/kotlin/io/github/skm0078/audora/core/stats/ListeningReportTest.kt
// Purpose: What the stats screen shows for a week, month, year and all time: totals, comparisons, bars, languages,
//          top books, hours of the day and days listened.

package io.github.skm0078.audora.core.stats

import io.github.skm0078.audora.core.model.Lang
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningReportTest {
    private fun report(slots: Map<ListeningSlot, Heard>, period: ListeningPeriod, today: LocalDate = TODAY) =
        ListeningReport.of(slots, period, today, DayOfWeek.MONDAY)

    private fun at(day: LocalDate, hour: Int?, ms: Long, book: String? = "a", lang: Lang? = Lang.EN) =
        ListeningSlot(day, hour, book, lang) to Heard(ms)

    private fun Pair<ListeningSlot, Heard>.withWords(words: Long) = first to second.copy(words = words)

    @Test
    fun `nothing listened is empty`() {
        val empty = report(emptyMap(), ListeningPeriod.WEEK)

        assertTrue(empty.isEmpty)
        assertEquals(7, empty.bars.size)
        assertNull(empty.peakStart)
        assertEquals(0, empty.daysListened)
        assertEquals(0L, empty.previousMs)
    }

    @Test
    fun `a week starts on the first day of the week and marks the days still to come`() {
        val slots = mapOf(
            at(TODAY, 9, MIN * 10).withWords(100),
            at(TODAY.minusDays(2), 21, MIN * 20).withWords(50), // Monday
            at(TODAY.minusDays(3), 21, MIN * 40), // Sunday: last week
        )
        val week = report(slots, ListeningPeriod.WEEK)

        assertEquals(LocalDate.of(2026, 10, 26), week.from)
        assertEquals(MIN * 30, week.ms)
        assertEquals(150L, week.words)
        assertEquals(listOf(MIN * 20, 0, MIN * 10, 0, 0, 0, 0), week.bars.map { it.ms })
        assertEquals(listOf(false, false, false, true, true, true, true), week.bars.map { it.later })
        assertEquals(2, week.daysListened)
    }

    @Test
    fun `the comparison takes as many days of the period before`() {
        // Today is the 3rd day of the week: last week's Monday to Wednesday count, its Thursday does not.
        val slots = mapOf(
            at(LocalDate.of(2026, 10, 19), 8, MIN * 5),
            at(LocalDate.of(2026, 10, 21), 8, MIN * 7),
            at(LocalDate.of(2026, 10, 22), 8, MIN * 100),
        )
        assertEquals(MIN * 12, report(slots, ListeningPeriod.WEEK).previousMs)

        // 31 March against February: February ends at its own last day.
        val march = LocalDate.of(2027, 3, 31)
        val feb = mapOf(at(LocalDate.of(2027, 2, 28), 8, MIN), at(LocalDate.of(2027, 3, 1), 8, MIN * 3))
        assertEquals(MIN, report(feb, ListeningPeriod.MONTH, march).previousMs)
        assertNull(report(feb, ListeningPeriod.ALL, march).previousMs)
    }

    @Test
    fun `month, year and all time bars`() {
        val slots = mapOf(
            at(LocalDate.of(2026, 10, 1), 8, MIN),
            at(LocalDate.of(2026, 3, 5), 8, MIN * 2),
            at(LocalDate.of(2024, 6, 1), null, MIN * 3, book = null, lang = null),
        )

        val month = report(slots, ListeningPeriod.MONTH)
        assertEquals(31, month.bars.size)
        assertEquals(MIN, month.bars.first().ms)
        assertTrue(month.bars.last().later)

        val year = report(slots, ListeningPeriod.YEAR)
        assertEquals(12, year.bars.size)
        assertEquals(MIN * 2, year.bars[2].ms)
        assertEquals(MIN, year.bars[9].ms)
        assertTrue(year.bars[10].later)

        val all = report(slots, ListeningPeriod.ALL)
        assertEquals(LocalDate.of(2024, 1, 1), all.from)
        assertEquals(listOf(MIN * 3, 0, MIN * 3), all.bars.map { it.ms })
        assertEquals(MIN * 6, all.ms)
    }

    @Test
    fun `languages and books are ranked, and old day-only time counts only in totals`() {
        val slots = mapOf(
            at(TODAY, 8, MIN * 5, book = "a", lang = Lang.EN),
            at(TODAY, 9, MIN * 9, book = "b", lang = Lang.BN),
            at(TODAY, 10, MIN * 2, book = "c", lang = Lang.HI),
            at(TODAY, 11, MIN, book = "d", lang = Lang.HI),
            at(TODAY, null, MIN * 50, book = null, lang = null),
        )
        val week = report(slots, ListeningPeriod.WEEK)

        assertEquals(listOf(Lang.BN to MIN * 9, Lang.EN to MIN * 5, Lang.HI to MIN * 3), week.languages)
        assertEquals(listOf("b" to MIN * 9, "a" to MIN * 5, "c" to MIN * 2), week.books)
        assertEquals(4, week.booksHeard)
        assertEquals(MIN * 67, week.ms)
        assertEquals(MIN * 17, week.hours.sum())
    }

    @Test
    fun `the peak is the busiest run of hours, across midnight too`() {
        val evening = mapOf(at(TODAY, 20, MIN), at(TODAY, 21, MIN * 5), at(TODAY, 22, MIN * 5), at(TODAY, 8, MIN * 6))
        assertEquals(20, report(evening, ListeningPeriod.WEEK).peakStart)

        val late = mapOf(at(TODAY, 23, MIN * 5), at(TODAY, 0, MIN * 5), at(TODAY, 1, MIN * 5), at(TODAY, 12, MIN * 9))
        assertEquals(23, report(late, ListeningPeriod.WEEK).peakStart)
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 10, 28)
        const val MIN = 60_000L
    }
}
