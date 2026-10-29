// File: core/src/main/kotlin/io/github/skm0078/audora/core/stats/ListeningStatsConfig.kt
// Purpose: The tunables of listening stats, streaks and milestones, in one place.

package io.github.skm0078.audora.core.stats

/**
 * @property streakDayMs listening a day needs to keep a streak: a minute, so a stray tap on Play does not count, and
 *   a short bus ride does.
 * @property maxSegmentMs the most one stretch between two reports may add; longer means a lost "stopped" report.
 * @property restDays one missed day a week, between two days listened, keeps the streak going (it is not counted):
 *   a habit that never punishes a single busy day.
 * @property calendarWeeks how many weeks the "every day you listened" calendar shows, ending this week.
 * @property calendarShadesMs the listening each darker shade of a calendar day starts at; under the first (the
 *   streak minimum) a day stays blank.
 * @property topBooks how many books "Most listened" names.
 * @property peakHours the width of "mostly 7 to 10 pm", in hours.
 * @property novelWords words in a typical novel, for "about 1 novel".
 * @property milestones what the badges ask for.
 */
data class ListeningStatsConfig(
    val streakDayMs: Long = MINUTE_MS,
    val maxSegmentMs: Long = DAY_MS,
    val restDays: Boolean = true,
    val calendarWeeks: Int = CALENDAR_WEEKS,
    val calendarShadesMs: List<Long> = CALENDAR_SHADES_MIN.map { it * MINUTE_MS },
    val topBooks: Int = TOP_BOOKS,
    val peakHours: Int = PEAK_HOURS,
    val novelWords: Long = NOVEL_WORDS,
    val milestones: MilestoneGoals = MilestoneGoals(),
) {
    private companion object {
        const val MINUTE_MS = 60_000L
        const val DAY_MS = 24 * 60 * MINUTE_MS
        const val CALENDAR_WEEKS = 20
        val CALENDAR_SHADES_MIN = listOf(1L, 15L, 30L, 60L)
        const val TOP_BOOKS = 3
        const val PEAK_HOURS = 3
        const val NOVEL_WORDS = 60_000L
    }
}

/** The badges' thresholds; each milestone is reached once and stays reached. */
data class MilestoneGoals(
    val shortStreakDays: Int = SHORT_STREAK,
    val longStreakDays: Int = LONG_STREAK,
    val hours: Int = HOURS,
    val languages: Int = LANGUAGES,
    val booksFinished: Int = BOOKS,
) {
    private companion object {
        const val SHORT_STREAK = 7
        const val LONG_STREAK = 30
        const val HOURS = 10
        const val LANGUAGES = 3
        const val BOOKS = 10
    }
}
