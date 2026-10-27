// File: app/src/main/kotlin/io/github/skm0078/audora/ui/settings/OcrModelsItem.kt
// Purpose: Settings row for a downloadable model pack (OCR, punctuation, voice commands): state, download, delete.

package io.github.skm0078.audora.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.speech.ocr.OcrModelsState
import io.github.skm0078.audora.ui.jobs.DownloadTimeLeft
import io.github.skm0078.audora.ui.theme.ConfirmedTextButton
import io.github.skm0078.audora.ui.theme.HelpButton
import io.github.skm0078.audora.ui.theme.optionRowColors
import io.github.skm0078.audora.ui.voices.megabytes

/**
 * The OCR models' row by default; [texts] names another pack (the punctuation model, RI-11). The row says its state
 * in one line; what the pack does waits behind the info button while it can still be downloaded, when it matters.
 */
@Composable
internal fun OcrModelsItem(
    state: OcrModelsState,
    onAction: (OcrModelsAction) -> Unit,
    texts: ModelPackTexts = ModelPackTexts.OCR,
) {
    // The action sits under the status, not in the trailing slot: beside a long title at large text sizes it
    // squeezed the title and status into a narrow column that broke words mid-way. Only the small help icon trails.
    ListItem(
        headlineContent = { Text(stringResource(texts.title)) },
        supportingContent = {
            Column {
                OcrModelsStatus(state, texts)
                PackActionButton(state, texts, onAction)
            }
        },
        trailingContent = if (state is OcrModelsState.NotDownloaded) {
            {
                HelpButton(
                    stringResource(texts.title),
                    stringResource(texts.missing, megabytes(state.missingBytes)),
                )
            }
        } else {
            null
        },
        colors = optionRowColors(),
    )
}

/** Download and Retry are filled, so the next step stands out; Cancel and Delete stay quiet text buttons. */
@Composable
private fun PackActionButton(state: OcrModelsState, texts: ModelPackTexts, onAction: (OcrModelsAction) -> Unit) {
    val action = when (state) {
        is OcrModelsState.NotDownloaded -> OcrModelsAction.DOWNLOAD to R.string.ocr_models_download_short

        is OcrModelsState.Failed -> OcrModelsAction.DOWNLOAD to R.string.ocr_models_retry

        OcrModelsState.Queued, is OcrModelsState.Downloading ->
            OcrModelsAction.CANCEL to
                R.string.ocr_models_cancel

        is OcrModelsState.Ready -> OcrModelsAction.DELETE to R.string.ocr_models_delete

        OcrModelsState.Unavailable -> null
    }
    action?.let { (what, label) ->
        when (what) {
            // Deleting means a download of tens of megabytes to get it back: ask first.
            OcrModelsAction.DELETE -> ConfirmedTextButton(
                stringResource(label),
                stringResource(R.string.model_delete_question, stringResource(texts.title)),
            ) { onAction(what) }

            OcrModelsAction.DOWNLOAD -> FilledTonalButton(onClick = { onAction(what) }) { Text(stringResource(label)) }

            OcrModelsAction.CANCEL -> TextButton(onClick = { onAction(what) }) { Text(stringResource(label)) }
        }
    }
}

@Composable
private fun OcrModelsStatus(state: OcrModelsState, texts: ModelPackTexts) {
    when (state) {
        is OcrModelsState.NotDownloaded -> Text(
            stringResource(R.string.pack_not_downloaded, megabytes(state.missingBytes)),
        )

        OcrModelsState.Queued -> Text(stringResource(R.string.ocr_models_queued))

        is OcrModelsState.Downloading -> {
            Text(stringResource(R.string.ocr_models_downloading, megabytes(state.bytes), megabytes(state.total)))
            LinearProgressIndicator(progress = { if (state.total > 0) state.bytes.toFloat() / state.total else 0f })
            DownloadTimeLeft(state.bytes, state.total)
        }

        is OcrModelsState.Ready -> Text(stringResource(texts.ready, megabytes(state.bytes)))

        is OcrModelsState.Failed ->
            Text(stringResource(R.string.ocr_models_failed), color = MaterialTheme.colorScheme.error)

        OcrModelsState.Unavailable -> Text(stringResource(R.string.settings_ocr_unavailable))
    }
}

/** A pack's row title, what it does and fetches (`%1$d` MB, its help) and what it is once ready (`%1$d` MB). */
internal data class ModelPackTexts(@StringRes val title: Int, @StringRes val missing: Int, @StringRes val ready: Int) {
    companion object {
        val OCR =
            ModelPackTexts(R.string.settings_ocr_title, R.string.settings_ocr_missing, R.string.settings_ocr_ready)
        val PUNCTUATION = ModelPackTexts(
            R.string.settings_punct_title,
            R.string.settings_punct_missing,
            R.string.settings_punct_ready,
        )

        /** [PUNCTUATION] while scanned pages (OCR) are not downloaded: ready, but nothing for it to work on yet. */
        val PUNCTUATION_NEEDS_OCR = ModelPackTexts(
            R.string.settings_punct_title,
            R.string.settings_punct_missing,
            R.string.settings_punct_ready_needs_ocr,
        )
        val ACTED = ModelPackTexts(R.string.acted_model_title, R.string.acted_model_missing, R.string.acted_model_ready)
        val STORY_BRAIN = ModelPackTexts(
            R.string.brain_model_title,
            R.string.brain_model_missing,
            R.string.brain_model_ready,
        )
        val VOICE_COMMANDS = ModelPackTexts(
            R.string.commands_model_title,
            R.string.commands_model_missing,
            R.string.commands_model_ready,
        )
    }
}
