// File: core/src/main/kotlin/io/github/skm0078/audora/core/stats/ListeningCalendar.kt
// Purpose: "Every day you listened": the last weeks as columns of days, each shaded by how long it was listened.

package io.github.skm0078.audora.core.stats

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * @property weeks oldest first; each holds its 7 days from the first day of the week. A day's [Day.shade] is 0 (not
 *   listened, or under the streak minimum) to [ListeningStatsConfig.calendarShadesMs]'s size; days after today are
 *   null, so the grid keeps its shape without claiming days not lived yet.
 * @property daysThisYear days of today's year that reached the streak minimum.
 */
data class ListeningCalendar(val weeks: List<List<Day?>>, val daysThisYear: Int) {
    data class Day(val date: LocalDate, val ms: Long, val shade: Int)

    val shades: Int get() = weeks.flatten().maxOfOrNull { it?.shade ?: 0 } ?: 0

    companion object {
        fun of(
            msByDay: Map<LocalDate, Long>,
            today: LocalDate,
            firstDayOfWeek: DayOfWeek,
            config: ListeningStatsConfig = ListeningStatsConfig(),
        ): ListeningCalendar {
            val thisWeek = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
            val first = thisWeek.minusWeeks(config.calendarWeeks - 1L)
            val weeks = (0 until config.calendarWeeks).map { week ->
                (0 until DAYS_A_WEEK).map { offset ->
                    val date = first.plusWeeks(week.toLong()).plusDays(offset.toLong())
                    if (date.isAfter(today)) null else dayOf(date, msByDay[date] ?: 0, config.calendarShadesMs)
                }
            }
            val minimum = config.streakDayMs
            val thisYear = msByDay.count { (day, ms) -> day.year == today.year && !day.isAfter(today) && ms >= minimum }
            return ListeningCalendar(weeks, thisYear)
        }

        private fun dayOf(date: LocalDate, ms: Long, shadesMs: List<Long>) = Day(date, ms, shadesMs.count { ms >= it })

        private const val DAYS_A_WEEK = 7
    }
}
