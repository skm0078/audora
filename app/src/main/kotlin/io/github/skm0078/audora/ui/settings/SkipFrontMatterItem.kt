// File: app/src/main/kotlin/io/github/skm0078/audora/ui/settings/SkipFrontMatterItem.kt
// Purpose: Settings' "Start new books at the first chapter" switch: on unless the listener turns it off
//          (ADR 2026-10-31-skip-front-matter).

package io.github.skm0078.audora.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.reader.BookStartPreferences
import io.github.skm0078.audora.ui.theme.SwitchOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The reader reads the switch when a book opens, so it applies from the next unread book opened. Read and written off
 * the main thread; the switch shows disabled until it is read.
 */
@Composable
internal fun SkipFrontMatterItem() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { BookStartPreferences(context) }
    var on by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(prefs) { on = withContext(Dispatchers.IO) { prefs.skipFrontMatter() } }
    SwitchOption(
        stringResource(R.string.settings_skip_front_matter_title),
        on ?: true,
        { turnOn ->
            on = turnOn
            scope.launch(Dispatchers.IO) { prefs.setSkipFrontMatter(turnOn) }
        },
        subtitle = stringResource(R.string.settings_skip_front_matter_short),
        help = stringResource(R.string.settings_skip_front_matter_body),
        enabled = on != null,
    )
}
