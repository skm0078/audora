// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/WhatDidItSay.kt
// Purpose: "What did it say?" (plan item 5): one tap replays the last sentence slower, shows it, then carries on.

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.ui.theme.Dimens

/**
 * A replay in progress: [unit] plays at [replaySpeed], then the listener's [restoreSpeed] comes back.
 *
 * @property from where reading was when the button was tapped; a move anywhere else before the replay starts (a tap
 *   on another sentence, a page turn) cancels it.
 * @property started the player has reached [unit].
 */
internal data class Replay(
    val unit: ReadingUnit,
    val from: ReadingPosition?,
    val restoreSpeed: Float,
    val replaySpeed: Float,
    val started: Boolean = false,
) {
    /** The replay after the player moved to [position]; null once it is over (or cancelled) and the speed returns. */
    fun advance(position: ReadingPosition?): Replay? {
        val inside = position != null && position.page == unit.page &&
            position.charStart in unit.charStart until unit.charEnd
        return when {
            inside -> copy(started = true)
            started -> null
            position == null || position == from -> this
            else -> null
        }
    }

    companion object {
        /**
         * The replay for a tap while [spokenIndex] of [units] is read: the sentence before it, which the listener
         * just heard (the one being read follows anyway), or the spoken one at the top of a page. A unit with no
         * text (a continued table's summary) is passed over: it would replay nothing. Null when nothing is being read
         * on the page.
         */
        fun start(
            units: List<ReadingUnit>,
            spokenIndex: Int?,
            from: ReadingPosition?,
            speed: Float,
            config: ReaderConfig,
        ): Replay? {
            val unit = spokenIndex?.let { spoken ->
                units.take(spoken).lastOrNull { it.text.isNotBlank() } ?: units.getOrNull(spoken)
            } ?: return null
            return Replay(unit, from, restoreSpeed = speed, replaySpeed = config.nearestSpeed(speed * REPLAY_FACTOR))
        }
    }
}

/** What the player shows and does for "What did it say?". */
@Stable
internal class WhatDidItSayState(val replaying: ReadingUnit?, val onTap: (() -> Unit)?)

/**
 * Drives one replay at a time: slows the voice, reads the sentence, and restores the speed when the player moves on,
 * when the replay is cancelled, or when the player closes. The speed is left alone if the listener changed it
 * meanwhile.
 */
@Composable
internal fun rememberWhatDidItSay(state: ReaderState, config: ReaderConfig, actions: ReaderActions): WhatDidItSayState {
    var replay by remember { mutableStateOf<Replay?>(null) }
    val currentSpeed by rememberUpdatedState(state.playback.speed)
    val currentActions by rememberUpdatedState(actions)
    fun finish(over: Replay) {
        if (currentSpeed == over.replaySpeed) currentActions.onSpeed(over.restoreSpeed)
    }
    LaunchedEffect(state.readingPosition) {
        val before = replay ?: return@LaunchedEffect
        val after = before.advance(state.readingPosition)
        replay = after
        if (after == null) finish(before)
    }
    DisposableEffect(Unit) { onDispose { replay?.let(::finish) } }
    val units = (state.content as? PageContent.Ready)?.units.orEmpty()
    val canStart = replay == null && state.playerReady && state.spokenUnitIndex != null
    val tap: () -> Unit = {
        Replay.start(units, state.spokenUnitIndex, state.readingPosition, state.playback.speed, config)?.let {
            replay = it
            actions.onSpeed(it.replaySpeed)
            actions.onSentenceTapped(it.unit)
        }
    }
    return WhatDidItSayState(replaying = replay?.unit, onTap = tap.takeIf { canStart })
}

/**
 * The button, and while a replay plays, the sentence it is reading again. No live region: the replay is spoken
 * aloud already, and TalkBack announcing the card would talk over it.
 */
@Composable
internal fun WhatDidItSay(control: WhatDidItSayState) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        control.replaying?.let { unit ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(Dimens.SpaceL), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
                    Text(stringResource(R.string.player_replaying), style = MaterialTheme.typography.labelLarge)
                    Text(unit.text, style = ReadingTextStyles.body())
                }
            }
        }
        FilledTonalButton(
            onClick = { control.onTap?.invoke() },
            enabled = control.onTap != null,
            modifier = Modifier.heightIn(min = Dimens.TouchTarget),
        ) {
            Icon(painterResource(R.drawable.ic_replay), contentDescription = null)
            Text(stringResource(R.string.player_what_did_it_say), Modifier.padding(start = Dimens.SpaceS))
        }
    }
}

/** How much slower the replay reads than the listener's speed (plan item 5). */
internal const val REPLAY_FACTOR = 0.85f
