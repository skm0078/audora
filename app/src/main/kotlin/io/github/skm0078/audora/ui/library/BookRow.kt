// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/BookRow.kt
// Purpose: One library row: title, pages and last use, a clear warning when the file is gone, and Remove.

package io.github.skm0078.audora.ui.library

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookAvailability
import io.github.skm0078.audora.core.library.BookTitles
import io.github.skm0078.audora.core.library.LibraryError

@Composable
internal fun BookRow(item: LibraryItem, onTap: () -> Unit, onRemove: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = {
            Text(displayTitle(item.book), maxLines = TITLE_LINES, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            val warning = availabilityWarning(item.availability)
            if (warning != null) {
                Text(warning, color = MaterialTheme.colorScheme.error)
            } else {
                Text(bookDetails(item.book))
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.library_book_menu))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_remove)) },
                        onClick = {
                            menuOpen = false
                            onRemove()
                        },
                    )
                }
            }
        },
        modifier = Modifier.clickable(onClick = onTap),
    )
}

@Composable
internal fun displayTitle(book: Book): String = titleOrUntitled(book.title)

@Composable
private fun availabilityWarning(availability: BookAvailability?): String? = when (availability) {
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
private fun titleOrUntitled(title: String): String =
    title.takeUnless { it == BookTitles.UNTITLED } ?: stringResource(R.string.library_untitled)

private fun errorText(error: LibraryError): Int = when (error) {
    LibraryError.CannotKeepAccess -> R.string.library_error_cannot_keep
    LibraryError.Unreadable -> R.string.library_error_unreadable
    LibraryError.NotAPdf -> R.string.library_error_not_pdf
    LibraryError.DifferentFile -> R.string.library_error_different_file
    LibraryError.NotInLibrary -> R.string.library_error_not_in_library
    is LibraryError.Storage -> R.string.library_error_storage
}

private const val TITLE_LINES = 2
