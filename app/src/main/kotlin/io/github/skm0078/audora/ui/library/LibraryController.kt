// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/LibraryController.kt
// Purpose: Library screen state from the books, their progress and file checks; I/O stays off the main thread.

package io.github.skm0078.audora.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.skm0078.audora.core.library.AddResult
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookAvailability
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.BookProgress
import io.github.skm0078.audora.core.library.Library
import io.github.skm0078.audora.core.library.LibraryError
import io.github.skm0078.audora.core.library.LibraryQuery
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.resume.ResumeAnchor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Plain state holder (no Compose UI here), like the other controllers. Call [start] once.
 *
 * @param anchors every book's resume anchor, for its progress and "Continue reading".
 * @param importShared copies a PDF another app only lends for a moment into a URI Audora can keep; null on failure.
 */
// Add, locate, share, remove and their messages, plus the list they publish.
@Suppress("TooManyFunctions")
class LibraryController(
    private val library: Library,
    private val scope: CoroutineScope,
    private val anchors: Flow<List<ResumeAnchor>> = flowOf(emptyList()),
    private val importShared: suspend (String) -> String? = { null },
) {
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
    private var query = LibraryQuery()
    private var progress: Map<BookId, ResumeAnchor> = emptyMap()

    /** Last check per book, with the URI it was made for: a book moved to a new URI is checked again. */
    private var checked: Map<BookId, Pair<String, BookAvailability>> = emptyMap()
    private var watch: Job? = null

    fun start() {
        if (watch != null) return
        // Progress is a nicety: if the anchors cannot be read the library still shows, without it.
        val saved = anchors.catch { e ->
            AudoraLog.w(TAG, "Reading positions could not be loaded; the library shows no progress", e)
            emit(emptyList())
        }
        watch = scope.launch {
            combine(library.books(), saved) { latest, positions -> latest to positions }
                .catch { e ->
                    AudoraLog.e(TAG, "Library could not be loaded", e)
                    listState = LibraryListState.Failed
                }
                .collect { (latest, positions) ->
                    val fresh = latest.filter { checked[it.id]?.first != it.documentUri }
                    books = latest
                    progress = positions.associateBy { it.bookId }
                    publish()
                    checkFiles(fresh)
                }
        }
    }

    /** Search, sort and filter (plan item 22); the search text is the listener's own, so it is never logged. */
    fun setQuery(next: LibraryQuery) {
        if (next == query) return
        query = next
        publish()
    }

    /** The screen came back to the foreground: files may have been moved or deleted meanwhile. */
    fun refreshAvailability() = checkFiles(books)

    fun add(documentUri: String) = runPick { addNow(documentUri) }

    /**
     * A PDF opened or shared from another app ("Open with Audora"): added like a picked one, then [open]ed. When the
     * other app's grant cannot be kept, the PDF is added through a copy made by `importShared`.
     */
    fun addShared(documentUri: String, open: (BookId) -> Unit) = runPick {
        val kept = library.add(documentUri)
        val added = if (kept.isCannotKeepAccess()) {
            importShared(documentUri)?.let { copy -> library.add(copy) } ?: kept
        } else {
            kept
        }
        report(added, picked = false)
        (added as? Outcome.Success)?.let { open(it.value.book.id) }
    }

    private suspend fun addNow(documentUri: String) = report(library.add(documentUri))

    /**
     * [picked]: the user chose the file in the picker, so a duplicate offers to open the book (plan item 27). A shared
     * file opens by itself, so its duplicate only gets the snackbar.
     */
    private fun report(added: Outcome<AddResult, LibraryError>, picked: Boolean = true) {
        val id = { book: Book -> book.id.takeIf { picked } }
        message = when (added) {
            is Outcome.Failure -> LibraryMessage.Failed(added.error)

            is Outcome.Success -> when (val result = added.value) {
                is AddResult.Added -> LibraryMessage.Added(result.book.title)
                is AddResult.AlreadyInLibrary -> LibraryMessage.AlreadyInLibrary(result.book.title, id(result.book))
                is AddResult.FoundAgain -> LibraryMessage.FoundAgain(result.book.title, id(result.book))
            }
        }
    }

    private fun Outcome<AddResult, LibraryError>.isCannotKeepAccess(): Boolean =
        this is Outcome.Failure && error == LibraryError.CannotKeepAccess

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
        val items = books.map { book ->
            val check = checked[book.id]?.takeIf { it.first == book.documentUri }
            LibraryItem(book, check?.second, BookProgress.of(book, progress[book.id]))
        }
        val latest = items.filterNot { it.needsLocating }
            .mapNotNull { item -> item.progress?.let { item to it.savedAtMillis } }
            .maxByOrNull { it.second }
            ?.first
        listState = LibraryListState.Loaded(
            items = query.apply(items, book = { it.book }, progress = { it.progress }),
            continueReading = latest.takeIf { query.isDefault },
            all = items,
            query = query,
        )
    }

    private companion object {
        const val TAG = "Audora.LibraryUi"
    }
}
