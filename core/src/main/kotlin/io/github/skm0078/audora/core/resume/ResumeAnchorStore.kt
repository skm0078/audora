// File: core/src/main/kotlin/io/github/skm0078/audora/core/resume/ResumeAnchorStore.kt
// Purpose: Persistence contract for resume anchors, one per book (Room in :playback, a fake in tests).

package io.github.skm0078.audora.core.resume

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.StorageError
import io.github.skm0078.audora.core.result.Outcome
import kotlinx.coroutines.flow.Flow

/** Removing a book from the library removes its anchor with it (the database cascades). */
interface ResumeAnchorStore {
    suspend fun get(bookId: BookId): ResumeAnchor?

    /** Every book's anchor, again on each save: the library's progress and "Continue reading". */
    fun observeAll(): Flow<List<ResumeAnchor>>

    /** The most recently saved anchor of any book: what a headset's play button resumes after a restart. */
    suspend fun latest(): ResumeAnchor?

    /** Replaces the book's anchor. Fails when the book is no longer in the library. */
    suspend fun put(anchor: ResumeAnchor): Outcome<Unit, StorageError>

    /** Forgets the book's place, so it is "Not started" and opens on its first page ("Mark as not started"). */
    suspend fun clear(bookId: BookId): Outcome<Unit, StorageError>
}
