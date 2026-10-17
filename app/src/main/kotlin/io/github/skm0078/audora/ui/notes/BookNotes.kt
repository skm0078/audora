// File: app/src/main/kotlin/io/github/skm0078/audora/ui/notes/BookNotes.kt
// Purpose: The Notes tab's content: every book's bookmarks from the bookmark store, books without any left out.

package io.github.skm0078.audora.ui.notes

import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.notes.Bookmark
import io.github.skm0078.audora.core.notes.BookmarkStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** One book's bookmarks, in the store's order. */
data class BookNotes(val book: Book, val bookmarks: List<Bookmark>)

private const val TAG = "Audora.Notes"

/**
 * Every book's bookmarks, books without any left out, in library order. One store query per book: libraries are a
 * few dozen books, and the store has no cross-book query yet. A book whose bookmarks cannot be read is logged and
 * shows none, so one bad row never empties the tab.
 */
fun bookNotes(store: BookmarkStore, books: List<Book>): Flow<List<BookNotes>> {
    if (books.isEmpty()) return flowOf(emptyList())
    val perBook = books.map { book ->
        store.observe(book.id)
            .catch { e ->
                AudoraLog.w(TAG, "Could not read bookmarks, book=${book.id.value}", e)
                emit(emptyList())
            }
            .map { BookNotes(book, it) }
    }
    return combine(perBook) { groups -> groups.filter { it.bookmarks.isNotEmpty() } }
}
