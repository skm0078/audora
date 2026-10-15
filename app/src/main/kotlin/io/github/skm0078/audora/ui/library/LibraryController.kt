// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/LibraryController.kt
// Purpose: Library screen state from the Library's books and file checks; I/O stays off the main thread.

package io.github.skm0078.audora.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.skm0078.audora.core.library.AddResult
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookAvailability
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.Library
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/** Plain state holder (no Compose UI here), like the other controllers. Call [start] once. */
class LibraryController(private val library: Library, private val scope: CoroutineScope) {
    var listState: LibraryListState by mutableStateOf(LibraryListState.Loading)
        private set

    /** A pick is being added or located; the screen shows progress and blocks a second pick. */
    var busy: Boolean by mutableStateOf(false)
        private set

    var message: LibraryMessage? by mutableStateOf(null)
        private set

    var pendingRemoval: Book? by mutableStateOf(null)
        private set

    private var books: List<Book> = emptyList()

    /** Last check per book, with the URI it was made for: a book moved to a new URI is checked again. */
    private var checked: Map<BookId, Pair<String, BookAvailability>> = emptyMap()
    private var watch: Job? = null

    fun start() {
        if (watch != null) return
        watch = scope.launch {
            library.books()
                .catch { e ->
                    AudoraLog.e(TAG, "Library could not be loaded", e)
                    listState = LibraryListState.Failed
                }
                .collect { latest ->
                    books = latest
                    publish()
                    checkFiles(latest.filter { checked[it.id]?.first != it.documentUri })
                }
        }
    }

    /** The screen came back to the foreground: files may have been moved or deleted meanwhile. */
    fun refreshAvailability() = checkFiles(books)

    fun add(documentUri: String) = runPick {
        message = when (val added = library.add(documentUri)) {
            is Outcome.Failure -> LibraryMessage.Failed(added.error)

            is Outcome.Success -> when (val result = added.value) {
                is AddResult.Added -> LibraryMessage.Added(result.book.title)
                is AddResult.AlreadyInLibrary -> LibraryMessage.AlreadyInLibrary(result.book.title)
                is AddResult.FoundAgain -> LibraryMessage.FoundAgain(result.book.title)
            }
        }
    }

    fun locate(id: BookId, documentUri: String) = runPick {
        message = when (val moved = library.relocate(id, documentUri)) {
            is Outcome.Failure -> LibraryMessage.Failed(moved.error)
            is Outcome.Success -> LibraryMessage.FoundAgain(moved.value.title)
        }
    }

    /** Asks the user to confirm removing [book]; null closes the question. */
    fun proposeRemoval(book: Book?) {
        pendingRemoval = book
    }

    fun confirmRemoval() {
        val book = pendingRemoval ?: return
        pendingRemoval = null
        scope.launch {
            message = when (val removed = library.remove(book.id)) {
                is Outcome.Failure -> LibraryMessage.Failed(removed.error)
                is Outcome.Success -> LibraryMessage.Removed(book.title)
            }
        }
    }

    fun messageShown() {
        message = null
    }

    private fun runPick(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                block()
            } finally {
                busy = false
            }
        }
    }

    private fun checkFiles(targets: List<Book>) {
        if (targets.isEmpty()) return
        scope.launch {
            val results = library.availability(targets)
            val uris = targets.associate { it.id to it.documentUri }
            checked = checked + results.mapValues { (id, state) -> requireNotNull(uris[id]) to state }
            publish()
        }
    }

    private fun publish() {
        listState = LibraryListState.Loaded(
            books.map { book ->
                val check = checked[book.id]?.takeIf { it.first == book.documentUri }
                LibraryItem(book, check?.second)
            },
        )
    }

    private companion object {
        const val TAG = "Audora.LibraryUi"
    }
}
