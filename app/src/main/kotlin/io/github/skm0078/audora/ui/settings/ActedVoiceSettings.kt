// File: app/src/main/kotlin/io/github/skm0078/audora/ui/settings/ActedVoiceSettings.kt
// Purpose: The acted Hindi voice's Settings row state (ADR-059): download, cancel and delete through ActedVoices.

package io.github.skm0078.audora.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.acted.ActedVoices
import io.github.skm0078.audora.speech.ocr.OcrModelsState
import io.github.skm0078.audora.ui.loggingFailures

/** The row's state (null while it loads) and what its buttons do. */
data class ActedVoiceSettings(val state: OcrModelsState?, val onAction: (OcrModelsAction) -> Unit)

/** Null on a phone without the memory for the voice, unless it is already installed (so it can still be deleted). */
@Composable
fun rememberActedVoiceSettings(voices: ActedVoices): ActedVoiceSettings? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fits = remember(voices) { ActedVoices.fitsPhone(context) }
    val state by remember(voices) {
        voices.state().loggingFailures(TAG, "the acted Hindi voice")
    }.collectAsStateWithLifecycle(initialValue = null)
    if (!fits && state !is OcrModelsState.Ready) return null
    return ActedVoiceSettings(state) { action ->
        scope.runModelAction(action, voices::download, voices::cancel) {
            val deleted = voices.delete()
            if (deleted is Outcome.Failure) AudoraLog.w(TAG, "Acted Hindi voice not deleted: ${deleted.error}")
        }
    }
}

private const val TAG = "Settings.ActedVoice"
