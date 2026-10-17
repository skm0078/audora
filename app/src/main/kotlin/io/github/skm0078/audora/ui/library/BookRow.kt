// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/BookRow.kt
// Purpose: A library book as a list row or a grid tile: cover, title, progress, a warning when the file is gone, menu.

package io.github.skm0078.audora.ui.library

import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookAvailability
import io.github.skm0078.audora.core.library.BookTitles
import io.github.skm0078.audora.core.library.LibraryError

@Composable
internal fun BookRow(
    item: LibraryItem,
    onTap: () -> Unit,
    onRemove: () -> Unit,
    onExport: () -> Unit,
    covers: CoverSource = CoverSource.None,
    onAddToUpNext: () -> Unit = {},
) {
    ListItem(
        leadingContent = { BookCover(item.book, covers) },
        headlineContent = {
            Text(displayTitle(item.book), maxLines = TITLE_LINES, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            val warning = availabilityWarning(item.availability)
            if (warning != null) {
                Text(warning, color = MaterialTheme.colorScheme.error)
            } else {
                Column {
                    Text(bookDetails(item.book))
                    item.progress?.let { ProgressLine(it) }
                }
            }
        },
        trailingContent = { BookMenu(item, onRemove, onExport, onAddToUpNext) },
        modifier = Modifier.clickable(onClick = onTap),
    )
}

/** A book's menu: Up next and Save as audiobook while the file can be opened, and Remove. */
@Composable
internal fun BookMenu(item: LibraryItem, onRemove: () -> Unit, onExport: () -> Unit, onAddToUpNext: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.library_book_menu))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            val choose = { action: () -> Unit ->
                menuOpen = false
                action()
            }
            // Only a book Audora can still open can be queued or read into audio files (ADR-034).
            if (item.availability == BookAvailability.AVAILABLE) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.library_add_to_up_next)) },
                    onClick = { choose(onAddToUpNext) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.library_export_audiobook)) },
                    onClick = { choose(onExport) },
                )
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.library_remove)) }, onClick = { choose(onRemove) })
        }
    }
}

@Composable
internal fun displayTitle(book: Book): String = titleOrUntitled(book.title)

@Composable
internal fun availabilityWarning(availability: BookAvailability?): String? = when (availability) {
    BookAvailability.MISSING -> stringResource(R.string.library_file_missing)
    BookAvailability.ACCESS_LOST -> stringResource(R.string.library_access_lost)
    BookAvailability.AVAILABLE, null -> null
}

@Composable
private fun bookDetails(book: Book): String {
    val lastOpened = book.lastOpenedAtMillis
    val whenText = if (lastOpened != null) {
        stringResource(R.string.library_opened_ago, relativeTime(lastOpened))
    } else {
        stringResource(R.string.library_added_ago, relativeTime(book.addedAtMillis))
    }
    val pages = book.pageCount ?: return whenText
    return stringResource(
        R.string.library_details,
        pluralStringResource(R.plurals.library_pages, pages, pages),
        whenText,
    )
}

/** "5 minutes ago", "yesterday", in the phone's language. Recomputed when the row recomposes. */
private fun relativeTime(millis: Long): String = DateUtils.getRelativeTimeSpanString(
    millis,
    System.currentTimeMillis(),
    DateUtils.MINUTE_IN_MILLIS,
).toString()

@Composable
internal fun messageText(message: LibraryMessage): String = when (message) {
    is LibraryMessage.Added -> stringResource(R.string.library_added, titleOrUntitled(message.title))
    is LibraryMessage.AlreadyInLibrary -> stringResource(R.string.library_already, titleOrUntitled(message.title))
    is LibraryMessage.FoundAgain -> stringResource(R.string.library_found_again, titleOrUntitled(message.title))
    is LibraryMessage.Removed -> stringResource(R.string.library_removed, titleOrUntitled(message.title))
    is LibraryMessage.Failed -> stringResource(errorText(message.error))
}

@Composable
internal fun titleOrUntitled(title: String): String =
    title.takeUnless { it == BookTitles.UNTITLED } ?: stringResource(R.string.library_untitled)

private fun errorText(error: LibraryError): Int = when (error) {
    LibraryError.CannotKeepAccess -> R.string.library_error_cannot_keep
    LibraryError.Unreadable -> R.string.library_error_unreadable
    LibraryError.NotAPdf -> R.string.library_error_not_pdf
    LibraryError.DifferentFile -> R.string.library_error_different_file
    LibraryError.NotInLibrary -> R.string.library_error_not_in_library
    is LibraryError.Storage -> R.string.library_error_storage
}

internal const val TITLE_LINES = 2

/** The first page once drawn (plan item 21); until then, or without one, a paper-coloured card with the initial. */
@Composable
internal fun BookCover(
    book: Book,
    covers: CoverSource,
    modifier: Modifier = Modifier.size(COVER_WIDTH, COVER_HEIGHT),
) {
    val cover by produceState<ImageBitmap?>(null, book.id, book.documentUri) { value = covers.cover(book) }
    val shape = RoundedCornerShape(COVER_CORNER)
    Box(
        modifier = modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val image = cover
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                displayTitle(book).take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val COVER_WIDTH = 40.dp
private val COVER_HEIGHT = 56.dp
private val COVER_CORNER = 4.dp
