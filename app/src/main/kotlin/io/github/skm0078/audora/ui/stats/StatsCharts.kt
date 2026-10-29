// File: app/src/main/kotlin/io/github/skm0078/audora/ui/stats/StatsCharts.kt
// Purpose: The stats screen's pictures: the goal ring, the period's bars, the languages strip, the calendar of days
//          listened and the parts of the day. Drawn in theme colours; the share card draws its bars with its own.

package io.github.skm0078.audora.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import io.github.skm0078.audora.core.stats.ListeningCalendar
import io.github.skm0078.audora.ui.theme.Dimens

/** The goal ring: [progress] of the goal, 0 to 1 (more is drawn full), with the percentage in the middle. */
@Composable
internal fun GoalRing(progress: Float, percent: String, description: String, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = MaterialTheme.colorScheme.primary
    Box(
        modifier.size(RING_SIZE).clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(RING_SIZE)) {
            val stroke = RING_STROKE.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, FULL_TURN, false, topLeft, arcSize, style = Stroke(stroke))
            val sweep = FULL_TURN * progress.coerceIn(0f, 1f)
            if (sweep > 0f) {
                drawArc(
                    fill,
                    START_ANGLE,
                    sweep,
                    false,
                    topLeft,
                    arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Text(percent, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Bars side by side, filling the width: the [highlight]ed one (the best) in the primary colour, the rest quieter, and
 * days still to come as faint stubs. Each bar is its own TalkBack stop with its date and time.
 */
@Composable
internal fun BarChart(bars: List<BarSpec>, highlight: Int?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val gap = if (bars.size > WIDE_BARS) NARROW_GAP else Dimens.SpaceS
    Row(modifier.fillMaxWidth().height(CHART_HEIGHT), horizontalArrangement = Arrangement.spacedBy(gap)) {
        bars.forEachIndexed { index, bar ->
            Column(
                Modifier.weight(1f).fillMaxHeight().semantics(mergeDescendants = true) {
                    contentDescription = bar.description
                },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs, Alignment.Bottom),
            ) {
                val color = when {
                    bar.later -> colors.surfaceContainerHigh
                    index == highlight -> colors.primary
                    else -> colors.surfaceContainerHighest
                }
                val corner = if (bars.size > WIDE_BARS) NARROW_CORNER else BAR_CORNER
                Bar(bar.share, color, corner, Modifier.weight(1f).widthIn(max = BAR_MAX_WIDTH))
                Text(
                    bar.label.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == highlight) colors.onSurface else colors.onSurfaceVariant,
                    fontWeight = if (index == highlight) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** A bar [share] of this space's height tall, standing on its floor; a sliver when nearly nothing. */
@Composable
private fun Bar(share: Float, color: Color, corner: Dp, modifier: Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(share.coerceIn(MIN_SHARE, 1f))
                .clip(RoundedCornerShape(corner))
                .background(color),
        )
    }
}

/** The languages as one strip of shares, in the scheme's three accents, most heard first. */
@Composable
internal fun LanguageStrip(shares: List<Float>, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(STRIP_HEIGHT).clip(RoundedCornerShape(STRIP_HEIGHT / 2)),
        horizontalArrangement = Arrangement.spacedBy(STRIP_GAP),
    ) {
        shares.forEachIndexed { index, share ->
            Box(Modifier.weight(share.coerceAtLeast(MIN_SHARE)).fillMaxHeight().background(languageColor(index)))
        }
    }
}

/** The colour of the [index]th language, most heard first. */
@Composable
internal fun languageColor(index: Int): Color = with(MaterialTheme.colorScheme) {
    listOf(primary, secondary, tertiary)[index % LANGUAGE_COLORS]
}

/**
 * The calendar: one column per week, one cell per day, darker for longer listening; days to come are left out. Cells
 * fit the width up to [CELL_MAX]; the share card passes its own [fill] and [empty]. Decorative for TalkBack: the
 * line under it says how many days were listened.
 */
@Composable
internal fun CalendarGrid(
    calendar: ListeningCalendar,
    modifier: Modifier = Modifier,
    fill: Color = MaterialTheme.colorScheme.primary,
    empty: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val shades = calendar.shades.coerceAtLeast(1)
    BoxWithConstraints(modifier.fillMaxWidth().clearAndSetSemantics {}) {
        val weeks = calendar.weeks.size.coerceAtLeast(1)
        val cell = min((maxWidth - CELL_GAP * (weeks - 1)) / weeks, CELL_MAX)
        Row(horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
            calendar.weeks.forEach { week ->
                Column(verticalArrangement = Arrangement.spacedBy(CELL_GAP)) {
                    week.forEach { day ->
                        val color = when {
                            day == null -> Color.Transparent
                            day.shade == 0 -> empty
                            else -> fill.copy(alpha = SHADE_FLOOR + (1 - SHADE_FLOOR) * day.shade / shades)
                        }
                        Box(Modifier.size(cell).clip(RoundedCornerShape(cell / CELL_ROUNDING)).background(color))
                    }
                }
            }
        }
    }
}

/** Morning, day, evening and night as four blocks, the busiest in the primary colour, each labelled under it. */
@Composable
internal fun DayParts(parts: List<Pair<String, Long>>, modifier: Modifier = Modifier) {
    val most = parts.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1L
    val busiest = parts.indexOfFirst { it.second == most }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        parts.forEachIndexed { index, (name, ms) ->
            Column(
                Modifier.weight(1f).height(PARTS_HEIGHT),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs, Alignment.Bottom),
            ) {
                val color = if (index == busiest) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                }
                Bar(ms.toFloat() / most, color, BAR_CORNER, Modifier.weight(1f))
                Text(
                    name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

private val RING_SIZE = 76.dp
private val RING_STROKE = 8.dp
private const val FULL_TURN = 360f
private const val START_ANGLE = -90f
private val CHART_HEIGHT = 112.dp
private val PARTS_HEIGHT = 88.dp
private val BAR_MAX_WIDTH = 28.dp
private val BAR_CORNER = 8.dp
private val NARROW_CORNER = 3.dp
private val NARROW_GAP = 2.dp
private const val WIDE_BARS = 12
private const val MIN_SHARE = 0.04f
private val STRIP_HEIGHT = 12.dp
private val STRIP_GAP = 2.dp
private const val LANGUAGE_COLORS = 3
private val CELL_GAP = 3.dp
private val CELL_MAX: Dp = 14.dp
private const val CELL_ROUNDING = 4
private const val SHADE_FLOOR = 0.3f
