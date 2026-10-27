// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/BookDetailsSheet.kt
// Purpose: A book's details (UI plan, Book details): cover, author, format, language, pages, file size, when it was
//          added and last read, where reading stands, its collections (ADR-069), the book's actions and what was made
//          from it (ADR-068).

package io.github.skm0078.audora.ui.library

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.annotation.StringRes
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.BookAvailability
import io.github.skm0078.audora.core.library.DocumentFormat
import io.github.skm0078.audora.ui.made.MadeFromBook
import io.github.skm0078.audora.ui.theme.AppSheet
import io.github.skm0078.audora.ui.theme.Dimens
import java.util.Locale

/** A book's details sheet, and Edit details opened from it (ADR-068); [details] is the book whose sheet is open. */
@Composable
internal fun BookSheets(
    listState: LibraryListState,
    actions: LibraryActions,
    covers: CoverSource,
    details: String?,
    onCloseDetails: () -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val writer = rememberCollectionWriter()
    val loaded = listState as? LibraryListState.Loaded
    detailsItem(listState, details)?.let { item ->
        val id = item.book.id
        BookDetailsSheet(item, actions, covers, onEdit = { editing = id.value }, onDismiss = onCloseDetails) {
            BookCollectionChips(
                collections = loaded?.collections.orEmpty(),
                memberOf = loaded?.members?.get(id).orEmpty(),
                onToggle = { collection, member -> writer.setMember(id, collection, member) },
                onCreate = { name -> writer.create(name, add = id) },
            )
        }
    }
    detailsItem(listState, editing)?.let { BookEditRoute(it.book) { editing = null } }
}

/**
 * Opened from a book's menu. Every action here also exists elsewhere (the tile, its menu), so the sheet only
 * gathers them; it dismisses itself before acting, so a removal dialog or the reader is never hidden behind it.
 * [collections] is the book's collection chips (ADR-069), which change at once and keep the sheet open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookDetailsSheet(
    item: LibraryItem,
    actions: LibraryActions,
    covers: CoverSource,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
    collections: @Composable () -> Unit = {},
) {
    val then = { action: () -> Unit ->
        onDismiss()
        action()
    }
    AppSheet(onDismissRequest = onDismiss) {
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
            collections()
            DetailActions(item, actions, then)
            TextButton(onClick = { then(onEdit) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.book_edit)) }
            MadeFromBook(item.book.id) { setKey -> then { actions.onPlayMade(setKey) } }
        }
    }
}

@Composable
private fun DetailLines(item: LibraryItem) {
    val book = item.book
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
        book.author?.let { Detail(stringResource(R.string.book_details_author, it)) }
        book.format?.let { Detail(stringResource(R.string.book_details_format, stringResource(formatName(it)))) }
        book.language?.let { Detail(stringResource(R.string.book_details_language, languageName(it))) }
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

@StringRes
internal fun formatName(format: DocumentFormat): Int = when (format) {
    DocumentFormat.PDF -> R.string.book_format_pdf
    DocumentFormat.EPUB -> R.string.book_format_epub
    DocumentFormat.TEXT -> R.string.book_format_text
    DocumentFormat.DOCX -> R.string.book_format_docx
    DocumentFormat.ODT -> R.string.book_format_odt
    DocumentFormat.RTF -> R.string.book_format_rtf
    DocumentFormat.HTML -> R.string.book_format_html
    DocumentFormat.FB2 -> R.string.book_format_fb2
    DocumentFormat.MOBI -> R.string.book_format_mobi
    DocumentFormat.CHM -> R.string.book_format_chm
    DocumentFormat.DJVU -> R.string.book_format_djvu
}

/** "Bengali", "বাংলা" or "बांग्ला": the language's name in the phone's language. */
@Composable
internal fun languageName(tag: String): String {
    val shown = LocalConfiguration.current.locales[0]
    return Locale.forLanguageTag(tag).getDisplayLanguage(shown).ifEmpty { tag }
}

/** A date in the phone's own style ("17 Oct 2026"), not a relative time: details are for looking things up. */
@Composable
private fun dateText(millis: Long): String =
    DateUtils.formatDateTime(LocalContext.current, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR)

/** The open details sheet's book, while it is still in the library (a removed book closes the sheet). */
internal fun detailsItem(list: LibraryListState, bookId: String?): LibraryItem? =
    bookId?.let { id -> (list as? LibraryListState.Loaded)?.all?.firstOrNull { it.book.id.value == id } }
