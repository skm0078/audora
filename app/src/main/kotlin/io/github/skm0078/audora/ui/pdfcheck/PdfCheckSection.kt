// File: app/src/main/kotlin/io/github/skm0078/audora/ui/pdfcheck/PdfCheckSection.kt
// Purpose: Password prompt and extraction summary for an opened book, until the reader screen (P1-05).

package io.github.skm0078.audora.ui.pdfcheck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.theme.AudoraTheme

@Composable
fun PdfCheckSection(
    state: PdfCheckState,
    onPasswordSubmitted: (String) -> Unit,
    onPasswordCancelled: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            PdfCheckState.Idle -> Button(onClick = onRetry) { Text(stringResource(R.string.pdf_check_retry)) }

            PdfCheckState.Opening -> CircularProgressIndicator()

            is PdfCheckState.NeedsPassword -> PasswordPromptDialog(
                wrongPassword = state.wrongPassword,
                onSubmit = onPasswordSubmitted,
                onDismiss = onPasswordCancelled,
            )

            is PdfCheckState.Ready -> SummaryCard(state.summary)

            is PdfCheckState.Failed -> Text(
                stringResource(failureText(state.reason)),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SummaryCard(summary: PdfCheckSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.pdf_check_pages, summary.pageCount, summary.pagesChecked, summary.millis),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(
                    R.string.pdf_check_details,
                    summary.sentences,
                    summary.actualTextSpans,
                    summary.unicodeMapErrors,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                summary.firstSentence ?: stringResource(R.string.pdf_check_no_text),
                style = MaterialTheme.typography.bodySmall,
                maxLines = FIRST_SENTENCE_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun failureText(reason: PdfCheckFailure): Int = when (reason) {
    PdfCheckFailure.DAMAGED -> R.string.pdf_check_damaged
    PdfCheckFailure.UNREADABLE -> R.string.pdf_check_unreadable
    PdfCheckFailure.ENGINE_UNAVAILABLE -> R.string.pdf_check_engine
    PdfCheckFailure.PAGE_FAILED -> R.string.pdf_check_page_failed
}

private const val FIRST_SENTENCE_LINES = 3

@Preview(showBackground = true)
@Composable
private fun PdfCheckSectionPreview() {
    AudoraTheme {
        PdfCheckSection(
            state = PdfCheckState.Ready(PdfCheckSummary(574, 3, 41, 0, 0, 38, "Universal Declaration of Human Rights")),
            onPasswordSubmitted = {},
            onPasswordCancelled = {},
            onRetry = {},
        )
    }
}
