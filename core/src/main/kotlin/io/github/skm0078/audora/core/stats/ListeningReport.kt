// File: core/src/main/kotlin/io/github/skm0078/audora/core/stats/ListeningReport.kt
// Purpose: What the stats screen and its share card show for a week, month, year or all time, worked out from the
//          slots listened.

package io.github.skm0078.audora.core.stats

import io.github.skm0078.audora.core.model.Lang
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** The tabs of the stats screen and the share card's span. */
enum class ListeningPeriod { WEEK, MONTH, YEAR, ALL }

/**
 * One bar of the chart.
 *
 * @property start the first day the bar covers: a day (week, month), a month's first day (year) or a year's (all time).
 * @property later the span has not started yet (the rest of this week), so it is drawn as a placeholder.
 */
data class ListeningBar(val start: LocalDate, val ms: Long, val later: Boolean)

/**
 * @property from the first day of the period; [to] is today.
 * @property ms listened in the period. [previousMs]: in the same number of days at the start of the period before,
 *   so "more than last week" compares like with like; null for all time.
 * @property bars week: 7 days; month: every day; year: 12 months; all time: one per year.
 * @property languages time per language heard, most first; time from before languages were kept is left out.
 * @property books the [ListeningStatsConfig.topBooks] most listened books' ids with their time, most first.
 * @property hours time per local hour of the day, 0 to 23; [peakStart] starts the busiest
 *   [ListeningStatsConfig.peakHours] hours (wrapping past midnight), null with no hours known.
 * @property daysListened days in the period that reached the streak minimum.
 */
data class ListeningReport(
    val period: ListeningPeriod,
    val from: LocalDate,
    val to: LocalDate,
    val ms: Long,
    val previousMs: Long?,
    val words: Long,
    val bars: List<ListeningBar>,
    val languages: List<Pair<Lang, Long>>,
    val books: List<Pair<String, Long>>,
    val hours: List<Long>,
    val peakStart: Int?,
    val daysListened: Int,
) {
    val isEmpty: Boolean get() = ms == 0L && words == 0L

    companion object {
        fun of(
            slots: Map<ListeningSlot, Heard>,
            period: ListeningPeriod,
            today: LocalDate,
            firstDayOfWeek: DayOfWeek,
            config: ListeningStatsConfig = ListeningStatsConfig(),
        ): ListeningReport {
            val from = startOf(period, slots, today, firstDayOfWeek)
            val inPeriod = slots.filterKeys { it.day in from..today }
            val hours = LongArray(HOURS_A_DAY).also { sums ->
                inPeriod.forEach { (slot, heard) -> slot.hour?.let { sums[it] += heard.ms } }
            }.toList()
            return ListeningReport(
                period = period,
                from = from,
                to = today,
                ms = inPeriod.values.sumOf { it.ms },
                previousMs = previousMs(slots, period, from, today),
                words = inPeriod.values.sumOf { it.words },
                bars = bars(inPeriod.msByDay(), period, from, today),
                languages = ranked(inPeriod) { it.lang },
                books = ranked(inPeriod) { it.book }.take(config.topBooks),
                hours = hours,
                peakStart = peakStart(hours, config.peakHours),
                daysListened = inPeriod.msByDay().count { it.value >= config.streakDayMs },
            )
        }

        private fun startOf(
            period: ListeningPeriod,
            slots: Map<ListeningSlot, Heard>,
            today: LocalDate,
            firstDayOfWeek: DayOfWeek,
        ): LocalDate = when (period) {
            ListeningPeriod.WEEK -> today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
            ListeningPeriod.MONTH -> today.withDayOfMonth(1)
            ListeningPeriod.YEAR -> today.withDayOfYear(1)
            ListeningPeriod.ALL -> slots.keys.minOfOrNull { it.day }?.withDayOfYear(1) ?: today.withDayOfYear(1)
        }

        private fun previousMs(
            slots: Map<ListeningSlot, Heard>,
            period: ListeningPeriod,
            from: LocalDate,
            today: LocalDate,
        ): Long? {
            val start = when (period) {
                ListeningPeriod.WEEK -> from.minusWeeks(1)
                ListeningPeriod.MONTH -> from.minusMonths(1)
                ListeningPeriod.YEAR -> from.minusYears(1)
                ListeningPeriod.ALL -> null
            } ?: return null
            // The same days into the period before; a short month ends at its own last day.
            val end = minOf(start.plusDays(ChronoUnit.DAYS.between(from, today)), from.minusDays(1))
            return slots.filterKeys { it.day in start..end }.values.sumOf { it.ms }
        }

        private fun bars(days: Map<LocalDate, Long>, period: ListeningPeriod, from: LocalDate, today: LocalDate) =
            when (period) {
                ListeningPeriod.WEEK -> (0L until DAYS_A_WEEK).map { from.plusDays(it) }.map { day ->
                    ListeningBar(day, days[day] ?: 0, later = day.isAfter(today))
                }

                ListeningPeriod.MONTH -> (0L until from.lengthOfMonth()).map { from.plusDays(it) }.map { day ->
                    ListeningBar(day, days[day] ?: 0, later = day.isAfter(today))
                }

                ListeningPeriod.YEAR -> (0L until MONTHS_A_YEAR).map { from.plusMonths(it) }.map { month ->
                    val ms = days.filterKeys { it.year == month.year && it.month == month.month }.values.sum()
                    ListeningBar(month, ms, later = month.isAfter(today))
                }

                ListeningPeriod.ALL -> (from.year..today.year).map { year ->
                    ListeningBar(LocalDate.of(year, 1, 1), days.filterKeys { it.year == year }.values.sum(), false)
                }
            }

        /** Time per known [key], most first; slots without one are left out. */
        private fun <K : Any> ranked(
            slots: Map<ListeningSlot, Heard>,
            key: (ListeningSlot) -> K?,
        ): List<Pair<K, Long>> {
            val sums = HashMap<K, Long>()
            slots.forEach { (slot, heard) -> key(slot)?.let { sums[it] = (sums[it] ?: 0) + heard.ms } }
            return sums.filterValues { it > 0 }.toList().sortedByDescending { it.second }
        }

        private fun peakStart(hours: List<Long>, width: Int): Int? {
            if (hours.all { it == 0L }) return null
            return hours.indices.maxBy { start -> (0 until width).sumOf { hours[(start + it) % HOURS_A_DAY] } }
        }

        private const val HOURS_A_DAY = 24
        private const val DAYS_A_WEEK = 7L
        private const val MONTHS_A_YEAR = 12L
    }
}
