// File: app/src/main/kotlin/io/github/skm0078/audora/ui/stats/StatsShareScreen.kt
// Purpose: "Share your listening": the stats card's preview, what it shows (month, week, year, streak), its colour
//          and shape, and Save picture / Share (stats share mockup).

package io.github.skm0078.audora.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.stats.ListeningPeriod
import io.github.skm0078.audora.ui.library.CoverSource
import io.github.skm0078.audora.ui.share.CardRecorder
import io.github.skm0078.audora.ui.share.CardShape
import io.github.skm0078.audora.ui.share.ShareCardFrame
import io.github.skm0078.audora.ui.theme.Dimens

/** Story and square only: a post's 4:5 adds little over the two the stats card is drawn for. */
private val SHAPES = listOf(CardShape.STORY, CardShape.SQUARE)

@Composable
internal fun StatsShareScreen(
    all: Map<ListeningPeriod, StatsData>,
    choices: StatsCardChoices,
    busy: Boolean,
    recorder: CardRecorder,
    covers: CoverSource,
    actions: StatsShareActions,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = TOP_BAR_HEIGHT).padding(horizontal = Dimens.SpaceS),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
        ) {
            IconButton(onClick = actions::onBack) {
                Icon(
                    painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.stats_close),
                )
            }
            Text(
                stringResource(R.string.stats_share_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
        }
        val data = all.getValue(choices.kind.period)
        ShareCardFrame(
            shape = choices.shape,
            recorder = recorder,
            markColor = choices.look.quiet,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = Dimens.ScreenPadding),
        ) {
            StatsShareCard(choices.kind, data, choices.look, covers)
        }
        Column(
            Modifier.widthIn(max = MAX_WIDTH).fillMaxWidth().align(Alignment.CenterHorizontally)
                .padding(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
        ) {
            KindChips(choices.kind) { actions.onChoices(choices.copy(kind = it)) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
            ) {
                LookSwatches(choices.look, Modifier.weight(1f)) { actions.onChoices(choices.copy(look = it)) }
                ShapeToggle(choices.shape) { actions.onChoices(choices.copy(shape = it)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
                OutlinedButton(onClick = actions::onSave, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.stats_save))
                }
                Button(onClick = actions::onShare, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Icon(
                        painterResource(R.drawable.ic_share),
                        contentDescription = null,
                        Modifier.padding(end = Dimens.SpaceS),
                    )
                    Text(stringResource(R.string.stats_share))
                }
            }
        }
    }
}

/** Month, Week, Year, Streak: one row that scrolls sideways when large text makes it wider than the screen. */
@Composable
private fun KindChips(selected: StatsCardKind, onKind: (StatsCardKind) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
    ) {
        StatsCardKind.entries.forEach { kind ->
            val label = if (kind == StatsCardKind.STREAK) R.string.stats_card_streak else periodLabel(kind.period)
            FilterChip(selected = kind == selected, onClick = { onKind(kind) }, label = { Text(stringResource(label)) })
        }
    }
}

/** One round swatch per colour, the chosen one ringed, each a radio button named for TalkBack. */
@Composable
private fun LookSwatches(selected: StatsCardLook, modifier: Modifier, onLook: (StatsCardLook) -> Unit) {
    Row(
        modifier.horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
    ) {
        StatsCardLook.entries.forEach { look ->
            val name = stringResource(look.label)
            val chosen = look == selected
            Box(
                Modifier
                    .size(Dimens.TouchTarget)
                    .selectable(chosen, role = Role.RadioButton) { onLook(look) }
                    .semantics { contentDescription = name },
                contentAlignment = Alignment.Center,
            ) {
                val ring = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                Box(
                    Modifier
                        .size(SWATCH)
                        .border(if (chosen) RING_CHOSEN else RING, ring, CircleShape)
                        .padding(RING_GAP)
                        .clip(CircleShape)
                        .background(look.swatch),
                )
            }
        }
    }
}

@Composable
private fun ShapeToggle(selected: CardShape, onShape: (CardShape) -> Unit) {
    SingleChoiceSegmentedButtonRow {
        SHAPES.forEachIndexed { index, shape ->
            SegmentedButton(
                selected = shape == selected,
                onClick = { onShape(shape) },
                shape = SegmentedButtonDefaults.itemShape(index, SHAPES.size),
                icon = {},
            ) { Text(stringResource(shape.label)) }
        }
    }
}

private val TOP_BAR_HEIGHT = 64.dp
private val MAX_WIDTH = 600.dp
private val SWATCH = 40.dp
private val RING = 1.dp
private val RING_CHOSEN = 3.dp
private val RING_GAP = 4.dp
