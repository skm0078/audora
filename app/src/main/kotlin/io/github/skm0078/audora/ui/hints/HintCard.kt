// File: app/src/main/kotlin/io/github/skm0078/audora/ui/hints/HintCard.kt
// Purpose: The card a first-use hint shows in: its line, Got it, and the button that does or shows the feature
//          (ADR 2026-10-30-first-use-hints).

package io.github.skm0078.audora.ui.hints

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.hints.FirstUseHint
import io.github.skm0078.audora.ui.theme.Dimens

/**
 * [hint]'s card. Both buttons answer it for good; [onAct] then does what its button says. Said politely by TalkBack
 * when it appears, as the reader's other cards are.
 */
@Composable
fun HintCard(hint: FirstUseHint, hints: HintsState, onAct: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(hint) { hints.onShown(hint) }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(start = Dimens.SpaceL, end = Dimens.SpaceS, top = Dimens.SpaceM)) {
            Text(stringResource(hintText(hint)), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { hints.answer(hint) }) {
                    Text(stringResource(R.string.hint_got_it))
                }
                TextButton(
                    onClick = {
                        hints.answer(hint)
                        onAct()
                    },
                ) { Text(stringResource(hintAction(hint))) }
            }
        }
    }
}

internal fun hintText(hint: FirstUseHint): Int = when (hint) {
    FirstUseHint.KEEP_OFFLINE -> R.string.hint_keep_offline
    FirstUseHint.SMART_SPEED -> R.string.hint_smart_speed
    FirstUseHint.CAST -> R.string.hint_cast
    FirstUseHint.QUIZ -> R.string.hint_quiz
    FirstUseHint.FINISHED -> R.string.hint_finished
    FirstUseHint.PREPARE_AHEAD -> R.string.hint_prepare_ahead
    FirstUseHint.MORE_BOOKS -> R.string.hint_more_books
}

/** The second button says what it does: most show the place, two do the job at once. */
internal fun hintAction(hint: FirstUseHint): Int = when (hint) {
    FirstUseHint.KEEP_OFFLINE -> R.string.hint_action_keep
    FirstUseHint.PREPARE_AHEAD -> R.string.hint_action_prepare
    else -> R.string.hint_show_me
}
