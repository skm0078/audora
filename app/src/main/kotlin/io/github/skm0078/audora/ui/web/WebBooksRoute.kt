// File: app/src/main/kotlin/io/github/skm0078/audora/ui/web/WebBooksRoute.kt
// Purpose: Books from the web on screen (CF-12, CF-15): the "Read a web page" dialog, the prompt for a shared link,
//          the book catalogues, the running download with Cancel, and what a finished one brings.

package io.github.skm0078.audora.ui.web

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.PendingShares
import io.github.skm0078.audora.R
import io.github.skm0078.audora.SharedText
import io.github.skm0078.audora.playback.web.WebAddress
import io.github.skm0078.audora.playback.web.WebBooks
import io.github.skm0078.audora.playback.web.WebBooksOnPhone
import io.github.skm0078.audora.playback.web.WebError
import io.github.skm0078.audora.playback.web.WebJob
import io.github.skm0078.audora.playback.web.WebJobKind
import io.github.skm0078.audora.playback.web.WebResult
import io.github.skm0078.audora.ui.theme.Dimens

/** Which web dialog the library's menu opened. */
internal enum class WebSheet { ADDRESS, CATALOGUES }

/**
 * What the web dialogs show, for the whole process: a rotation keeps them, and a link shared while the reader is open
 * is asked about there. Main thread only, like the Compose state it is.
 */
internal object WebBooksUi {
    var sheet: WebSheet? by mutableStateOf(null)

    /** A link shared from another app, until the listener says whether to read it. */
    var sharedLink: String? by mutableStateOf(null)
}

/**
 * Drawn over every screen. Nothing is fetched until the listener taps Read or Download. An article becomes shared
 * text (saved and opened like any shared text); a book file goes to [onBook] to be added and opened.
 */
@Composable
internal fun WebBooksRoute(onBook: (String) -> Unit) {
    val context = LocalContext.current
    val books = remember { WebBooksOnPhone.get(context) }
    val job by books.job.collectAsStateWithLifecycle()
    val result by books.result.collectAsStateWithLifecycle()
    LaunchedEffect(result) {
        val done = result ?: return@LaunchedEffect
        books.consume()
        when (done) {
            is WebResult.Article -> PendingShares.text = SharedText(done.article.title, done.article.text)

            is WebResult.Book -> {
                WebBooksUi.sheet = null
                onBook(Uri.fromFile(done.file).toString())
            }

            is WebResult.Failed -> Toast.makeText(context, webErrorText(context, done.error), Toast.LENGTH_LONG).show()
        }
    }
    val start = { started: Boolean ->
        if (!started) Toast.makeText(context, R.string.web_busy, Toast.LENGTH_LONG).show()
    }
    when (WebBooksUi.sheet) {
        WebSheet.ADDRESS -> WebAddressDialog(
            onRead = { address ->
                WebBooksUi.sheet = null
                start(books.readPage(address))
            },
            onDismiss = { WebBooksUi.sheet = null },
        )

        WebSheet.CATALOGUES -> CatalogueDialog(books, onDownloadStarted = start) { WebBooksUi.sheet = null }

        null -> Unit
    }
    WebBooksUi.sharedLink?.let { link ->
        SharedLinkDialog(
            link = link,
            onRead = {
                WebBooksUi.sharedLink = null
                start(books.readPage(link))
            },
            onDismiss = { WebBooksUi.sharedLink = null },
        )
    }
    job?.let { running -> WebJobDialog(running, books) }
}

/** The listener types or pastes an address; Read is offered once it is a web address. */
@Composable
private fun WebAddressDialog(onRead: (String) -> Unit, onDismiss: () -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    val address = WebAddress.normalize(typed)
    val read = { address?.let(onRead) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.web_page_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                Text(stringResource(R.string.web_page_explain), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text(stringResource(R.string.web_page_address)) },
                    singleLine = true,
                    isError = typed.isNotBlank() && address == null,
                    supportingText = if (typed.isNotBlank() && address == null) {
                        { Text(stringResource(R.string.web_error_address)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { read() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { read() }, enabled = address != null) { Text(stringResource(R.string.web_read)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.web_cancel)) } },
    )
}

/** A link shared from a browser or chat: read only once the listener says so, naming the site it comes from. */
@Composable
private fun SharedLinkDialog(link: String, onRead: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.web_shared_link_title)) },
        text = { Text(stringResource(R.string.web_shared_link_body, WebAddress.hostOf(link))) },
        confirmButton = { TextButton(onClick = onRead) { Text(stringResource(R.string.web_read)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.web_cancel)) } },
    )
}

/** The running download, with how far it is when the site says the size, and Cancel. */
@Composable
private fun WebJobDialog(job: WebJob, books: WebBooks) {
    AlertDialog(
        // Only Cancel ends it: tapping beside the dialog must not leave a download running unseen.
        onDismissRequest = {},
        title = {
            val title = when (job.kind) {
                WebJobKind.PAGE -> R.string.web_getting_page
                WebJobKind.BOOK -> R.string.web_downloading_book
            }
            Text(stringResource(title, job.label))
        },
        text = {
            val progress = job.progress
            if (progress == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = books::cancel) { Text(stringResource(R.string.web_cancel)) } },
    )
}

/** What went wrong, in words the listener can act on. */
internal fun webErrorText(context: Context, error: WebError): String = when (error) {
    WebError.BadAddress -> context.getString(R.string.web_error_address)
    is WebError.Unreachable -> context.getString(R.string.web_error_unreachable)
    is WebError.Refused -> context.getString(R.string.web_error_refused, error.code)
    WebError.TooBig -> context.getString(R.string.web_error_too_big)
    WebError.NoArticle -> context.getString(R.string.web_error_no_article)
    WebError.NotACatalogue -> context.getString(R.string.web_error_not_catalogue)
    is WebError.NotSaved -> context.getString(R.string.web_error_not_saved)
}
