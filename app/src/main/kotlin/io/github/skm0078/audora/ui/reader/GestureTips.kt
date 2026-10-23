// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/GestureTips.kt
// Purpose: A one-time card above the reader's controls naming the long-press gestures, which nothing on screen shows.

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.skm0078.audora.R
import io.github.skm0078.audora.battery.OnboardingPreferences
import io.github.skm0078.audora.ui.theme.Dimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shown until "Got it" is tapped, once for the whole app: long-press a sentence for its options, long-press the skip
 * buttons to move by paragraph. Read off the main thread; hidden until read, so it never flashes for someone who has
 * already dismissed it.
 */
@Composable
fun GestureTips(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val prefs = remember { OnboardingPreferences(context) }
    val scope = rememberCoroutineScope()
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(prefs) { show = withContext(Dispatchers.IO) { !prefs.gestureTipsSeen } }
    if (!show) return
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(start = Dimens.SpaceL, end = Dimens.SpaceS, top = Dimens.SpaceM)) {
            Text(stringResource(R.string.gesture_tips_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.gesture_tips_sentence), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.gesture_tips_skip), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = {
                        show = false
                        scope.launch(Dispatchers.IO) { prefs.gestureTipsSeen = true }
                    },
                ) { Text(stringResource(R.string.gesture_tips_got_it)) }
            }
        }
    }
}
