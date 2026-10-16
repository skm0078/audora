// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/PageOriginNotice.kt
// Purpose: The reader's "OCR" badge and scanned-page notice: where a page's words come from (P3-05).

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.speech.ocr.OcrModelsState
import io.github.skm0078.audora.ui.theme.AudoraTheme
import io.github.skm0078.audora.ui.voices.megabytes

/**
 * Nothing for a page read from its own text. "OCR" for a page read from its image (Build Spec §5 step 3c: the page
 * shows it, because OCR can misread). For a scanned page that cannot be read yet: why, and the one tap that fixes it.
 */
@Composable
internal fun PageOriginNotice(origin: PageOrigin, ocrModels: OcrModelsState?, onDownload: () -> Unit) {
    when (origin) {
        PageOrigin.TEXT -> Unit
        PageOrigin.OCR -> OcrBadge()
        PageOrigin.NEEDS_OCR_MODELS -> NeedsModelsCard(ocrModels, onDownload)
        PageOrigin.OCR_FAILED -> NoticeCard(stringResource(R.string.reader_ocr_failed))
    }
}

@Composable
private fun OcrBadge() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
            Text(
                stringResource(R.string.reader_ocr_badge),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Text(
            stringResource(R.string.reader_ocr_badge_body),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NeedsModelsCard(state: OcrModelsState?, onDownload: () -> Unit) {
    NoticeCard(stringResource(R.string.reader_ocr_needs_models)) {
        when (state) {
            is OcrModelsState.NotDownloaded -> Button(onClick = onDownload) {
                Text(stringResource(R.string.ocr_models_download, megabytes(state.missingBytes)))
            }

            is OcrModelsState.Failed -> {
                Text(
                    stringResource(R.string.ocr_models_failed, state.message),
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onDownload) { Text(stringResource(R.string.ocr_models_retry)) }
            }

            OcrModelsState.Queued -> Text(stringResource(R.string.ocr_models_queued))

            is OcrModelsState.Downloading -> {
                Text(stringResource(R.string.ocr_models_downloading, megabytes(state.bytes), megabytes(state.total)))
                LinearProgressIndicator(
                    progress = { if (state.total > 0) state.bytes.toFloat() / state.total else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Ready: the page is read again as soon as the models land (MainActivity); Unavailable: a broken build.
            is OcrModelsState.Ready, OcrModelsState.Unavailable, null -> Unit
        }
    }
}

@Composable
private fun NoticeCard(text: String, content: @Composable () -> Unit = {}) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            content()
        }
    }
}

@Preview
@Composable
private fun PageOriginNoticePreview() {
    AudoraTheme {
        Column {
            PageOriginNotice(PageOrigin.OCR, null) {}
            PageOriginNotice(PageOrigin.NEEDS_OCR_MODELS, OcrModelsState.NotDownloaded(38_341_592)) {}
            PageOriginNotice(PageOrigin.NEEDS_OCR_MODELS, OcrModelsState.Downloading(12_000_000, 38_341_592)) {}
        }
    }
}
