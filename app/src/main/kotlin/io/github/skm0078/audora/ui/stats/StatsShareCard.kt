// File: app/src/main/kotlin/io/github/skm0078/audora/ui/stats/StatsShareCard.kt
// Purpose: The stats share card's picture: a week, month or year in numbers, covers and bars, or the streak with the
//          calendar, in the chosen colours. Drawn inside ShareCardFrame, which adds the ListenLock mark.

package io.github.skm0078.audora.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.stats.ListeningBar
import io.github.skm0078.audora.core.stats.ListeningPeriod
import io.github.skm0078.audora.core.stats.ListeningStatsConfig
import io.github.skm0078.audora.ui.library.BookCover
import io.github.skm0078.audora.ui.library.CoverSource
import java.text.NumberFormat

/**
 * The card's own words and pictures, on the 360-dp-wide canvas the frame gives it. A square card has no room for the
 * covers, so it leaves them out; everything else fits both shapes.
 */
@Composable
internal fun StatsShareCard(
    kind: StatsCardKind,
    data: StatsData,
    look: StatsCardLook,
    covers: CoverSource,
    config: ListeningStatsConfig = ListeningStatsConfig(),
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(look.brush)) {
        val roomy = maxHeight > ROOMY_HEIGHT
        Column(
            Modifier.fillMaxSize().padding(start = PAD, end = PAD, top = PAD, bottom = MARK_ROOM),
            verticalArrangement = Arrangement.spacedBy(GAP),
        ) {
            if (kind == StatsCardKind.STREAK) {
                StreakBody(data, look)
            } else {
                PeriodBody(data, look, config)
                if (roomy) CoverRow(data.report.books.map { it.first }.mapNotNull(data.books::get), covers)
                Spacer(Modifier.weight(1f))
                CardBars(data, look)
            }
        }
    }
}

@Composable
private fun PeriodBody(data: StatsData, look: StatsCardLook, config: ListeningStatsConfig) {
    val report = data.report
    val format = NumberFormat.getIntegerInstance(locale())
    val kicker = when (report.period) {
        ListeningPeriod.WEEK -> stringResource(R.string.stats_card_my_week)
        else -> stringResource(R.string.stats_card_my, periodName(report).orEmpty())
    }
    Kicker(kicker, look)
    val languages = report.languages.size
    val headline = if (report.booksHeard > 0 && languages > 0) {
        stringResource(
            R.string.stats_card_headline,
            pluralStringResource(R.plurals.stats_card_books, report.booksHeard, report.booksHeard),
            pluralStringResource(R.plurals.stats_card_languages, languages, languages),
        )
    } else {
        stringResource(R.string.stats_card_headline_time, duration(report.ms))
    }
    Headline(headline, look)
    NumberPair(
        duration(report.ms) to stringResource(R.string.stats_card_listened),
        format.format(data.streaks.current) to stringResource(R.string.stats_card_day_streak),
        look,
    )
    NumberPair(
        words(report.words) to wordsCaption(report.words, config.novelWords),
        format.format(languages) to stringResource(R.string.stats_card_languages_label),
        look,
    )
}

@Composable
private fun StreakBody(data: StatsData, look: StatsCardLook) {
    val format = NumberFormat.getIntegerInstance(locale())
    Kicker(stringResource(R.string.stats_card_my_streak), look)
    Headline(
        pluralStringResource(R.plurals.stats_card_streak_headline, data.streaks.current, data.streaks.current),
        look,
    )
    NumberPair(
        format.format(data.streaks.best) to stringResource(R.string.stats_card_best),
        format.format(data.calendar.daysThisYear) to stringResource(R.string.stats_card_days_year),
        look,
    )
    Spacer(Modifier.height(GAP))
    CalendarGrid(data.calendar, fill = look.bar, empty = look.empty)
}

@Composable
private fun Kicker(text: String, look: StatsCardLook) = Text(
    text.uppercase(locale()),
    color = look.quiet,
    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = KICKER_SPACING),
)

@Composable
private fun Headline(text: String, look: StatsCardLook) = Text(
    text,
    color = look.ink,
    style = MaterialTheme.typography.headlineSmall,
    fontWeight = FontWeight.SemiBold,
    maxLines = 3,
    overflow = TextOverflow.Ellipsis,
)

@Composable
private fun NumberPair(left: Pair<String, String>, right: Pair<String, String>, look: StatsCardLook) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GAP)) {
        listOf(left, right).forEach { (value, caption) ->
            Column(Modifier.weight(1f)) {
                Text(value, color = look.ink, fontSize = NUMBER_SIZE, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(caption, color = look.quiet, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
        }
    }
}

@Composable
private fun CoverRow(books: List<Book>, covers: CoverSource) {
    if (books.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(COVER_GAP)) {
        books.forEach { BookCover(it, covers, Modifier.size(COVER_WIDTH, COVER_HEIGHT)) }
    }
}

/** The period's bars in the card's colours with their caption; no labels: the caption names the span. */
@Composable
private fun CardBars(data: StatsData, look: StatsCardLook) {
    val report = data.report
    val name = periodName(report).orEmpty()
    val caption = when (report.period) {
        ListeningPeriod.WEEK -> stringResource(R.string.stats_card_bars_week)
        ListeningPeriod.MONTH -> stringResource(R.string.stats_card_bars_month, name)
        ListeningPeriod.YEAR -> stringResource(R.string.stats_card_bars_year, name)
        ListeningPeriod.ALL -> stringResource(R.string.stats_card_bars_all)
    }
    Text(caption, color = look.quiet, style = MaterialTheme.typography.bodySmall)
    DrawnBars(report.bars, look.bar, look.empty, Modifier.fillMaxWidth().height(BARS_HEIGHT))
}

/** Bars drawn in one canvas: a 31-day month stays crisp at any picture size. A bar with nothing is a short stub. */
@Composable
private fun DrawnBars(bars: List<ListeningBar>, fill: Color, empty: Color, modifier: Modifier) {
    val most = bars.maxOfOrNull { it.ms }?.takeIf { it > 0 } ?: 1L
    Canvas(modifier) {
        if (bars.isEmpty()) return@Canvas
        val slot = size.width / bars.size
        val width = slot * BAR_FILL
        val radius = CornerRadius(width / 2, width / 2)
        bars.forEachIndexed { index, bar ->
            val height = if (bar.ms > 0) maxOf(size.height * bar.ms / most, width) else width / 2
            drawRoundRect(
                color = if (bar.ms > 0) fill else empty,
                topLeft = Offset(index * slot + (slot - width) / 2, size.height - height),
                size = Size(width, height),
                cornerRadius = radius,
            )
        }
    }
}

private val PAD = 24.dp
private val MARK_ROOM = 48.dp
private val GAP = 16.dp
private val ROOMY_HEIGHT = 480.dp
private val KICKER_SPACING = 1.2.sp
private val NUMBER_SIZE = 32.sp
private val COVER_GAP = 8.dp
private val COVER_WIDTH = 64.dp
private val COVER_HEIGHT = 88.dp
private val BARS_HEIGHT = 72.dp
private const val BAR_FILL = 0.62f
