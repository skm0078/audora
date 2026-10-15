// File: core/src/main/kotlin/io/github/skm0078/audora/core/resume/ResumeAnchorStore.kt
// Purpose: Persistence contract for resume anchors, one per book (Room in :playback, a fake in tests).

package io.github.skm0078.audora.core.resume

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.StorageError
import io.github.skm0078.audora.core.result.Outcome

/** Removing a book from the library removes its anchor with it (the database cascades). */
interface ResumeAnchorStore {
    suspend fun get(bookId: BookId): ResumeAnchor?

    /** Replaces the book's anchor. Fails when the book is no longer in the library. */
    suspend fun put(anchor: ResumeAnchor): Outcome<Unit, StorageError>
}
