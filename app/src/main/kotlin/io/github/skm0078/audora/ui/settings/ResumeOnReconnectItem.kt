// File: app/src/main/kotlin/io/github/skm0078/audora/ui/settings/ResumeOnReconnectItem.kt
// Purpose: Settings' "Resume when headphones reconnect" switch (CF-4): off until the listener turns it on.

package io.github.skm0078.audora.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.PlayerControlsConfig
import io.github.skm0078.audora.playback.controls.ResumeOnReconnect
import io.github.skm0078.audora.ui.theme.SwitchOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The playback service reads the choice when headphones come back, so it applies at once. Read and written off the
 * main thread (the first read loads the file); the switch shows off and disabled until it is read.
 */
@Composable
internal fun ResumeOnReconnectItem(config: PlayerControlsConfig = PlayerControlsConfig()) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val choice = remember(context) { ResumeOnReconnect(context) }
    var on by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(choice) { on = withContext(Dispatchers.IO) { choice.on } }
    val minutes = (config.reconnectWindowMs / MS_PER_MINUTE).toInt()
    SwitchOption(
        stringResource(R.string.settings_reconnect_title),
        on == true,
        { turnOn ->
            on = turnOn
            scope.launch(Dispatchers.IO) { choice.set(turnOn) }
        },
        subtitle = stringResource(R.string.settings_reconnect_short),
        help = pluralStringResource(R.plurals.settings_reconnect_body, minutes, minutes),
        enabled = on != null,
    )
}

private const val MS_PER_MINUTE = 60_000L
