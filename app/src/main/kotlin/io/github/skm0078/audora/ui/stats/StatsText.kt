// File: app/src/main/kotlin/io/github/skm0078/audora/ui/stats/StatsText.kt
// Purpose: How the stats screen and card say times, word counts, periods, days and hours in the app's language.

package io.github.skm0078.audora.ui.stats

import android.icu.text.CompactDecimalFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.stats.ListeningPeriod
import io.github.skm0078.audora.core.stats.ListeningReport
import io.github.skm0078.audora.core.stats.Milestone
import io.github.skm0078.audora.core.stats.MilestoneGoals
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

@Composable
@ReadOnlyComposable
internal fun locale(): Locale = LocalConfiguration.current.locales[0]

/** "45 min", "5 h", "5 h 50 min": minutes under an hour, whole hours without "0 min". */
@Composable
@ReadOnlyComposable
internal fun duration(ms: Long): String {
    val minutes = (ms / MINUTE_MS).toInt()
    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR
    return when {
        hours == 0 -> stringResource(R.string.stats_minutes, minutes)
        rest == 0 -> stringResource(R.string.stats_hours, hours)
        else -> stringResource(R.string.stats_hours_minutes, hours, rest)
    }
}

/** "≈61k": words rounded to what a glance needs, in the language's own digits and compact form. */
@Composable
@ReadOnlyComposable
internal fun words(count: Long): String = stringResource(R.string.stats_words_value, compactNumber(count, locale()))

internal fun compactNumber(count: Long, locale: Locale): String =
    CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT).format(count)

/** The period as said after "in": the month's name, the year's number; null for a week and all time. */
@Composable
@ReadOnlyComposable
internal fun periodName(report: ListeningReport): String? = when (report.period) {
    ListeningPeriod.MONTH -> monthName(report.from, locale())
    ListeningPeriod.YEAR -> yearName(report.from.year, locale())
    ListeningPeriod.WEEK, ListeningPeriod.ALL -> null
}

/** "5 h 50 min this week", "18 h in October". */
@Composable
@ReadOnlyComposable
internal fun totalLine(report: ListeningReport): String {
    val time = duration(report.ms)
    val name = periodName(report)
    return when {
        report.period == ListeningPeriod.WEEK -> stringResource(R.string.stats_total_week, time)
        name != null -> stringResource(R.string.stats_total_in, time, name)
        else -> stringResource(R.string.stats_total_all, time)
    }
}

/** "1 h 10 min more than last week"; null for all time, which has nothing before it. */
@Composable
@ReadOnlyComposable
internal fun comparisonLine(report: ListeningReport): String? {
    val before = report.previousMs ?: return null
    val change = report.ms - before
    val (more, less) = when (report.period) {
        ListeningPeriod.WEEK -> R.string.stats_more_week to R.string.stats_less_week
        ListeningPeriod.MONTH -> R.string.stats_more_month to R.string.stats_less_month
        ListeningPeriod.YEAR, ListeningPeriod.ALL -> R.string.stats_more_year to R.string.stats_less_year
    }
    return when {
        abs(change) < MINUTE_MS -> stringResource(R.string.stats_same_as_before)
        change > 0 -> stringResource(more, duration(change))
        else -> stringResource(less, duration(-change))
    }
}

/** "books finished in October". */
@Composable
@ReadOnlyComposable
internal fun finishedCaption(report: ListeningReport): String {
    val name = periodName(report)
    return when {
        report.period == ListeningPeriod.WEEK -> stringResource(R.string.stats_books_week)
        name != null -> stringResource(R.string.stats_books_in, name)
        else -> stringResource(R.string.stats_books_all)
    }
}

/** "words heard, about 2 novels" once a novel's worth is heard, else "words heard". */
@Composable
@ReadOnlyComposable
internal fun wordsCaption(words: Long, novelWords: Long): String {
    val novels = (words / novelWords).toInt()
    return if (novels < 1) {
        stringResource(R.string.stats_words_caption)
    } else {
        pluralStringResource(R.plurals.stats_words_novels, novels, novels)
    }
}

/** A badge's name: "7-day streak", "10 hours", from the thresholds it asks for. */
@Composable
@ReadOnlyComposable
internal fun milestoneName(milestone: Milestone, goals: MilestoneGoals): String {
    val (res, count) = when (milestone) {
        Milestone.SHORT_STREAK -> R.plurals.stats_badge_streak to goals.shortStreakDays
        Milestone.HOURS -> R.plurals.stats_badge_hours to goals.hours
        Milestone.LANGUAGES -> R.plurals.stats_badge_languages to goals.languages
        Milestone.FIRST_AUDIOBOOK -> return stringResource(R.string.stats_badge_first_audiobook)
        Milestone.LONG_STREAK -> R.plurals.stats_badge_streak to goals.longStreakDays
        Milestone.BOOKS -> R.plurals.stats_badge_books to goals.booksFinished
    }
    return pluralStringResource(res, count, count)
}

internal const val MINUTE_MS = 60_000L
private const val MINUTES_PER_HOUR = 60
