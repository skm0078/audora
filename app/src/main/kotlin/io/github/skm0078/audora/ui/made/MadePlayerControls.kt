// File: app/src/main/kotlin/io/github/skm0078/audora/ui/made/MadePlayerControls.kt
// Purpose: The saved-audio player's seek bar and controls panel (ADR 2026-10-28-made-tab): the book player's raised
//          panel with chapter, 15 s and 30 s skips around a large Play, and Speed and the sleep timer under them.

package io.github.skm0078.audora.ui.made

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.ui.reader.SleepTimerMenuButton
import io.github.skm0078.audora.ui.theme.Dimens
import io.github.skm0078.audora.ui.theme.namedSlider
import java.util.Locale

/**
 * While dragging, the bar follows the finger; the seek happens once, when it is let go. Under it, where the chapter
 * is, which chapter of how many, and the time left in it, so the bar's ends read without a tap.
 */
@Composable
internal fun MadeSeekBar(state: MadePlayerState, controls: MadeAudioController?, modifier: Modifier = Modifier) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(1)
    val fraction = dragging ?: (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val at = dragging?.let { (it * duration).toLong() } ?: state.positionMs
    val position = clockText(at)
    val spoken = stringResource(R.string.made_player_position, position, clockText(state.durationMs))
    Column(modifier.fillMaxWidth()) {
        Slider(
            value = fraction,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { controls?.seekTo((it * duration).toLong()) }
                dragging = null
            },
            enabled = state.durationMs > 0,
            modifier = Modifier.namedSlider(stringResource(R.string.a11y_player_position), spoken),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SeekLabel(position, Modifier.weight(1f))
            if (state.count > 1) SeekLabel(stringResource(R.string.made_player_chapter, state.index + 1, state.count))
            val left = clockText((state.durationMs - at).coerceAtLeast(0))
            SeekLabel(stringResource(R.string.made_player_left, left), Modifier.weight(1f), end = true)
        }
    }
}

@Composable
private fun SeekLabel(text: String, modifier: Modifier = Modifier, end: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = if (end) TextAlign.End else TextAlign.Start,
        modifier = modifier,
    )
}

/** The book player's panel (ADR-036): raised, rounded at the top, everything under the thumb. */
@Composable
internal fun MadeControlsPanel(
    state: MadePlayerState,
    controls: MadeAudioController?,
    config: ReaderConfig,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = Dimens.SpaceXl, topEnd = Dimens.SpaceXl),
        modifier = modifier.fillMaxWidth().padding(top = Dimens.SpaceS),
    ) {
        Column(
            Modifier.navigationBarsPadding().padding(horizontal = Dimens.SpaceS, vertical = Dimens.SpaceM),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
        ) {
            TransportRow(state, controls)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MadeSpeedButton(state.speed, config, enabled = state.connected) { controls?.setSpeed(it) }
                Box(Modifier.weight(1f))
                SleepTimerMenuButton(
                    timer = state.sleepTimer,
                    choices = madeSleepChoices(state.count, config),
                    enabled = state.connected,
                    onChoose = { controls?.sleep(it) },
                )
            }
        }
    }
}

/** Previous chapter, back 15 s, Play, forward 30 s, next chapter: spread evenly, Play in the middle. */
@Composable
private fun TransportRow(state: MadePlayerState, controls: MadeAudioController?) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { controls?.previousChapter() }, enabled = state.index > 0) {
            Icon(painterResource(R.drawable.ic_skip_previous), stringResource(R.string.made_player_previous))
        }
        SecondsButton(BACK_SECONDS, R.string.made_player_back, forward = false) { controls?.skip(-BACK_MS) }
        FilledIconButton(onClick = { controls?.playPause() }, Modifier.size(Dimens.PlayButton - Dimens.SpaceS)) {
            val (icon, label) = if (state.playing) {
                R.drawable.ic_pause to R.string.made_player_pause
            } else {
                R.drawable.ic_play to R.string.made_player_play
            }
            Icon(painterResource(icon), stringResource(label))
        }
        SecondsButton(FORWARD_SECONDS, R.string.made_player_forward, forward = true) { controls?.skip(FORWARD_MS) }
        IconButton(onClick = { controls?.nextChapter() }, enabled = state.index < state.count - 1) {
            Icon(painterResource(R.drawable.ic_skip_next), stringResource(R.string.made_player_next))
        }
    }
}

/** The circling arrow with its seconds inside, mirrored for forward: a podcast player's skip, read by its name. */
@Composable
private fun SecondsButton(seconds: Int, @StringRes description: Int, forward: Boolean, onClick: () -> Unit) {
    val label = stringResource(description)
    val mirrored = if (forward) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painterResource(R.drawable.ic_replay),
                contentDescription = null,
                Modifier.size(SKIP_ICON).then(mirrored),
            )
            Text(
                seconds.toString(),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = Dimens.SpaceXs / 2).clearAndSetSemantics {},
            )
        }
    }
}

/** "1:05:09" or "4:07": the player's clock, not words. */
internal fun clockText(ms: Long): String {
    val seconds = (ms / MS_PER_SECOND).coerceAtLeast(0)
    val hours = seconds / SECONDS_PER_HOUR
    val minutes = seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val rest = seconds % SECONDS_PER_MINUTE
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, rest)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, rest)
    }
}

private const val BACK_SECONDS = 15
private const val FORWARD_SECONDS = 30
private const val BACK_MS = BACK_SECONDS * 1_000L
private const val FORWARD_MS = FORWARD_SECONDS * 1_000L
private const val MS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private val SKIP_ICON = Dimens.SpaceXxl
