// File: app/src/main/kotlin/io/github/skm0078/audora/ui/settings/SettingsRouteState.kt
// Purpose: State the Settings route keeps for a model pack row (OCR, punctuation) and the skip citations switch,
//          kept out of MainActivity so it stays within detekt's size limit.

package io.github.skm0078.audora.ui.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.playback.book.ReadingTextPreferences
import io.github.skm0078.audora.speech.ocr.OcrModelsState
import io.github.skm0078.audora.ui.loggingFailures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A model pack row's state (null while it loads) and what its buttons do. */
internal class ModelPackSetting(val state: OcrModelsState?, val onAction: (OcrModelsAction) -> Unit)

/** The row for one model pack: [what] names it in the log when its state cannot be read. */
@Composable
internal fun rememberModelPackSetting(
    tag: String,
    what: String,
    state: () -> Flow<OcrModelsState>,
    download: () -> Unit,
    cancel: () -> Unit,
    delete: suspend () -> Unit,
): ModelPackSetting {
    val scope = rememberCoroutineScope()
    val current by remember { state().loggingFailures(tag, what) }.collectAsStateWithLifecycle(initialValue = null)
    return ModelPackSetting(current) { action -> scope.runModelAction(action, download, cancel, delete) }
}

/** The skip citations switch as it shows, and its change. */
internal class SkipCitationsSetting(val skip: Boolean, val onChange: (Boolean) -> Unit)

@Composable
internal fun rememberSkipCitationsSetting(context: Context): SkipCitationsSetting {
    val scope = rememberCoroutineScope()
    val textPreferences = remember { ReadingTextPreferences.forContext(context) }
    // A one-line properties file, read once when Settings opens (like the onboarding flags). Saved off the main
    // thread, one write at a time and in order, so quick taps leave the file as the switch shows.
    var skip by remember { mutableStateOf(textPreferences.skipCitationMarkers()) }
    val textWrites = remember { Dispatchers.IO.limitedParallelism(1) }
    return SkipCitationsSetting(skip) { next ->
        skip = next
        scope.launch {
            val saved = withContext(textWrites) { textPreferences.setSkipCitationMarkers(next) }
            if (!saved) skip = !next // logged; the switch shows what books will use
        }
    }
}
