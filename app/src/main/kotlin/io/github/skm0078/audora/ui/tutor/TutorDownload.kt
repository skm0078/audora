// File: app/src/main/kotlin/io/github/skm0078/audora/ui/tutor/TutorDownload.kt
// Purpose: The reading tutor's speech model offer: its sizes, the download's progress, cancel and retry (item 67).

package io.github.skm0078.audora.ui.tutor

import android.text.format.Formatter
import androidx.annotation.StringRes
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
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.speech.listen.ListenPin
import io.github.skm0078.audora.speech.ocr.OcrModelsState

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
