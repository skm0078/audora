// File: core/src/main/kotlin/io/github/skm0078/audora/core/library/BookPlacement.kt
// Purpose: Writes a scanned document into the store as a new book, a moved book or a book changed in place.

package io.github.skm0078.audora.core.library

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.flatMap
import io.github.skm0078.audora.core.result.map

/** Not thread-safe on its own: [Library] calls it under its lock. Gives grants back once no book uses them. */
internal class BookPlacement(
    private val store: BookStore,
    private val documents: DocumentAccess,
    private val clock: () -> Long,
    private val newId: () -> BookId,
) {
    suspend fun add(documentUri: String, scanned: ScannedDocument): Outcome<AddResult, LibraryError> {
        val sameContent = store.byFingerprint(scanned.fingerprint)
        val sameUri = store.byDocumentUri(documentUri)
        return when {
            sameContent != null && sameContent.documentUri == documentUri ->
                Outcome.Success(AddResult.AlreadyInLibrary(sameContent))

            sameContent != null -> moveTo(sameContent, documentUri, scanned).map { AddResult.FoundAgain(it) }

            // The file at this URI was changed in place: same book, new content.
            sameUri != null -> {
                val changed = sameUri.copy(fingerprint = scanned.fingerprint, sizeBytes = scanned.sizeBytes)
                AudoraLog.i(TAG, "Book content changed in place, book=${sameUri.id.logLabel}")
                store.update(changed).toLibrary { AddResult.AlreadyInLibrary(changed) }
            }

            else -> insertNew(documentUri, scanned)
        }
    }

    suspend fun moveTo(book: Book, documentUri: String, scanned: ScannedDocument): Outcome<Book, LibraryError> {
        if (book.documentUri == documentUri) return Outcome.Success(book)
        val moved = book.copy(documentUri = documentUri, sizeBytes = scanned.sizeBytes)
        val written = dropStale(store.byDocumentUri(documentUri), keep = book.id).flatMap { store.update(moved) }
        // The new URI's grant (or Audora's own copy) was taken for this move; give it back if the move failed.
        if (written is Outcome.Failure) releaseIfUnused(documentUri)
        return written.toLibrary {
            releaseIfUnused(book.documentUri)
            AudoraLog.i(TAG, "Book found at a new location, book=${book.id.logLabel}")
            moved
        }
    }

    suspend fun releaseIfUnused(documentUri: String) {
        if (store.byDocumentUri(documentUri) == null) documents.releaseAccess(documentUri)
    }

    private suspend fun insertNew(documentUri: String, scanned: ScannedDocument): Outcome<AddResult, LibraryError> {
        val book = Book(
            id = newId(),
            documentUri = documentUri,
            title = BookTitles.fromDisplayName(scanned.displayName),
            sizeBytes = scanned.sizeBytes,
            fingerprint = scanned.fingerprint,
            pageCount = null,
            addedAtMillis = clock(),
            lastOpenedAtMillis = null,
        )
        val inserted = store.insert(book)
        if (inserted is Outcome.Failure) releaseIfUnused(documentUri)
        return inserted.toLibrary {
            AudoraLog.i(TAG, "Added book=${book.id.logLabel} bytes=${book.sizeBytes}")
            AddResult.Added(book)
        }
    }

    /**
     * Another book's entry pointing at a URI whose content is now this book's: its own file was overwritten,
     * so the entry can never open its book again. URIs are unique in the store, so it must go first.
     */
    private suspend fun dropStale(stale: Book?, keep: BookId): Outcome<Unit, StorageError> {
        if (stale == null || stale.id == keep) return Outcome.Success(Unit)
        AudoraLog.w(TAG, "Dropping book=${stale.id.logLabel}: its file now holds book=${keep.logLabel}")
        return store.delete(stale.id)
    }

    private companion object {
        const val TAG = "Audora.Library"
    }
}

/** A storage failure becomes [LibraryError.Storage] (logged here, once); success runs [onSuccess]. */
internal inline fun <T> Outcome<Unit, StorageError>.toLibrary(onSuccess: () -> T): Outcome<T, LibraryError> =
    when (this) {
        is Outcome.Success -> Outcome.Success(onSuccess())

        is Outcome.Failure -> {
            AudoraLog.e("Audora.Library", "Library storage failed: ${error.detail}")
            Outcome.Failure(LibraryError.Storage(error.detail))
        }
    }
