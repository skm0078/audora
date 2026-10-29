// File: app/src/main/kotlin/io/github/skm0078/audora/ui/stats/StatsCards.kt
// Purpose: The top of the stats screen: the total with the goal ring, the period's bars, the three big numbers and
//          the languages heard.

package io.github.skm0078.audora.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.stats.ListeningReport
import io.github.skm0078.audora.core.stats.ListeningStatsConfig
import io.github.skm0078.audora.ui.theme.Dimens
import io.github.skm0078.audora.ui.voices.languageName
import java.text.NumberFormat
import kotlin.math.roundToInt

/** A white card on the screen's tint, like the mockup's: every section of the stats screen sits in one. */
@Composable
internal fun StatsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CARD_CORNER),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(Dimens.SpaceL), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
            content()
        }
    }
}

/** A card's small heading, announced as a heading so TalkBack can jump between cards. */
@Composable
internal fun CardTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { heading() },
    )
}

/** "5 h 50 min this week" with the goal ring (when a goal is set), the goal to change it, and the comparison. */
@Composable
internal fun TotalCard(data: StatsData, weeklyGoalHours: Int, onGoal: () -> Unit) {
    val report = data.report
    StatsCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceL),
        ) {
            data.goalMs?.let { goal ->
                val progress = report.ms.toFloat() / goal
                val percent = (progress * PERCENT).roundToInt()
                GoalRing(
                    progress,
                    NumberFormat.getPercentInstance(locale()).format(progress.toDouble()),
                    stringResource(R.string.stats_goal_progress, percent),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
                Text(totalLine(report), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                comparisonLine(report)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val goal = if (weeklyGoalHours > 0) {
            stringResource(
                R.string.stats_goal,
                pluralStringResource(R.plurals.stats_goal_hours, weeklyGoalHours, weeklyGoalHours),
            )
        } else {
            stringResource(R.string.stats_goal_set)
        }
        TextButton(onClick = onGoal) { Text(goal) }
    }
}

/** The period's bars, the best one highlighted. */
@Composable
internal fun BarsCard(report: ListeningReport) {
    val locale = locale()
    val best = report.bars.indices.maxByOrNull { report.bars[it].ms }?.takeIf { report.bars[it].ms > 0 }
    val most = report.bars.maxOfOrNull { it.ms }?.takeIf { it > 0 } ?: 1L
    val specs = report.bars.map { bar ->
        BarSpec(
            share = bar.ms.toFloat() / most,
            later = bar.later,
            label = barLabel(report.period, bar.start, locale),
            description = stringResource(
                R.string.stats_chart_day,
                barDate(report.period, bar.start, locale),
                duration(bar.ms),
            ),
        )
    }
    StatsCard { BarChart(specs, best) }
}

/** The streak, the books finished and the words heard, side by side; captions wrap at large text. */
@Composable
internal fun NumbersCard(data: StatsData, config: ListeningStatsConfig) {
    val format = NumberFormat.getIntegerInstance(locale())
    StatsCard {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
            BigNumber(
                format.format(data.streaks.current),
                stringResource(R.string.stats_streak_caption, data.streaks.best),
                Modifier.weight(1f),
            )
            BigNumber(format.format(data.finished), finishedCaption(data.report), Modifier.weight(1f))
            BigNumber(
                words(data.report.words),
                wordsCaption(data.report.words, config.novelWords),
                Modifier.weight(1f),
            )
        }
        if (data.streaks.restDaysUsed > 0) {
            Text(
                pluralStringResource(R.plurals.stats_rest_days, data.streaks.restDaysUsed, data.streaks.restDaysUsed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BigNumber(value: String, caption: String, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The languages heard as one strip and a legend with each one's share. Hidden when no language is known. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LanguagesCard(report: ListeningReport) {
    val total = report.languages.sumOf { it.second }.takeIf { it > 0 } ?: return
    val percent = NumberFormat.getPercentInstance(locale())
    StatsCard {
        CardTitle(stringResource(R.string.stats_languages))
        LanguageStrip(report.languages.map { it.second.toFloat() / total })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceL)) {
            report.languages.forEachIndexed { index, (lang, ms) ->
                Row(
                    Modifier.semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
                ) {
                    Box(
                        Modifier.size(
                            LEGEND_DOT,
                        ).clip(RoundedCornerShape(LEGEND_CORNER)).background(languageColor(index)),
                    )
                    Text(stringResource(languageName(lang)), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        percent.format(ms.toDouble() / total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private val CARD_CORNER = 20.dp
private val LEGEND_DOT = 10.dp
private val LEGEND_CORNER = 3.dp
private const val PERCENT = 100
