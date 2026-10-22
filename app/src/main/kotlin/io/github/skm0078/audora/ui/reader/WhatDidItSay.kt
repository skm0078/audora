// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/WhatDidItSay.kt
// Purpose: "What did it say?" (plan item 5): one tap has the player replay the last sentence slower and shows it.

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.ui.theme.Dimens

/**
 * A replay in progress: the player service reads [unit] again a little slower, then reads on at the listener's speed
 * (ReaderModel.replayPrevious); this only follows it, to show the sentence while it plays.
 *
 * @property from where reading was when the button was tapped; a move anywhere else before the replay starts (a tap
 *   on another sentence, a page turn) cancels it.
 * @property started the player has reached [unit].
 */
internal data class Replay(val unit: ReadingUnit, val from: ReadingPosition?, val started: Boolean = false) {
    /** The replay after the player moved to [position]; null once it is over (or cancelled). */
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
         * The sentence the player replays for a tap while [spokenIndex] of [units] is read: the one before it, which
         * the listener just heard. A unit with no length (charEnd == charStart, a continued table's summary) is passed
         * over, as the player never plays one. Null when nothing is being read, or at the top of the page: the
         * player then replays the previous page's last sentence, and the card stays away rather than show the wrong
         * one.
         */
        fun start(units: List<ReadingUnit>, spokenIndex: Int?, from: ReadingPosition?): Replay? {
            val unit = spokenIndex?.let { spoken -> units.take(spoken).lastOrNull { it.charEnd > it.charStart } }
            return unit?.let { Replay(it, from) }
        }
    }
}

/** What the player shows and does for "What did it say?". */
@Stable
internal class WhatDidItSayState(val replaying: ReadingUnit?, val onTap: (() -> Unit)?)

/** Shows one replay at a time: from the tap until the player moves on from the replayed sentence (or elsewhere). */
@Composable
internal fun rememberWhatDidItSay(state: ReaderState, actions: ReaderActions): WhatDidItSayState {
    var replay by remember { mutableStateOf<Replay?>(null) }
    LaunchedEffect(state.readingPosition) {
        replay = replay?.advance(state.readingPosition)
    }
    val units = (state.content as? PageContent.Ready)?.units.orEmpty()
    val canStart = replay == null && state.playerReady && state.spokenUnitIndex != null
    val tap: () -> Unit = {
        replay = Replay.start(units, state.spokenUnitIndex, state.readingPosition)
        actions.onReplayPrevious()
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
