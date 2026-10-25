// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/GestureTips.kt
// Purpose: A one-time card above the reader's controls naming the tap and long-press gestures nothing on screen shows.

package io.github.skm0078.audora.ui.reader

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.theme.Dimens

/** Tap a sentence to read from it, hold it for its options, hold the skip buttons to move by paragraph. */
@Composable
internal fun GestureTips(onGotIt: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(start = Dimens.SpaceL, end = Dimens.SpaceS, top = Dimens.SpaceM)) {
            Text(stringResource(R.string.gesture_tips_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.gesture_tips_tap), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.gesture_tips_sentence), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.gesture_tips_skip), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onGotIt) { Text(stringResource(R.string.gesture_tips_got_it)) }
            }
        }
    }
}
