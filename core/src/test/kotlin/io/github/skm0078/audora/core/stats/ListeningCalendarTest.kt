// File: core/src/test/kotlin/io/github/skm0078/audora/core/stats/ListeningCalendarTest.kt
// Purpose: The calendar's weeks end on this week, shade days by time listened, leave days to come blank, and count
//          this year's days listened.

package io.github.skm0078.audora.core.stats

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListeningCalendarTest {
    @Test
    fun `weeks, shades and days to come`() {
        val days = mapOf(
            TODAY to MIN * 61,
            TODAY.minusDays(1) to MIN * 20,
            TODAY.minusDays(2) to MIN * 59 / 60, // under a minute
            LocalDate.of(2025, 12, 31) to MIN * 5,
        )
        val calendar = ListeningCalendar.of(days, TODAY, DayOfWeek.MONDAY)

        assertEquals(20, calendar.weeks.size)
        val thisWeek = calendar.weeks.last()
        assertEquals(LocalDate.of(2026, 10, 26), thisWeek.first()?.date)
        assertEquals(listOf(0, 2, 4), thisWeek.take(3).map { it?.shade })
        thisWeek.drop(3).forEach { assertNull(it) }
        assertEquals(LocalDate.of(2026, 6, 15), calendar.weeks.first().first()?.date)
        assertEquals(4, calendar.shades)
        assertEquals("last year's day does not count", 2, calendar.daysThisYear)
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 10, 28)
        const val MIN = 60_000L
    }
}
