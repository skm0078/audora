// File: app/src/main/kotlin/io/github/skm0078/audora/ui/stats/StatsData.kt
// Purpose: Everything the stats screen and its share card show for one period, worked out together off the main
//          thread from the listening log, the library and the books' places.

package io.github.skm0078.audora.ui.stats

import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookProgress
import io.github.skm0078.audora.core.resume.ResumeAnchor
import io.github.skm0078.audora.core.stats.Heard
import io.github.skm0078.audora.core.stats.ListeningCalendar
import io.github.skm0078.audora.core.stats.ListeningMilestones
import io.github.skm0078.audora.core.stats.ListeningPeriod
import io.github.skm0078.audora.core.stats.ListeningReport
import io.github.skm0078.audora.core.stats.ListeningSlot
import io.github.skm0078.audora.core.stats.ListeningStatsConfig
import io.github.skm0078.audora.core.stats.ListeningStreaks
import io.github.skm0078.audora.core.stats.Milestone
import io.github.skm0078.audora.core.stats.msByDay
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What the stats are worked out from: the log, the library and the listener's goal, at one moment. */
internal data class StatsInputs(
    val slots: Map<ListeningSlot, Heard>,
    val books: List<Book>,
    val anchors: List<ResumeAnchor>,
    val madeAudiobook: Boolean,
    val weeklyGoalHours: Int,
    val today: LocalDate,
    val zone: ZoneId,
    val firstDayOfWeek: DayOfWeek,
)

/**
 * @property finished books finished in the period: a Finished book (its place on its last page) whose place was saved
 *   in the period; all time counts every Finished book.
 * @property books the library's books by id, for "Most listened" titles and covers; a removed book is missing.
 * @property goalMs the period's share of the weekly goal (a month's is its days / 7 of it); null with no goal, and
 *   for all time.
 */
internal data class StatsData(
    val report: ListeningReport,
    val streaks: ListeningStreaks,
    val calendar: ListeningCalendar,
    val milestones: Map<Milestone, Boolean>,
    val finished: Int,
    val books: Map<String, Book>,
    val goalMs: Long?,
) {
    /** Nothing was ever listened: the screen says how stats start instead of showing empty charts. */
    val isEmpty: Boolean get() = streaks.best == 0 && calendar.daysThisYear == 0 && report.isEmpty
}

/** The four periods share the streaks, calendar and milestones, so they are worked out once for all of them. */
internal object StatsAllPeriods {
    fun of(
        inputs: StatsInputs,
        config: ListeningStatsConfig = ListeningStatsConfig(),
    ): Map<ListeningPeriod, StatsData> {
        val byDay = inputs.slots.msByDay()
        val listened = byDay.filterValues { it >= config.streakDayMs }.keys
        val streaks = ListeningStreaks.of(listened, inputs.today, inputs.firstDayOfWeek, config.restDays)
        val calendar = ListeningCalendar.of(byDay, inputs.today, inputs.firstDayOfWeek, config)
        val finishedOn = finishedDays(inputs)
        val milestones = ListeningMilestones.of(
            inputs.slots,
            streaks.best,
            finishedOn.size,
            inputs.madeAudiobook,
            config.milestones,
        )
        val books = inputs.books.associateBy { it.id.value }
        return ListeningPeriod.entries.associateWith { period ->
            val report = ListeningReport.of(inputs.slots, period, inputs.today, inputs.firstDayOfWeek, config)
            StatsData(
                report = report,
                streaks = streaks,
                calendar = calendar,
                milestones = milestones,
                finished = finishedOn.count { period == ListeningPeriod.ALL || it in report.from..report.to },
                books = books,
                goalMs = goalMs(inputs.weeklyGoalHours, period, report),
            )
        }
    }

    /** The day each Finished book's place was saved. */
    private fun finishedDays(inputs: StatsInputs): List<LocalDate> {
        val anchors = inputs.anchors.associateBy { it.bookId }
        return inputs.books.mapNotNull { book ->
            val anchor = anchors[book.id]
            anchor?.takeIf { BookProgress.of(book, it)?.isFinished == true }
                ?.let { Instant.ofEpochMilli(it.savedAtMillis).atZone(inputs.zone).toLocalDate() }
        }
    }

    private fun goalMs(weeklyHours: Int, period: ListeningPeriod, report: ListeningReport): Long? {
        if (weeklyHours <= 0) return null
        val weekly = weeklyHours * HOUR_MS
        return when (period) {
            ListeningPeriod.WEEK -> weekly
            ListeningPeriod.MONTH -> weekly * report.from.lengthOfMonth() / DAYS_A_WEEK
            ListeningPeriod.YEAR -> weekly * report.from.lengthOfYear() / DAYS_A_WEEK
            ListeningPeriod.ALL -> null
        }
    }

    private const val HOUR_MS = 60 * 60_000L
    private const val DAYS_A_WEEK = 7
}

/** One bar of the screen's chart: its height as a share of the tallest, its label (or none), what TalkBack says. */
internal data class BarSpec(val share: Float, val later: Boolean, val label: String?, val description: String)
