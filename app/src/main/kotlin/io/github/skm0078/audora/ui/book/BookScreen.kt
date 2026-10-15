// File: app/src/main/kotlin/io/github/skm0078/audora/ui/book/BookScreen.kt
// Purpose: What opening a book shows until the reader (P1-05): password prompt and what Audora found in it.

package io.github.skm0078.audora.ui.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.ui.library.displayTitle
import io.github.skm0078.audora.ui.pdfcheck.PdfCheckSection
import io.github.skm0078.audora.ui.pdfcheck.PdfCheckState
import io.github.skm0078.audora.ui.pdfcheck.PdfCheckSummary
import io.github.skm0078.audora.ui.theme.AudoraTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookScreen(
    lookup: BookController.Lookup,
    checkState: PdfCheckState,
    onPasswordSubmitted: (String) -> Unit,
    onPasswordCancelled: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    if (lookup is BookController.Lookup.Found) {
                        Text(displayTitle(lookup.book), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (lookup) {
                BookController.Lookup.Loading -> CircularProgressIndicator()

                BookController.Lookup.NotInLibrary -> Text(
                    stringResource(R.string.book_not_in_library),
                    color = MaterialTheme.colorScheme.error,
                )

                is BookController.Lookup.Found -> {
                    Text(stringResource(R.string.book_reader_coming), style = MaterialTheme.typography.bodyMedium)
                    PdfCheckSection(
                        state = checkState,
                        onPasswordSubmitted = onPasswordSubmitted,
                        onPasswordCancelled = onPasswordCancelled,
                        onRetry = onRetry,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BookScreenPreview() {
    val book = Book(BookId("preview"), "content://x/1", "Moby Dick", 1_600_000, "v1:a", 574, 0, null)
    AudoraTheme {
        BookScreen(
            lookup = BookController.Lookup.Found(book),
            checkState = PdfCheckState.Ready(PdfCheckSummary(574, 3, 41, 0, 0, 38, "Call me Ishmael.")),
            onPasswordSubmitted = {},
            onPasswordCancelled = {},
            onRetry = {},
            onBack = {},
        )
    }
}
