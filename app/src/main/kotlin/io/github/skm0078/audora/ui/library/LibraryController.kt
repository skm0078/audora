// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/LibraryController.kt
// Purpose: Library screen state from the books, their progress, collections and file checks; I/O stays off the main
//          thread, and search keys are folded once per change of the library (ADR-069). A removed book can be
//          brought back for a few seconds (CF-3).

package io.github.skm0078.audora.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.skm0078.audora.core.library.AddResult
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookAvailability
import io.github.skm0078.audora.core.library.BookCollection
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.BookProgress
import io.github.skm0078.audora.core.library.Library
import io.github.skm0078.audora.core.library.LibraryError
import io.github.skm0078.audora.core.library.LibraryFacets
import io.github.skm0078.audora.core.library.LibraryIndex
import io.github.skm0078.audora.core.library.LibraryQuery
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.resume.ResumeAnchor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Plain state holder (no Compose UI here), like the other controllers. Call [start] once.
 *
 * @param anchors every book's resume anchor, for its progress and "Continue reading".
 * @param importShared copies a PDF another app only lends for a moment into a URI Audora can keep; null on failure.
 * @param collections the listener's collections, and [members] each book's: both read whole, once per change.
 * @param comics takes a file that is not a book but becomes one, a comic archive or a scanned DjVu made into a photo
 *   book that opens when made (ADR 2026-10-26-more-ebook-formats, ADR 2026-10-27-djvu-books), and says so to the
 *   listener; false for any other file.
 */
