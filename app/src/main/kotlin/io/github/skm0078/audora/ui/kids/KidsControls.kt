// File: app/src/main/kotlin/io/github/skm0078/audora/ui/kids/KidsControls.kt
// Purpose: Kids mode's screens pieces (plan item 71): the player's three big buttons, and the grown-ups' banner
//          that leaves kids mode only after a long, deliberate press.

package io.github.skm0078.audora.ui.kids

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.theme.Dimens

/** Back a sentence, play or pause, on a sentence: the whole player for a child, each button easy to hit. */
@Composable
fun KidsControls(playing: Boolean, onPlayPause: () -> Unit, onBack: () -> Unit, onForward: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = Dimens.SpaceXl),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(onClick = onBack, modifier = Modifier.size(SIDE_BUTTON)) {
            Icon(painterResource(R.drawable.ic_skip_previous), stringResource(R.string.reader_skip_back))
        }
        FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(PLAY_BUTTON)) {
            if (playing) {
                Icon(painterResource(R.drawable.ic_pause), stringResource(R.string.reader_pause), Modifier.size(ICON))
            } else {
                Icon(painterResource(R.drawable.ic_play), stringResource(R.string.reader_play), Modifier.size(ICON))
            }
        }
        FilledTonalIconButton(onClick = onForward, modifier = Modifier.size(SIDE_BUTTON)) {
            Icon(painterResource(R.drawable.ic_skip_next), stringResource(R.string.reader_skip_forward))
        }
    }
}

/**
 * "Kids mode is on. Grown-ups: hold to leave." A press must be held for [UNLOCK_HOLD_MS]; a ring fills meanwhile,
 * and letting go early resets it. TalkBack's long-press action leaves at once (a screen reader user is not a child
 * fumbling at the screen).
 */
@Composable
fun KidsBanner(onLeave: () -> Unit) {
    val leave by rememberUpdatedState(onLeave)
    var holding by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(holding) {
        if (holding) {
            progress.animateTo(1f, tween(UNLOCK_HOLD_MS, easing = LinearEasing))
            leave()
        } else {
            progress.snapTo(0f)
        }
    }
    val label = stringResource(R.string.kids_leave)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                onLongClick(label) {
                    leave()
                    true
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    holding = true
                    waitForUpOrCancellation()
                    holding = false
                }
            },
    ) {
        Row(
            modifier = Modifier.padding(Dimens.SpaceL),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceL),
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { progress.value }, modifier = Modifier.size(RING))
            }
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private val PLAY_BUTTON = 112.dp
private val SIDE_BUTTON = 80.dp
private val ICON = 48.dp
private val RING = 32.dp

/** Long enough that a child does not leave kids mode by tapping around. */
private const val UNLOCK_HOLD_MS = 3_000
