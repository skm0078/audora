// File: core/src/main/kotlin/io/github/skm0078/audora/core/stats/ListeningStatsConfig.kt
// Purpose: The tunables of listening stats and streaks (CF-8), in one place.

package io.github.skm0078.audora.core.stats

/**
 * @property streakDayMs listening a day needs to keep a streak: a minute, so a stray tap on Play does not count, and
 *   a short bus ride does.
 * @property maxSegmentMs the most one stretch between two reports may add; longer means a lost "stopped" report.
 * @property chartDays how many days the chart shows, ending today.
 */
data class ListeningStatsConfig(
    val streakDayMs: Long = MINUTE_MS,
    val maxSegmentMs: Long = DAY_MS,
    val chartDays: Int = WEEK_DAYS,
) {
    private companion object {
        const val MINUTE_MS = 60_000L
        const val DAY_MS = 24 * 60 * MINUTE_MS
        const val WEEK_DAYS = 7
    }
}
