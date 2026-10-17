// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/LibraryState.kt
// Purpose: UI state of the library screen: the books, whether each file can be read, and one-off messages.

package io.github.skm0078.audora.ui.library

import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookAvailability
import io.github.skm0078.audora.core.library.BookProgress
import io.github.skm0078.audora.core.library.LibraryError
import io.github.skm0078.audora.core.library.LibraryQuery

/**
 * [availability] is null until the file has been checked; the row then shows no warning. [progress] is null for a
 * book never read (or never opened, so its page count is unknown).
 */
data class LibraryItem(val book: Book, val availability: BookAvailability?, val progress: BookProgress? = null) {
    val needsLocating: Boolean
        get() = availability == BookAvailability.MISSING || availability == BookAvailability.ACCESS_LOST
}

sealed interface LibraryListState {
    data object Loading : LibraryListState

    /**
     * [items]: the books [query] keeps, in its order. [continueReading]: the book read most recently, offered on top
     * while nothing is searched or filtered; null when none was read yet. [all]: every book in the library, so "no
     * book matches" and "the library is empty" can be told apart, and the mini player finds a filtered-out book.
     */
    data class Loaded(
        val items: List<LibraryItem>,
        val continueReading: LibraryItem? = null,
        val all: List<LibraryItem> = items,
        val query: LibraryQuery = LibraryQuery(),
    ) : LibraryListState

    /** The database could not be read; the log has the cause. */
    data object Failed : LibraryListState
}

/** Shown once in a snackbar, then cleared. Titles here are for the screen only, never the log. */
sealed interface LibraryMessage {
    data class Added(val title: String) : LibraryMessage

    data class AlreadyInLibrary(val title: String) : LibraryMessage

    data class FoundAgain(val title: String) : LibraryMessage

    data class Removed(val title: String) : LibraryMessage

    data class Failed(val error: LibraryError) : LibraryMessage
}
