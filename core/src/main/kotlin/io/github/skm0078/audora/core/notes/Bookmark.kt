// File: core/src/main/kotlin/io/github/skm0078/audora/core/notes/Bookmark.kt
// Purpose: A bookmark with an optional note (plan item 29), anchored like a resume position so it survives updates.

package io.github.skm0078.audora.core.notes

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.StorageError
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.resume.ResumeAnchor
import kotlinx.coroutines.flow.Flow

/**
 * The sentence is held as a [ResumeAnchor] (offsets and hashes, never the PDF's text), so a later pipeline finds it
 * again the way resume does (ReAnchoring). [note] is the listener's own words; it is stored, never logged.
 *
 * @property id 0 until stored.
 */
data class Bookmark(val id: Long, val anchor: ResumeAnchor, val note: String = "") {
    val bookId: BookId get() = anchor.bookId

    /** Log form: identifiers only, never the note. */
    val logLabel: String get() = "bookmark=$id ${anchor.logLabel} note=${note.isNotBlank()}"
}

/** Removing a book removes its bookmarks with it (the database cascades). */
interface BookmarkStore {
    /** The book's bookmarks in reading order (page, then offset), again after every change. */
    fun observe(bookId: BookId): Flow<List<Bookmark>>

    /** Stores [bookmark] and returns its id. */
    suspend fun add(bookmark: Bookmark): Outcome<Long, StorageError>

    suspend fun updateNote(id: Long, note: String): Outcome<Unit, StorageError>

    suspend fun delete(id: Long): Outcome<Unit, StorageError>
}
