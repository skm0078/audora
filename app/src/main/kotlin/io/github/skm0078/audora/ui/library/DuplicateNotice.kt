// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/DuplicateNotice.kt
// Purpose: The duplicate check's dialog (plan item 27), its content taken from the add's one-off message.

package io.github.skm0078.audora.ui.library

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId

/**
 * A picked PDF the library already has.
 *
 * @property moved the book now opens from the picked file (its old file had gone); false when nothing changed.
 */
data class DuplicateNotice(val id: BookId, val title: String, val moved: Boolean)

/** The dialog for [message], or null when it is not a picked duplicate (those keep the snackbar). */
fun duplicateOf(message: LibraryMessage): DuplicateNotice? = when (message) {
    is LibraryMessage.AlreadyInLibrary -> message.id?.let { DuplicateNotice(it, message.title, moved = false) }
    is LibraryMessage.FoundAgain -> message.id?.let { DuplicateNotice(it, message.title, moved = true) }
    is LibraryMessage.Added, is LibraryMessage.Removed, is LibraryMessage.Failed -> null
}

/** [id]'s book in the loaded library, or null while it loads or after the book was removed. */
internal fun bookIn(list: LibraryListState, id: BookId): Book? =
    (list as? LibraryListState.Loaded)?.all?.firstOrNull { it.book.id == id }?.book

/**
 * Plan item 27: a picked PDF the library already has. Audora keeps one copy, never a second: the fingerprint keys the
 * book's place, so two copies would fight over it (ADR-036). The dialog says so and offers the book.
 */
@Composable
internal fun DuplicateDialog(notice: DuplicateNotice, listState: LibraryListState, actions: LibraryActions) {
    val title = titleOrUntitled(notice.title)
    val onDismiss = actions::onMessageShown
    val onOpen: () -> Unit = {
        actions.onMessageShown()
        bookIn(listState, notice.id)?.let(actions::onOpenBook)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_duplicate_title)) },
        text = {
            val body = if (notice.moved) R.string.library_duplicate_moved else R.string.library_duplicate_same
            Text(stringResource(body, title))
        },
        confirmButton = { TextButton(onClick = onOpen) { Text(stringResource(R.string.library_duplicate_open)) } },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_duplicate_close)) }
        },
    )
}
