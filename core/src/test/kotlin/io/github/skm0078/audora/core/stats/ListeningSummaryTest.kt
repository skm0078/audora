// File: core/src/test/kotlin/io/github/skm0078/audora/core/stats/ListeningSummaryTest.kt
// Purpose: Today, this week, the chart and streaks from the days listened (CF-8), table by table.

package io.github.skm0078.audora.core.stats

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningSummaryTest {
    private fun summary(days: Map<LocalDate, Long>, firstDay: DayOfWeek = DayOfWeek.MONDAY) =
        ListeningSummary.of(days, TODAY, firstDay)

    /** Monday 26 October 2026 is [TODAY]; each key is days before it. */
    private fun daysBack(vararg listened: Pair<Int, Long>): Map<LocalDate, Long> =
        listened.associate { (back, ms) -> TODAY.minusDays(back.toLong()) to ms }

    @Test
    fun `nothing listened is empty with no streak`() {
        val empty = summary(emptyMap())
        assertTrue(empty.isEmpty)
        assertEquals(0, empty.currentStreak)
        assertEquals(0, empty.longestStreak)
        assertEquals(7, empty.chart.size)
        assertEquals(TODAY, empty.chart.last().first)
    }

    @Test
    fun `today and this week follow the phone's first day of the week`() {
        val days = daysBack(0 to MIN * 20, 1 to MIN * 30, 3 to MIN * 15, 9 to MIN * 60)
        // Today is a Monday: a Monday week holds today only, a Sunday week also holds yesterday.
        assertEquals(MIN * 20, summary(days).todayMs)
        assertEquals(MIN * 20, summary(days).weekMs)
        assertEquals(MIN * 50, summary(days, DayOfWeek.SUNDAY).weekMs)
        assertEquals(MIN * 125, summary(days).totalMs)
    }

    @Test
    fun `streaks count days in a row with at least a minute`() {
        val cases = listOf(
            // days listened (back from today) to (current, longest)
            daysBack(0 to MIN, 1 to MIN, 2 to MIN) to (3 to 3),
            // Today not listened yet: the streak up to yesterday still stands.
            daysBack(1 to MIN, 2 to MIN) to (2 to 2),
            // A gap yesterday breaks it.
            daysBack(0 to MIN, 2 to MIN, 3 to MIN, 4 to MIN) to (1 to 3),
            // Under a minute does not count, and breaks a run.
            daysBack(0 to MIN, 1 to MIN - 1, 2 to MIN) to (1 to 1),
            daysBack(5 to MIN, 6 to MIN) to (0 to 2),
        )
        cases.forEach { (days, expected) ->
            val result = summary(days)
            assertEquals("streaks of $days", expected, result.currentStreak to result.longestStreak)
        }
    }

    @Test
    fun `the chart is the last seven days, oldest first, zero where nothing was heard`() {
        val chart = summary(daysBack(0 to MIN * 3, 6 to MIN * 2, 7 to MIN * 9)).chart

        assertEquals((6 downTo 0).map { TODAY.minusDays(it.toLong()) }, chart.map { it.first })
        assertEquals(listOf(MIN * 2, 0, 0, 0, 0, 0, MIN * 3), chart.map { it.second })
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 10, 26)
        const val MIN = 60_000L
    }
}
