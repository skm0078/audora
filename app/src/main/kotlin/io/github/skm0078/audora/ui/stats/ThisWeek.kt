// File: app/src/main/kotlin/io/github/skm0078/audora/ui/stats/ThisWeek.kt
// Purpose: The home's "This week" (personal touch 7): the week's listening in the goal ring, the streak under it,
//          and a tap that opens the listening stats.

package io.github.skm0078.audora.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.stats.WeekGlance
import io.github.skm0078.audora.di.ListeningStatsEntryPoint
import io.github.skm0078.audora.ui.theme.Dimens
import java.text.NumberFormat
import java.time.LocalDate
import java.time.temporal.WeekFields
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The week so far from the listening log and the goal, worked out off the main thread whenever either changes; null
 * until both are read, and while nothing was ever listened.
 */
@Composable
fun rememberWeekGlance(): WeekGlance? {
    val context = LocalContext.current.applicationContext
    val log = remember(context) { ListeningStatsEntryPoint.of(context).listeningLog() }
    val goal = remember(context) { ListeningGoal(context) }
    LaunchedEffect(log) { withContext(Dispatchers.IO) { log.load() } }
    val slots by log.slots.collectAsStateWithLifecycle()
    val hours by remember(goal) { goal.changes }.collectAsStateWithLifecycle(initialValue = null)
    val firstDay = WeekFields.of(locale()).firstDayOfWeek
    val glance by produceState<WeekGlance?>(null, slots, hours, firstDay) {
        val weekly = hours ?: return@produceState
        value = withContext(Dispatchers.Default) { WeekGlance.of(slots, weekly, LocalDate.now(), firstDay) }
    }
    return glance
}

/** "3 h 10 min of 5 h" beside the ring (or "3 h 10 min this week" with no goal), and the streak once there is one. */
@Composable
fun ThisWeekCard(week: WeekGlance, onOpen: () -> Unit) {
    val openLabel = stringResource(R.string.home_week_open)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClickLabel = openLabel) {
            onOpen()
        },
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Row(
            Modifier.padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceM),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceL),
        ) {
            week.progress?.let { progress ->
                GoalRing(
                    progress,
                    NumberFormat.getPercentInstance(locale()).format(progress.toDouble()),
                    stringResource(R.string.stats_goal_progress, (progress * PERCENT).roundToInt()),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
                Text(
                    stringResource(R.string.home_section_week),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(weekLine(week), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (week.streak > 0) {
                    Text(
                        pluralStringResource(R.plurals.home_week_streak, week.streak, week.streak),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun weekLine(week: WeekGlance): String {
    val heard = duration(week.ms)
    return week.goalMs?.let { stringResource(R.string.home_week_of_goal, heard, duration(it)) }
        ?: stringResource(R.string.stats_total_week, heard)
}

private const val PERCENT = 100
