// File: app/src/main/kotlin/io/github/skm0078/audora/ui/stats/StatsMoreCards.kt
// Purpose: The lower stats cards: the calendar of days listened, the most listened books, when in the day, and the
//          milestones.

package io.github.skm0078.audora.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.stats.ListeningCalendar
import io.github.skm0078.audora.core.stats.Milestone
import io.github.skm0078.audora.core.stats.MilestoneGoals
import io.github.skm0078.audora.ui.library.BookCover
import io.github.skm0078.audora.ui.library.CoverSource
import io.github.skm0078.audora.ui.library.displayTitle
import io.github.skm0078.audora.ui.theme.Dimens

@Composable
internal fun CalendarCard(calendar: ListeningCalendar) {
    StatsCard {
        CardTitle(pluralStringResource(R.plurals.stats_calendar, calendar.weeks.size, calendar.weeks.size))
        CalendarGrid(calendar)
        Text(
            pluralStringResource(R.plurals.stats_days_this_year, calendar.daysThisYear, calendar.daysThisYear),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** The period's most listened books with their time; a book since removed is named as such, without a cover. */
@Composable
internal fun TopBooksCard(top: List<Pair<String, Long>>, books: Map<String, Book>, covers: CoverSource) {
    if (top.isEmpty()) return
    StatsCard {
        CardTitle(stringResource(R.string.stats_most_listened))
        top.forEach { (id, ms) ->
            val book = books[id]
            Row(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
            ) {
                if (book != null) {
                    BookCover(book, covers, Modifier.size(COVER_WIDTH, COVER_HEIGHT))
                } else {
                    Spacer(Modifier.width(COVER_WIDTH))
                }
                Text(
                    book?.let { displayTitle(it) } ?: stringResource(R.string.stats_removed_book),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(duration(ms), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Morning, day, evening and night, and the busiest few hours said in words. Hidden until an hour is known. */
@Composable
internal fun WhenCard(hours: List<Long>, peakStart: Int?, peakHours: Int) {
    val start = peakStart ?: return
    val locale = locale()
    val parts = DAY_PARTS.map { (name, range) -> stringResource(name) to range.sumOf { hours[it % hours.size] } }
    StatsCard {
        CardTitle(stringResource(R.string.stats_when))
        DayParts(parts)
        Text(
            stringResource(R.string.stats_peak, hourName(start, locale), hourName(start + peakHours, locale)),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** The badges, three to a row: reached ones filled, the rest dashed, each said with whether it is reached. */
@Composable
internal fun MilestonesCard(milestones: Map<Milestone, Boolean>, goals: MilestoneGoals) {
    StatsCard {
        CardTitle(stringResource(R.string.stats_milestones))
        Milestone.entries.chunked(BADGES_A_ROW).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                row.forEach { milestone ->
                    Badge(milestoneName(milestone, goals), milestones[milestone] == true, Modifier.weight(1f))
                }
                repeat(BADGES_A_ROW - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Badge(name: String, reached: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val said = stringResource(if (reached) R.string.stats_badge_reached else R.string.stats_badge_not_yet, name)
    Column(
        modifier.clearAndSetSemantics { contentDescription = said },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
    ) {
        val shape = RoundedCornerShape(BADGE_CORNER)
        val outline = colors.outline
        val tile = if (reached) {
            Modifier.clip(shape).background(colors.primaryContainer)
        } else {
            // Dashed: a place kept for a badge still to come.
            Modifier.drawBehind {
                drawRoundRect(
                    outline,
                    cornerRadius = CornerRadius(BADGE_CORNER.toPx()),
                    style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH, DASH))),
                )
            }
        }
        Box(Modifier.size(BADGE_SIZE).then(tile), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(if (reached) R.drawable.ic_star else R.drawable.ic_star_border),
                contentDescription = null,
                tint = if (reached) colors.onPrimaryContainer else colors.onSurfaceVariant,
            )
        }
        Text(name, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

// Morning 5 to noon, day noon to 5 pm, evening 5 to 9 pm, night 9 pm to 5 am (hours past 23 wrap round).
@Suppress("MagicNumber") // The hours of the day are their own names.
private val DAY_PARTS = listOf(
    R.string.stats_part_morning to (5 until 12),
    R.string.stats_part_day to (12 until 17),
    R.string.stats_part_evening to (17 until 21),
    R.string.stats_part_night to (21 until 29),
)
private val COVER_WIDTH = 40.dp
private val COVER_HEIGHT = 56.dp
private const val BADGES_A_ROW = 3
private val BADGE_SIZE = 56.dp
private val BADGE_CORNER = 16.dp
private const val DASH = 8f
