// File: app/src/main/kotlin/io/github/skm0078/audora/ui/stats/ListeningStatsSheet.kt
// Purpose: Listening stats on one sheet (CF-8): today, this week, streaks, books finished, the last 7 days as bars.

package io.github.skm0078.audora.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.stats.ListeningSummary
import io.github.skm0078.audora.ui.theme.Dimens
import io.github.skm0078.audora.ui.theme.OptionSheet
import java.time.LocalDate
import java.time.format.TextStyle

@Composable
fun ListeningStatsSheet(summary: ListeningSummary, booksFinished: Int, onClear: () -> Unit, onClose: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    OptionSheet(stringResource(R.string.library_listening_stats), onClose) {
        if (summary.isEmpty) {
            Text(stringResource(R.string.stats_empty), style = MaterialTheme.typography.bodyMedium)
        }
        StatsRows(summary, booksFinished)
        WeekChart(summary.chart)
        Text(
            stringResource(R.string.stats_private),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!summary.isEmpty) {
            TextButton(onClick = { confirming = true }) { Text(stringResource(R.string.stats_clear)) }
        }
    }
    if (confirming) {
        ClearDialog(
            onConfirm = {
                confirming = false
                onClear()
            },
            onDismiss = { confirming = false },
        )
    }
}

/** One row per number, label first: rows wrap at any text size, where tiles side by side would squeeze. */
@Composable
private fun StatsRows(summary: ListeningSummary, booksFinished: Int) {
    Column {
        StatRow(stringResource(R.string.stats_today), duration(summary.todayMs))
        HorizontalDivider()
        StatRow(stringResource(R.string.stats_week), duration(summary.weekMs))
        HorizontalDivider()
        StatRow(stringResource(R.string.stats_streak), days(summary.currentStreak))
        HorizontalDivider()
        StatRow(stringResource(R.string.stats_best_streak), days(summary.longestStreak))
        HorizontalDivider()
        StatRow(stringResource(R.string.stats_books_finished), booksFinished.toString())
        HorizontalDivider()
        StatRow(stringResource(R.string.stats_total), duration(summary.totalMs))
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTarget)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.End)
    }
}

/** A bar per day, as tall as its share of the busiest day; TalkBack reads each day's name and time. */
@Composable
private fun WeekChart(chart: List<Pair<LocalDate, Long>>) {
    val most = chart.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1L
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        Text(
            stringResource(R.string.stats_chart_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
            chart.forEach { (day, ms) -> DayBar(day, ms, ms.toFloat() / most, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun DayBar(day: LocalDate, ms: Long, share: Float, modifier: Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val described = stringResource(
        R.string.stats_chart_day,
        day.dayOfWeek.getDisplayName(TextStyle.FULL, locale),
        duration(ms),
    )
    Column(
        modifier.clearAndSetSemantics { contentDescription = described },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
    ) {
        Box(Modifier.height(BAR_HEIGHT), contentAlignment = Alignment.BottomCenter) {
            Box(
                Modifier
                    .fillMaxWidth()
                    // A day listened always shows a sliver, so a short day is not mistaken for none.
                    .height(if (ms > 0) BAR_HEIGHT * share.coerceAtLeast(MIN_SHARE) else EMPTY_BAR)
                    .background(
                        if (ms > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(topStart = Dimens.SpaceXs, topEnd = Dimens.SpaceXs),
                    ),
            )
        }
        Text(day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ClearDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stats_clear_title)) },
        text = { Text(stringResource(R.string.stats_clear_body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.stats_clear_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.stats_cancel)) } },
    )
}

/** "25 min" or "3 h 10 min", rounded down to the minute. */
@Composable
private fun duration(ms: Long): String {
    val minutes = (ms / MINUTE_MS).toInt()
    val hours = minutes / MINUTES_PER_HOUR
    return if (hours == 0) {
        stringResource(R.string.stats_minutes, minutes)
    } else {
        stringResource(R.string.stats_hours_minutes, hours, minutes % MINUTES_PER_HOUR)
    }
}

@Composable
private fun days(count: Int): String = pluralStringResource(R.plurals.stats_days, count, count)

private val BAR_HEIGHT = 96.dp
private val EMPTY_BAR = 2.dp
private const val MIN_SHARE = 0.06f
private const val MINUTE_MS = 60_000L
private const val MINUTES_PER_HOUR = 60
