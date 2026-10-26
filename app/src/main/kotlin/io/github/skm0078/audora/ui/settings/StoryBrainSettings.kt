// File: app/src/main/kotlin/io/github/skm0078/audora/ui/settings/StoryBrainSettings.kt
// Purpose: The story brain's Settings row state (ADR story-brain): download, cancel and delete through
//          StoryBrainModels.

package io.github.skm0078.audora.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.brain.StoryBrainModels
import io.github.skm0078.audora.speech.ocr.OcrModelsState
import io.github.skm0078.audora.ui.loggingFailures

/** The row's state (null while it loads) and what its buttons do. */
data class StoryBrainSettings(val state: OcrModelsState?, val onAction: (OcrModelsAction) -> Unit)

/** Null on a phone that cannot run it (too little memory, or a build without its library): no row at all. */
@Composable
fun rememberStoryBrainSettings(models: StoryBrainModels): StoryBrainSettings? {
    val scope = rememberCoroutineScope()
    val state by remember(models) {
        models.state().loggingFailures(TAG, "the story brain")
    }.collectAsStateWithLifecycle(initialValue = null)
    if (state is OcrModelsState.Unavailable) return null
    return StoryBrainSettings(state) { action ->
        scope.runModelAction(action, models::download, models::cancel) {
            val deleted = models.delete()
            if (deleted is Outcome.Failure) AudoraLog.w(TAG, "Story brain not deleted: ${deleted.error}")
        }
    }
}

private const val TAG = "Settings.StoryBrain"
