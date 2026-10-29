// File: core/src/main/kotlin/io/github/skm0078/audora/core/stats/WeekGlance.kt
// Purpose: The home's "This week" (personal touch 7): the week's listening against the goal, and the current
//          streak, worked out the same way as the listening stats screen so the two never disagree.

package io.github.skm0078.audora.core.stats

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * [ms] listened since the week began on its first day, [goalMs] the weekly goal (null with none), [streak] the
 * current streak in days.
 */
data class WeekGlance(val ms: Long, val goalMs: Long?, val streak: Int) {
    /** How much of the goal is done, 0 up (past 1 once it is passed); null with no goal. */
    val progress: Float? get() = goalMs?.let { ms.toFloat() / it }

    companion object {
        /** Null while nothing was ever listened: a new listener's home stays as light as it was. */
        fun of(
            slots: Map<ListeningSlot, Heard>,
            weeklyGoalHours: Int,
            today: LocalDate,
            firstDayOfWeek: DayOfWeek,
            config: ListeningStatsConfig = ListeningStatsConfig(),
        ): WeekGlance? {
            val byDay = slots.msByDay().filterValues { it > 0 }
            if (byDay.isEmpty()) return null
            val from = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
            val listened = byDay.filterValues { it >= config.streakDayMs }.keys
            return WeekGlance(
                ms = byDay.filterKeys { it in from..today }.values.sum(),
                goalMs = weeklyGoalHours.takeIf { it > 0 }?.let { it * HOUR_MS },
                streak = ListeningStreaks.of(listened, today, firstDayOfWeek, config.restDays).current,
            )
        }

        private const val HOUR_MS = 60 * 60_000L
    }
}
