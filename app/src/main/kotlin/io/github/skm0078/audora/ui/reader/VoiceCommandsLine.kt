// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/VoiceCommandsLine.kt
// Purpose: The reader's line saying whether voice commands are really listening, or why not (plan item 38), so a
//          listener never talks to a microphone that is not open.

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.theme.Dimens

/**
 * A mic and one short line under the time left: the phrase to say while listening, or what went wrong. TalkBack
 * reads a change politely, so a failure is heard without looking.
 */
@Composable
internal fun VoiceCommandsLine(status: VoiceCommandsStatus) {
    val failed = status.failure != null
    val color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.SpaceXl)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Decorative: the line's words say it all.
        Icon(painterResource(R.drawable.ic_mic), null, Modifier.size(ICON_SIZE), tint = color)
        Text(stringResource(status.text), style = MaterialTheme.typography.labelMedium, color = color)
    }
}

private val ICON_SIZE = 16.dp