// Add, locate, share, remove and their messages, plus the list they publish.
// Every parameter after the scope is an optional hook with a no-op default, so callers name only what they use.
@Suppress("TooManyFunctions", "LongParameterList")
class LibraryController(
    private val library: Library,
    private val scope: CoroutineScope,
    private val anchors: Flow<List<ResumeAnchor>> = flowOf(emptyList()),
    private val importShared: suspend (String) -> String? = { null },
    private val collections: Flow<List<BookCollection>> = flowOf(emptyList()),
    private val members: Flow<Map<BookId, Set<String>>> = flowOf(emptyMap()),
    private val comics: suspend (String) -> Boolean = { false },
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
    private var index = LibraryIndex.EMPTY
    private var facets = LibraryFacets.EMPTY
    private var own = OwnCollections()
    private var search: Job? = null

    /**
     * Removals waiting out their undo window (CF-3), each with the job that removes the book when it ends. [removing]
     * holds those whose removal runs now, too late to undo. Both are hidden from the list; nothing is deleted until the
     * window ends, so Undo only has to show the book again.
     */
    private var leaving: Map<BookId, Job> = emptyMap()
    private var removing: Set<BookId> = emptySet()

    /** Last check per book, with the URI it was made for: a book moved to a new URI is checked again. */
    private var checked: Map<BookId, Pair<String, BookAvailability>> = emptyMap()
    private var watch: Job? = null

    fun start() {
        if (watch != null) return
        // Progress is a nicety: if the anchors cannot be read the library still shows, without it. A save every
        // sentence or two while a book plays changes nothing the library shows until the page does, so those are
        // skipped instead of filtering and sorting every book on the main thread every 2 s.
        val saved = anchors.catch { e ->
            AudoraLog.w(TAG, "Reading positions could not be loaded; the library shows no progress", e)
            emit(emptyList())
        }.distinctUntilChanged { old, new -> progressShown(old) == progressShown(new) }
        scope.launch { library.pruneDocuments() }
        watch = scope.launch {
            combine(library.books(), saved, ownCollections()) { latest, positions, own ->
                Triple(latest, positions, own)
            }
                .catch { e ->
                    AudoraLog.e(TAG, "Library could not be loaded", e)
                    listState = LibraryListState.Failed
                }
                .collect { (latest, positions, own) ->
                    val fresh = latest.filter { checked[it.id]?.first != it.documentUri }
                    if (latest != books) {
                        index = LibraryIndex.of(latest, index)
                        facets = LibraryFacets.of(latest, index)
                    }
                    books = latest
                    removing = removing.filterTo(HashSet()) { id -> latest.any { it.id == id } }
                    progress = positions.associateBy { it.bookId }
                    this@LibraryController.own = own
                    publish()
                    checkFiles(fresh)
                }
        }
    }

    /** Collections are a nicety too: if they cannot be read the library shows every book, ungrouped. */
    private fun ownCollections(): Flow<OwnCollections> = combine(collections, members, ::OwnCollections).catch { e ->
        AudoraLog.w(TAG, "Collections could not be loaded; the library shows none", e)
        emit(OwnCollections())
    }

    /**
     * Search, sort and filter (plan item 22); the search text is the listener's own, so it is never logged. A change
     * of text shows in the field at once and narrows the list once typing pauses, so a fast typist on a slow phone
     * does not filter thousands of books on every letter.
     */
    fun setQuery(next: LibraryQuery) {
        if (next == query) return
        val typed = next.copy(text = query.text) == query
        query = next
        search?.cancel()
        val shown = listState
        if (typed && shown is LibraryListState.Loaded) {
            listState = shown.copy(query = next)
            search = scope.launch {
                delay(SEARCH_PAUSE_MS)
                publish()
            }
        } else {
            publish()
        }
    }

    /** The screen came back to the foreground: files may have been moved or deleted meanwhile. */
    fun refreshAvailability() = checkFiles(books)

    fun add(documentUri: String) = runPick { addNow(documentUri) }

    /**
     * A PDF opened or shared from another app ("Open with Audora"): added like a picked one, then [open]ed. When the
     * other app's grant cannot be kept, the PDF is added through a copy made by `importShared`. [done] runs once the
     * add has finished, never when it was cancelled (the screen went away), so the caller can try again.
     */
    fun addShared(documentUri: String, open: (BookId) -> Unit, done: () -> Unit = {}) = runPick {
        if (comics(documentUri)) {
            done()
            return@runPick
        }
        val kept = library.add(documentUri)
        val added = if (kept.isCannotKeepAccess()) {
            importShared(documentUri)?.let { copy -> library.add(copy) } ?: kept
        } else {
            kept
        }
        report(added, picked = false)
        done()
        (added as? Outcome.Success)?.let { open(it.value.book.id) }
    }

    private suspend fun addNow(documentUri: String) {
        if (!comics(documentUri)) report(library.add(documentUri))
    }

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

    /**
     * Hides the book at once and removes it after [undoMs], unless [undoRemoval] brings it back first (CF-3). The
     * snackbar offering Undo shows for [undoMs]; the removal waits [REMOVAL_UNDO_GRACE_MS] longer, so an Undo tapped
     * in the snackbar's last moment still finds the book. If the screen's scope ends first (the activity is
     * finished), the removal runs then: the listener asked for it. A process killed inside the window keeps the book.
     */
    fun confirmRemoval(undoMs: Long = REMOVAL_UNDO_MS) {
        val book = pendingRemoval ?: return
        pendingRemoval = null
        if (book.id in leaving || book.id in removing) return
        leaving = leaving + (book.id to scope.launch { removeAfter(book, undoMs + REMOVAL_UNDO_GRACE_MS) })
        message = LibraryMessage.Removed(book.title, undo = book.id, undoMs = undoMs)
        publish()
    }

    /** Undo on the snackbar: the book shows again as it was, place, notes and collections untouched. */
    fun undoRemoval(id: BookId) {
        val job = leaving[id] ?: return
        leaving = leaving - id
        job.cancel()
        AudoraLog.i(TAG, "Removal undone, book=${id.logLabel}")
        publish()
    }

    private suspend fun removeAfter(book: Book, windowMs: Long) {
        try {
            delay(windowMs)
        } finally {
            // Undo takes the book out of [leaving] before it cancels; any other end of the wait removes it.
            if (book.id in leaving) withContext(NonCancellable) { remove(book) }
        }
    }

    private suspend fun remove(book: Book) {
        leaving = leaving - book.id
        removing = removing + book.id
        val removed = library.remove(book.id)
        if (removed is Outcome.Failure) {
            // The book is still in the library: show it again and say why.
            removing = removing - book.id
            message = LibraryMessage.Failed(removed.error)
            publish()
        }
        // On success the book stays hidden until the library's next list no longer has it, so it never flickers back.
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
        val hidden = leaving.keys + removing
        val items = books.filterNot { it.id in hidden }.map { book ->
            val check = checked[book.id]?.takeIf { it.first == book.documentUri }
            LibraryItem(book, check?.second, BookProgress.of(book, progress[book.id]))
        }
        val latest = items.filterNot { it.needsLocating }
            .mapNotNull { item -> item.progress?.let { item to it.savedAtMillis } }
            .maxByOrNull { it.second }
            ?.first
        query = query.copy(narrow = query.narrow.keptBy(facets, own.collections.mapTo(HashSet()) { it.id }))
        listState = LibraryListState.Loaded(
            items = query.apply(items, { it.book }, { it.progress }, index, own.members),
            latest = latest,
            all = items,
            query = query,
            collections = own.collections,
            members = own.members,
            facets = facets,
            leaving = hidden,
        )
    }

    private data class OwnCollections(
        val collections: List<BookCollection> = emptyList(),
        val members: Map<BookId, Set<String>> = emptyMap(),
    )

    private companion object {
        const val TAG = "Audora.LibraryUi"

        /** Long enough to span a burst of keys, short enough to feel like the list follows the typing. */
        const val SEARCH_PAUSE_MS = 200L
    }
}

/**
 * What the library shows of the resume anchors: each book's page (its progress) and which book was read last
 * ("Continue reading"). Two lists with the same of both look the same on screen.
 */
internal fun progressShown(anchors: List<ResumeAnchor>): Pair<Map<BookId, Int>, BookId?> =
    anchors.associate { it.bookId to it.page } to anchors.maxByOrNull { it.savedAtMillis }?.bookId
