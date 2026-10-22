// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/BookDetailsSheet.kt
// Purpose: A book's details (UI plan, Book details): cover, pages, file size, when it was added and last read,
//          where reading stands, and the book's actions in one place.

package io.github.skm0078.audora.ui.library

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.BookAvailability
import io.github.skm0078.audora.ui.theme.Dimens

/**
 * Opened from a book's menu. Every action here also exists elsewhere (the tile, its menu), so the sheet only
 * gathers them; it dismisses itself before acting, so a removal dialog or the reader is never hidden behind it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookDetailsSheet(item: LibraryItem, actions: LibraryActions, covers: CoverSource, onDismiss: () -> Unit) {
    val then = { action: () -> Unit ->
        onDismiss()
        action()
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceS),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceL)) {
                BookCover(item.book, covers, Modifier.size(Dimens.CoverMedium, Dimens.CoverMedium * COVER_RATIO))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
                    Text(
                        displayTitle(item.book),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                    availabilityWarning(item.availability)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    item.progress?.let { ProgressLine(it) }
                }
            }
            DetailLines(item)
            DetailActions(item, actions, then)
        }
    }
}

@Composable
private fun DetailLines(item: LibraryItem) {
    val book = item.book
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
        book.pageCount?.let { Detail(pluralStringResource(R.plurals.library_pages, it, it)) }
        Detail(stringResource(R.string.book_details_size, Formatter.formatShortFileSize(context, book.sizeBytes)))
        Detail(stringResource(R.string.book_details_added, dateText(book.addedAtMillis)))
        book.lastOpenedAtMillis?.let { Detail(stringResource(R.string.book_details_opened, dateText(it))) }
    }
}

@Composable
private fun Detail(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * Continue, Open, Up next and Save as audiobook while the file can be read (a missing one is found from its tile),
 * then Remove.
 */
@Composable
private fun DetailActions(item: LibraryItem, actions: LibraryActions, then: (() -> Unit) -> Unit) {
    val book = item.book
    val readable = item.availability == BookAvailability.AVAILABLE
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        if (readable && item.progress != null) {
            Button(onClick = { then { actions.onContinue(book) } }, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.library_continue))
            }
        }
        if (readable) {
            OutlinedButton(onClick = { then { actions.onOpenBook(book) } }, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.book_details_open))
            }
            OutlinedButton(onClick = { then { actions.onAddToUpNext(book) } }, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.library_add_to_up_next))
            }
            OutlinedButton(onClick = { then { actions.onExportAudiobook(book) } }, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.library_export_audiobook))
            }
        }
        TextButton(onClick = { then { actions.onRequestRemoval(book) } }, Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.library_remove), color = MaterialTheme.colorScheme.error)
        }
    }
}

/** A date in the phone's own style ("17 Oct 2026"), not a relative time: details are for looking things up. */
@Composable
private fun dateText(millis: Long): String =
    DateUtils.formatDateTime(LocalContext.current, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR)

/** The open details sheet's book, while it is still in the library (a removed book closes the sheet). */
internal fun detailsItem(list: LibraryListState, bookId: String?): LibraryItem? =
    bookId?.let { id -> (list as? LibraryListState.Loaded)?.all?.firstOrNull { it.book.id.value == id } }
