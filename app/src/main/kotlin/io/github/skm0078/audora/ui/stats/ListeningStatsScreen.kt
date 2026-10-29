// File: app/src/main/kotlin/io/github/skm0078/audora/ui/stats/ListeningStatsScreen.kt
// Purpose: "Your listening": the full stats screen with its Week / Month / Year / All time tabs, the cards, the
//          weekly goal, Clear, and Share to the stats card (stats screen mockups, canvas row "Your listening").

package io.github.skm0078.audora.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.stats.ListeningPeriod
import io.github.skm0078.audora.core.stats.ListeningStatsConfig
import io.github.skm0078.audora.ui.library.CoverSource
import io.github.skm0078.audora.ui.theme.ChoiceDialog
import io.github.skm0078.audora.ui.theme.Dimens

/**
 * [all] is every period's stats, null while they are worked out. Cards with nothing to say (no language known yet, no
 * book, no hour) are left out rather than drawn empty; before any listening the screen says how stats start.
 */
@Composable
internal fun ListeningStatsScreen(
    all: Map<ListeningPeriod, StatsData>?,
    period: ListeningPeriod,
    weeklyGoalHours: Int,
    covers: CoverSource,
    actions: StatsScreenActions,
    config: ListeningStatsConfig = ListeningStatsConfig(),
) {
    var choosingGoal by rememberSaveable { mutableStateOf(false) }
    var clearing by rememberSaveable { mutableStateOf(false) }
    val data = all?.get(period)
    Column(Modifier.fillMaxSize()) {
        StatsTopBar(
            title = stringResource(R.string.stats_title),
            canShare = data?.isEmpty == false,
            onClose = actions::onClose,
            onShare = actions::onShare,
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = MAX_WIDTH).fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = Dimens.ScreenPadding,
                    end = Dimens.ScreenPadding,
                    bottom = Dimens.SpaceXxl,
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
            ) {
                item { PeriodTabs(period, actions::onPeriod) }
                when {
                    data == null -> item { Loading() }
                    data.isEmpty -> item { EmptyCard() }
                    else -> statsCards(data, weeklyGoalHours, covers, config) { choosingGoal = true }
                }
                item { Footer(canClear = data?.isEmpty == false) { clearing = true } }
            }
        }
    }
    if (choosingGoal) {
        ChoiceDialog(
            title = stringResource(R.string.stats_goal_title),
            options = ListeningGoal.OPTIONS,
            selected = weeklyGoalHours,
            label = { hours ->
                if (hours == 0) {
                    stringResource(R.string.stats_goal_off)
                } else {
                    pluralStringResource(R.plurals.stats_goal_hours, hours, hours)
                }
            },
            onDismiss = { choosingGoal = false },
        ) { hours ->
            choosingGoal = false
            actions.onGoal(hours)
        }
    }
    if (clearing) {
        ClearDialog(
            onConfirm = {
                clearing = false
                actions.onClear()
            },
            onDismiss = { clearing = false },
        )
    }
}

private fun LazyListScope.statsCards(
    data: StatsData,
    weeklyGoalHours: Int,
    covers: CoverSource,
    config: ListeningStatsConfig,
    onGoal: () -> Unit,
) {
    item { TotalCard(data, weeklyGoalHours, onGoal) }
    item { BarsCard(data.report) }
    item { NumbersCard(data, config) }
    item { LanguagesCard(data.report) }
    item { CalendarCard(data.calendar) }
    item { TopBooksCard(data.report.books, data.books, covers) }
    item { WhenCard(data.report.hours, data.report.peakStart, config.peakHours) }
    item { MilestonesCard(data.milestones, config.milestones) }
}

@Composable
private fun StatsTopBar(title: String, canShare: Boolean, onClose: () -> Unit, onShare: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = TOP_BAR_HEIGHT).padding(horizontal = Dimens.SpaceS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
    ) {
        IconButton(onClick = onClose) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.stats_close))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        Button(onClick = onShare, enabled = canShare, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
            Icon(painterResource(R.drawable.ic_share), contentDescription = null, Modifier.padding(end = Dimens.SpaceS))
            Text(stringResource(R.string.stats_share))
        }
    }
}

/** Week, Month, Year, All time: one row of four that the whole screen follows. */
@Composable
private fun PeriodTabs(selected: ListeningPeriod, onPeriod: (ListeningPeriod) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        ListeningPeriod.entries.forEachIndexed { index, period ->
            SegmentedButton(
                selected = period == selected,
                onClick = { onPeriod(period) },
                shape = SegmentedButtonDefaults.itemShape(index, ListeningPeriod.entries.size),
                icon = {},
            ) { Text(stringResource(periodLabel(period)), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxWidth().padding(Dimens.SpaceXxl), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyCard() {
    StatsCard { Text(stringResource(R.string.stats_empty), style = MaterialTheme.typography.bodyLarge) }
}

@Composable
private fun Footer(canClear: Boolean, onClear: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
        Text(
            stringResource(R.string.stats_private),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (canClear) TextButton(onClick = onClear) { Text(stringResource(R.string.stats_clear)) }
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

/** Wide enough for every card's numbers side by side; a tablet shows one centred column, not stretched cards. */
private val MAX_WIDTH = 600.dp
private val TOP_BAR_HEIGHT = 64.dp
