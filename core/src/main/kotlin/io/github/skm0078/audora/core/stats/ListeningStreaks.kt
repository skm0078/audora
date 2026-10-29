// File: core/src/main/kotlin/io/github/skm0078/audora/core/stats/ListeningStreaks.kt
// Purpose: Days in a row listened, now and at best, with one forgiven rest day a week (the design doc's gentle habit).

package io.github.skm0078.audora.core.stats

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * @property current days listened in the run that reaches today; today counts once it is listened, and until the day
 *   is over a run that reached yesterday is not lost.
 * @property best the longest run ever.
 * @property restDaysUsed the days forgiven inside the current run (shown as "1 rest day").
 */
data class ListeningStreaks(val current: Int, val best: Int, val restDaysUsed: Int) {
    companion object {
        /**
         * [listened] are the days that reached the streak minimum. With rest days on, a single missed day between two
         * days listened keeps the run going (not counted in it) once per week starting on [firstDayOfWeek]. Today is
         * still open: as a neighbour it counts as listened, so missing yesterday does not end the run before today
         * is over.
         */
        fun of(
            listened: Set<LocalDate>,
            today: LocalDate,
            firstDayOfWeek: DayOfWeek,
            restDays: Boolean,
        ): ListeningStreaks {
            val first = listened.minOrNull() ?: return ListeningStreaks(0, 0, 0)
            val restWeeks = HashSet<LocalDate>()
            val open = { day: LocalDate -> day in listened || day == today }
            var run = 0
            var rests = 0
            var best = 0
            var day = first
            while (!day.isAfter(today)) {
                val weekStart = day.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
                val restDay = restDays && weekStart !in restWeeks && open(day.minusDays(1)) && open(day.plusDays(1))
                when {
                    day in listened -> {
                        run++
                        best = maxOf(best, run)
                    }

                    day == today -> Unit

                    restDay -> {
                        rests++
                        restWeeks += weekStart
                    }

                    else -> {
                        run = 0
                        rests = 0
                        restWeeks.clear()
                    }
                }
                day = day.plusDays(1)
            }
            return ListeningStreaks(run, best, rests)
        }
    }
}
