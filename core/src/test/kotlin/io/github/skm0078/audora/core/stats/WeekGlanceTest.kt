// File: core/src/test/kotlin/io/github/skm0078/audora/core/stats/WeekGlanceTest.kt
// Purpose: The home's "This week" counts from the week's first day, matches the stats screen, and stays away until
//          something was listened.

package io.github.skm0078.audora.core.stats

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeekGlanceTest {
    private fun heard(daysBack: Long, minutes: Long) =
        ListeningSlot(TODAY.minusDays(daysBack)) to Heard(minutes * MINUTE)

    private fun glance(vararg heard: Pair<ListeningSlot, Heard>, goalHours: Int = 5, firstDay: DayOfWeek = MONDAY) =
        WeekGlance.of(heard.toList().sumHeard(), goalHours, TODAY, firstDay)

    @Test
    fun `nothing ever listened shows nothing`() {
        assertNull(glance())
        assertNull(glance(heard(0, 0)))
    }

    @Test
    fun `the week counts from its first day, today included`() {
        // Wednesday: Monday and Tuesday count, last Sunday does not.
        val week = glance(heard(0, 30), heard(1, 20), heard(2, 10), heard(3, 45))
        assertEquals(60 * MINUTE, week?.ms)
        assertEquals(4, week?.streak)
    }

    @Test
    fun `a week starting on Sunday takes Sunday in`() {
        assertEquals(
            105 * MINUTE,
            glance(heard(0, 30), heard(1, 20), heard(2, 10), heard(3, 45), firstDay = SUNDAY)?.ms,
        )
    }

    @Test
    fun `the goal is the weekly hours and none is no ring`() {
        val week = glance(heard(0, 150), goalHours = 5)
        assertEquals(5 * 60 * MINUTE, week?.goalMs)
        assertEquals(0.5f, week?.progress)
        assertNull(glance(heard(0, 150), goalHours = 0)?.progress)
    }

    @Test
    fun `listened before this week still shows this week at zero`() {
        val week = glance(heard(10, 40))
        assertEquals(0L, week?.ms)
        assertEquals(0, week?.streak)
    }

    @Test
    fun `it agrees with the stats screen's week`() {
        val slots = listOf(heard(0, 30), heard(1, 20), heard(4, 45)).sumHeard()
        val report = ListeningReport.of(slots, ListeningPeriod.WEEK, TODAY, MONDAY)
        assertEquals(report.ms, WeekGlance.of(slots, 5, TODAY, MONDAY)?.ms)
    }

    private companion object {
        /** A Wednesday. */
        val TODAY: LocalDate = LocalDate.of(2026, 10, 28)
        val MONDAY = DayOfWeek.MONDAY
        val SUNDAY = DayOfWeek.SUNDAY
        const val MINUTE = 60_000L
    }
}
