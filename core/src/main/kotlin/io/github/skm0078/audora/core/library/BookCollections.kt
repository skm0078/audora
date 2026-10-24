// File: core/src/main/kotlin/io/github/skm0078/audora/core/library/BookCollections.kt
// Purpose: The listener's own collections of books ("For school", "Bedtime", ADR-069): a book can be in several, and
//          removing a book or a collection removes only the link.

package io.github.skm0078.audora.core.library

import io.github.skm0078.audora.core.result.Outcome
import kotlinx.coroutines.flow.Flow

data class BookCollection(val id: String, val name: String, val createdAtMillis: Long)

/** Writes report failures as [StorageError] instead of throwing, like [BookStore]. */
interface CollectionStore {
    /** Every collection, by name. */
    fun observe(): Flow<List<BookCollection>>

    /** Each book's collections; a book in none is absent. */
    fun observeMembers(): Flow<Map<BookId, Set<String>>>

    suspend fun create(collection: BookCollection): Outcome<Unit, StorageError>

    suspend fun rename(id: String, name: String): Outcome<Unit, StorageError>

    /** The collection goes; its books stay in the library. */
    suspend fun delete(id: String): Outcome<Unit, StorageError>

    /** Puts [book] in collection [id], or takes it out. */
    suspend fun setMember(book: BookId, id: String, member: Boolean, atMillis: Long): Outcome<Unit, StorageError>
}

object CollectionNames {
    /** Longer than a chip can show; the name is cut here. */
    const val MAX_LENGTH = 60

    /** [raw] trimmed and cut, or null when nothing is left. */
    fun clean(raw: String): String? = raw.trim().replace(Regex("\\s+"), " ").take(MAX_LENGTH).ifEmpty { null }
}
