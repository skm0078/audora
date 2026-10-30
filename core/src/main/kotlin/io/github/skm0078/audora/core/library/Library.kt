// File: core/src/main/kotlin/io/github/skm0078/audora/core/library/Library.kt
// Purpose: The library's rules: add a picked PDF, recognize one already there, find a moved file again, remove.

package io.github.skm0078.audora.core.library

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A book is identified by its content ([BookFingerprinter]), not by its URI: the same PDF picked again from
 * another folder or app finds its existing entry (and, from P1-06, its reading position) instead of becoming a
 * second copy. Changes are serialized, so two quick picks of the same file cannot both insert it.
 *
 * Holds a persisted read grant per book and gives it back when the book leaves the library or moves to a new
 * URI: Android caps how many grants an app may keep (512 on Android 11+).
 */
class Library(
    private val store: BookStore,
    private val documents: DocumentAccess,
    fingerprinter: BookFingerprinter,
    private val clock: () -> Long,
    newId: () -> BookId = { BookId(UUID.randomUUID().toString()) },
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val changes = Mutex()
    private val pruned = AtomicBoolean(false)
    private val scanner = DocumentScanner(documents, fingerprinter)
    private val placement = BookPlacement(store, documents, clock, newId)

    fun books(): Flow<List<Book>> = store.observeAll()

    suspend fun book(id: BookId): Book? = withContext(io) { store.byId(id) }

    /** Adds the PDF the user picked, or returns the book it already is. */
    suspend fun add(documentUri: String): Outcome<AddResult, LibraryError> = changes.withLock {
        withContext(io) {
            when (val scanned = scan(documentUri)) {
                is Outcome.Failure -> scanned
                is Outcome.Success -> placement.add(documentUri, scanned.value)
            }
        }
    }

    /** Points a book whose file went missing at [documentUri], if that file is the same PDF. */
    suspend fun relocate(id: BookId, documentUri: String): Outcome<Book, LibraryError> = changes.withLock {
        withContext(io) {
            val book = store.byId(id) ?: return@withContext Outcome.Failure(LibraryError.NotInLibrary)
            when (val scanned = scan(documentUri)) {
                is Outcome.Failure -> scanned

                is Outcome.Success -> if (scanned.value.fingerprint == book.fingerprint) {
                    placement.moveTo(book, documentUri, scanned.value)
                } else {
                    placement.releaseIfUnused(documentUri)
                    AudoraLog.i(TAG, "Relocate refused, different file, book=${book.id.logLabel}")
                    Outcome.Failure(LibraryError.DifferentFile)
                }
            }
        }
    }

    /** Removes the entry only; the PDF itself stays where it is. */
    suspend fun remove(id: BookId): Outcome<Unit, LibraryError> = changes.withLock {
        withContext(io) {
            val book = store.byId(id) ?: return@withContext Outcome.Success(Unit)
            store.delete(id).toLibrary {
                placement.releaseIfUnused(book.documentUri)
                AudoraLog.i(TAG, "Removed book=${id.logLabel}")
            }
        }
    }

    /** The book was opened: it moves to the top of the list and remembers its page count. */
    suspend fun markOpened(id: BookId, pageCount: Int): Outcome<Unit, LibraryError> = changes.withLock {
        withContext(io) {
            val book = store.byId(id) ?: return@withContext Outcome.Failure(LibraryError.NotInLibrary)
            store.update(book.copy(pageCount = pageCount, lastOpenedAtMillis = clock())).toLibrary {}
        }
    }

    /**
     * [id] changed by [transform] under the library's lock, so two changes never overwrite each other; saved only when
     * it changed. The book's details go through it ([fill], [edit], [resetDetails]).
     */
    suspend fun change(id: BookId, transform: (Book) -> Book): Outcome<Book, LibraryError> = changes.withLock {
        withContext(io) {
            val book = store.byId(id) ?: return@withContext Outcome.Failure(LibraryError.NotInLibrary)
            val changed = transform(book)
            if (changed == book) return@withContext Outcome.Success(book)
            store.update(changed).toLibrary { changed }
        }
    }

    /**
     * Once per process (later calls, from a recreated activity, do nothing): deletes Audora's own copies of shared
     * PDFs that no book uses (left by a crash between copying and adding). Under the lock, so no add or remove changes
     * the books meanwhile; unreadable books skip it (logged).
     */
    suspend fun pruneDocuments(): Unit = changes.withLock {
        if (!pruned.compareAndSet(false, true)) return@withLock
        withContext(io) {
            val books = store.observeAll()
                .catch { e -> AudoraLog.w(TAG, "Books could not be read; unused copies are kept this time", e) }
                .firstOrNull() ?: return@withContext
            documents.pruneUnused(books.mapTo(HashSet()) { it.documentUri })
        }
    }

    /** One provider call per book; run when the library is shown, so a deleted file is flagged before a tap. */
    suspend fun availability(books: List<Book>): Map<BookId, BookAvailability> = withContext(io) {
        books.associate { book ->
            val availability = documents.availability(book.documentUri)
            if (availability != BookAvailability.AVAILABLE) {
                AudoraLog.i(TAG, "Book file unavailable: book=${book.id.logLabel} state=$availability")
            }
            book.id to availability
        }
    }

    /**
     * Takes the lasting grant, then reads what identifies the document. Gives the grant back on failure, and when the
     * add is cancelled mid-scan (the screen that started it went away), so no grant is left behind for a book that
     * never arrived.
     */
    private suspend fun scan(documentUri: String): Outcome<ScannedDocument, LibraryError> {
        val kept = documents.keepAccess(documentUri)
        if (kept is Outcome.Failure) return Outcome.Failure(kept.error.toLibraryError())
        val scanned = try {
            scanner.scan(documentUri)
        } catch (e: CancellationException) {
            withContext(NonCancellable) { placement.releaseIfUnused(documentUri) }
            throw e
        }
        if (scanned is Outcome.Failure) {
            AudoraLog.w(TAG, "Could not add a picked file: ${scanned.error}")
            placement.releaseIfUnused(documentUri)
        }
        return scanned
    }

    private companion object {
        const val TAG = "Audora.Library"
    }
}

sealed interface AddResult {
    val book: Book

    data class Added(override val book: Book) : AddResult

    /** The same file was picked again. */
    data class AlreadyInLibrary(override val book: Book) : AddResult

    /** The same PDF from a new place: the existing book now points there. */
    data class FoundAgain(override val book: Book) : AddResult
}

sealed interface LibraryError {
    /** The picking app gives only a one-time grant: the file must be copied to the phone first. */
    data object CannotKeepAccess : LibraryError

    /**
     * The app holds as many lasting file grants as it safely can (`DocumentError.GrantsFull`): the book must be added
     * through Audora's own copy instead.
     */
    data object GrantsFull : LibraryError

    data object Unreadable : LibraryError

    data object NotAPdf : LibraryError

    /** A file picked to find a missing book is a different PDF. */
    data object DifferentFile : LibraryError

    data object NotInLibrary : LibraryError

    data class Storage(val detail: String) : LibraryError
}
