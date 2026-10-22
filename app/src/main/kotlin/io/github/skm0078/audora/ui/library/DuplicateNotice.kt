// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/DuplicateNotice.kt
// Purpose: The duplicate check's dialog content (plan item 27), taken from the add's one-off message.

package io.github.skm0078.audora.ui.library

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
