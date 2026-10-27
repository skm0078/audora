// File: core/src/main/kotlin/io/github/skm0078/audora/core/stats/ListeningSummary.kt
// Purpose: What the Listening stats sheet shows, worked out from the days listened (CF-8).

package io.github.skm0078.audora.core.stats

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * @property todayMs listened today.
 * @property weekMs listened this week, from the phone's first day of the week up to today.
 * @property chart the last [ListeningStatsConfig.chartDays] days, oldest first, ending today.
 * @property currentStreak days in a row with at least [ListeningStatsConfig.streakDayMs], ending today, or yesterday
 *   while today has not reached it yet (the streak is not lost until the day is over).
 * @property longestStreak the longest such run ever.
 * @property totalMs listened in all.
 */
data class ListeningSummary(
    val todayMs: Long,
    val weekMs: Long,
    val chart: List<Pair<LocalDate, Long>>,
    val currentStreak: Int,
    val longestStreak: Int,
    val totalMs: Long,
) {
    val isEmpty: Boolean get() = totalMs == 0L

    companion object {
        fun of(
            days: Map<LocalDate, Long>,
            today: LocalDate,
            firstDayOfWeek: DayOfWeek,
            config: ListeningStatsConfig = ListeningStatsConfig(),
        ): ListeningSummary {
            val weekStart = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
            val counted = days.filterValues { it >= config.streakDayMs }.keys
            return ListeningSummary(
                todayMs = days[today] ?: 0,
                weekMs = days.filterKeys { it in weekStart..today }.values.sum(),
                chart = (config.chartDays - 1 downTo 0).map { back ->
                    today.minusDays(back.toLong()).let { it to (days[it] ?: 0) }
                },
                currentStreak = currentStreak(counted, today),
                longestStreak = longestStreak(counted),
                totalMs = days.values.sum(),
            )
        }

        private fun currentStreak(counted: Set<LocalDate>, today: LocalDate): Int {
            var day = if (today in counted) today else today.minusDays(1)
            var streak = 0
            while (day in counted) {
                streak++
                day = day.minusDays(1)
            }
            return streak
        }

        private fun longestStreak(counted: Set<LocalDate>): Int {
            var longest = 0
            var run = 0
            var previous: LocalDate? = null
            for (day in counted.sorted()) {
                run = if (previous != null && day == previous.plusDays(1)) run + 1 else 1
                longest = maxOf(longest, run)
                previous = day
            }
            return longest
        }
    }
}
