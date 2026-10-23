// File: app/src/main/kotlin/io/github/skm0078/audora/ui/settings/OcrModelsItem.kt
// Purpose: Settings row for a downloadable model pack (OCR, punctuation, voice commands): state, download, delete.

package io.github.skm0078.audora.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.speech.ocr.OcrModelsState
import io.github.skm0078.audora.ui.theme.ConfirmedTextButton
import io.github.skm0078.audora.ui.voices.megabytes

/** The OCR models' row by default; [texts] names another pack (the punctuation model, RI-11). */
@Composable
internal fun OcrModelsItem(
    state: OcrModelsState,
    onAction: (OcrModelsAction) -> Unit,
    texts: ModelPackTexts = ModelPackTexts.OCR,
) {
    ListItem(
        headlineContent = { Text(stringResource(texts.title)) },
        supportingContent = { Column { OcrModelsStatus(state, texts) } },
        trailingContent = {
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
                if (what == OcrModelsAction.DELETE) {
                    // Deleting means a download of tens of megabytes to get it back: ask first.
                    ConfirmedTextButton(
                        stringResource(label),
                        stringResource(R.string.model_delete_question, stringResource(texts.title)),
                    ) { onAction(what) }
                } else {
                    TextButton(onClick = { onAction(what) }) { Text(stringResource(label)) }
                }
            }
        },
        colors = settingsRowColors(),
    )
}

@Composable
private fun OcrModelsStatus(state: OcrModelsState, texts: ModelPackTexts) {
    when (state) {
        is OcrModelsState.NotDownloaded -> Text(
            stringResource(texts.missing, megabytes(state.missingBytes)),
        )

        OcrModelsState.Queued -> Text(stringResource(R.string.ocr_models_queued))

        is OcrModelsState.Downloading -> {
            Text(stringResource(R.string.ocr_models_downloading, megabytes(state.bytes), megabytes(state.total)))
            LinearProgressIndicator(progress = { if (state.total > 0) state.bytes.toFloat() / state.total else 0f })
        }

        is OcrModelsState.Ready -> Text(stringResource(texts.ready, megabytes(state.bytes)))

        is OcrModelsState.Failed ->
            Text(stringResource(R.string.ocr_models_failed), color = MaterialTheme.colorScheme.error)

        OcrModelsState.Unavailable -> Text(stringResource(R.string.settings_ocr_unavailable))
    }
}

/** A pack's row title, what a download fetches (`%1$d` MB) and what it is once ready (`%1$d` MB). */
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
        val VOICE_COMMANDS = ModelPackTexts(
            R.string.commands_model_title,
            R.string.commands_model_missing,
            R.string.commands_model_ready,
        )
    }
}
