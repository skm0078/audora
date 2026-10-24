// File: app/src/main/kotlin/io/github/skm0078/audora/ui/tutor/TutorDownload.kt
// Purpose: The reading tutor's model offer (sizes, progress, cancel, retry) and its recovery buttons (item 67).

package io.github.skm0078.audora.ui.tutor

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.playback.tutor.TutorError
import io.github.skm0078.audora.speech.listen.ListenPin
import io.github.skm0078.audora.speech.ocr.OcrModelsState
import io.github.skm0078.audora.ui.jobs.DownloadTimeLeft

/** Offers [lang]'s model with its download and on-phone sizes; [download] null while its state loads. */
@Composable
internal fun TutorDownloadOffer(lang: Lang, download: OcrModelsState?, onDownload: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val pin = ListenPin.of(lang)
    val size = Formatter.formatShortFileSize(context, pin.sizeBytes)
    val onPhone = Formatter.formatShortFileSize(context, pin.unpackedBytes)
    Text(stringResource(R.string.tutor_needs_model, stringResource(langName(lang)), size, onPhone))
    when (download) {
        is OcrModelsState.Downloading -> {
            val fraction = if (download.total > 0) download.bytes.toFloat() / download.total else 0f
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            DownloadTimeLeft(download.bytes, download.total)
            TextButton(onClick = onCancel) { Text(stringResource(R.string.tutor_cancel_download)) }
        }

        OcrModelsState.Queued -> Text(stringResource(R.string.tutor_queued))

        is OcrModelsState.Failed -> {
            Text(
                stringResource(R.string.tutor_download_failed, download.message),
                color = MaterialTheme.colorScheme.error,
            )
            OutlinedButton(onClick = onDownload) { Text(stringResource(R.string.tutor_download)) }
        }

        null -> LinearProgressIndicator(Modifier.fillMaxWidth())

        else -> OutlinedButton(onClick = onDownload) { Text(stringResource(R.string.tutor_download)) }
    }
}

@StringRes
private fun langName(lang: Lang): Int = when (lang) {
    Lang.EN -> R.string.tutor_lang_en
    Lang.HI -> R.string.tutor_lang_hi
    Lang.BN -> R.string.tutor_lang_bn
}

/** What the reader can do about [failure]: retry or re-download a model, or open settings for the microphone. */
@Composable
internal fun FailureActions(failure: TutorError?, retry: () -> Unit, downloadAgain: () -> Unit) {
    when (failure) {
        TutorError.MODEL_FAILED -> ModelFailedActions(retry, downloadAgain)
        TutorError.NO_PERMISSION -> OpenAppSettings()
        TutorError.MICROPHONE_FAILED, null -> Unit
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelFailedActions(retry: () -> Unit, downloadAgain: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = retry) { Text(stringResource(R.string.tutor_try_again)) }
        OutlinedButton(onClick = downloadAgain) { Text(stringResource(R.string.tutor_download_again)) }
    }
}

/** After "Don't ask again" Android shows no prompt; this opens Audora's page in the phone's settings instead. */
@Composable
private fun OpenAppSettings() {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            val app = Uri.fromParts("package", context.packageName, null)
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app)
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        },
    ) { Text(stringResource(R.string.tutor_open_settings)) }
}
