// File: app/src/main/kotlin/io/github/skm0078/audora/ui/settings/OcrModelsItem.kt
// Purpose: Settings row for the OCR models (P3-05): their state, and download, cancel or delete.

package io.github.skm0078.audora.ui.settings

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
import io.github.skm0078.audora.ui.voices.megabytes

/** What the row can ask the OCR model manager to do. */
enum class OcrModelsAction { DOWNLOAD, CANCEL, DELETE }

@Composable
internal fun OcrModelsItem(state: OcrModelsState, onAction: (OcrModelsAction) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_ocr_title)) },
        supportingContent = { Column { OcrModelsStatus(state) } },
        trailingContent = {
            val action = when (state) {
                is OcrModelsState.NotDownloaded, is OcrModelsState.Failed ->
                    OcrModelsAction.DOWNLOAD to R.string.ocr_models_download_short

                OcrModelsState.Queued, is OcrModelsState.Downloading -> OcrModelsAction.CANCEL to R.string.ocr_models_cancel

                is OcrModelsState.Ready -> OcrModelsAction.DELETE to R.string.ocr_models_delete

                OcrModelsState.Unavailable -> null
            }
            action?.let { (what, label) -> TextButton(onClick = { onAction(what) }) { Text(stringResource(label)) } }
        },
    )
}

@Composable
private fun OcrModelsStatus(state: OcrModelsState) {
    when (state) {
        is OcrModelsState.NotDownloaded -> Text(stringResource(R.string.settings_ocr_missing, megabytes(state.missingBytes)))

        OcrModelsState.Queued -> Text(stringResource(R.string.ocr_models_queued))

        is OcrModelsState.Downloading -> {
            Text(stringResource(R.string.ocr_models_downloading, megabytes(state.bytes), megabytes(state.total)))
            LinearProgressIndicator(progress = { if (state.total > 0) state.bytes.toFloat() / state.total else 0f })
        }

        is OcrModelsState.Ready -> Text(stringResource(R.string.settings_ocr_ready, megabytes(state.bytes)))

        is OcrModelsState.Failed ->
            Text(stringResource(R.string.ocr_models_failed, state.message), color = MaterialTheme.colorScheme.error)

        OcrModelsState.Unavailable -> Text(stringResource(R.string.settings_ocr_unavailable))
    }
}
