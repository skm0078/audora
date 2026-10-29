// File: app/src/main/kotlin/io/github/skm0078/audora/ui/stats/StatsDates.kt
// Purpose: How the stats say days, months, years and hours: chart labels, what TalkBack reads for a bar, the peak
//          hours, in the phone's locale.

package io.github.skm0078.audora.ui.stats

import android.text.format.DateFormat
import androidx.annotation.StringRes
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.stats.ListeningPeriod
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** The month's name as a word ("October"), for "in October" and "My October". */
internal fun monthName(day: LocalDate, locale: Locale): String =
    day.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)

/** A bar's label: the day's initial (week), its number every 7 days (month), the month's initial, or the year. */
internal fun barLabel(period: ListeningPeriod, start: LocalDate, locale: Locale): String? = when (period) {
    ListeningPeriod.WEEK -> start.dayOfWeek.getDisplayName(TextStyle.NARROW_STANDALONE, locale)

    ListeningPeriod.MONTH -> NumberFormat.getIntegerInstance(locale).format(start.dayOfMonth)
        .takeIf { (start.dayOfMonth - 1) % MONTH_LABEL_EVERY == 0 }

    ListeningPeriod.YEAR -> start.month.getDisplayName(TextStyle.NARROW_STANDALONE, locale)

    ListeningPeriod.ALL -> yearName(start.year, locale)
}

/** What TalkBack says for a bar or a calendar day: its whole date. */
internal fun barDate(period: ListeningPeriod, start: LocalDate, locale: Locale): String = when (period) {
    ListeningPeriod.WEEK, ListeningPeriod.MONTH -> start.format(DateTimeFormatter.ofPattern(dayPattern(locale), locale))
    ListeningPeriod.YEAR -> start.format(DateTimeFormatter.ofPattern(monthPattern(locale), locale))
    ListeningPeriod.ALL -> yearName(start.year, locale)
}

private fun dayPattern(locale: Locale): String = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")

private fun monthPattern(locale: Locale): String = DateFormat.getBestDateTimePattern(locale, "MMMMyyyy")

/** "7 pm" or "19", as the phone's locale says an hour. */
internal fun hourName(hour: Int, locale: Locale): String {
    val pattern = DateFormat.getBestDateTimePattern(locale, "j")
    return LocalTime.of(hour % HOURS_A_DAY, 0).format(DateTimeFormatter.ofPattern(pattern, locale))
}

/** A year as digits without grouping: "2026", never "2,026". */
internal fun yearName(year: Int, locale: Locale): String =
    NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = false }.format(year)

/** The tab's name for a period. */
@StringRes
internal fun periodLabel(period: ListeningPeriod): Int = when (period) {
    ListeningPeriod.WEEK -> R.string.stats_period_week
    ListeningPeriod.MONTH -> R.string.stats_period_month
    ListeningPeriod.YEAR -> R.string.stats_period_year
    ListeningPeriod.ALL -> R.string.stats_period_all
}

private const val HOURS_A_DAY = 24
private const val MONTH_LABEL_EVERY = 7
