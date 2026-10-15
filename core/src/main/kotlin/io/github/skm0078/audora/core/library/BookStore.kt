// File: core/src/main/kotlin/io/github/skm0078/audora/core/library/BookStore.kt
// Purpose: Persistence contract for the library (Room in :playback, an in-memory fake in tests).

package io.github.skm0078.audora.core.library

import io.github.skm0078.audora.core.result.Outcome
import kotlinx.coroutines.flow.Flow

/** Writes report failures as [StorageError] (disk full, database corrupt) instead of throwing. */
interface BookStore {
    /** Every book, most recently opened or added first. Emits again after every change. */
    fun observeAll(): Flow<List<Book>>

    suspend fun byId(id: BookId): Book?

    suspend fun byFingerprint(fingerprint: String): Book?

    suspend fun byDocumentUri(documentUri: String): Book?

    suspend fun insert(book: Book): Outcome<Unit, StorageError>

    suspend fun update(book: Book): Outcome<Unit, StorageError>

    suspend fun delete(id: BookId): Outcome<Unit, StorageError>
}

data class StorageError(val detail: String)
