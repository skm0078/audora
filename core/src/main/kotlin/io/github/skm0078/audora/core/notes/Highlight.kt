// File: core/src/main/kotlin/io/github/skm0078/audora/core/notes/Highlight.kt
// Purpose: A highlighted passage with its quote and an optional note (plan item 30), anchored like a bookmark.

package io.github.skm0078.audora.core.notes

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.StorageError
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.resume.ResumeAnchor
import kotlinx.coroutines.flow.Flow

/** The colours a highlight can have; stored by name. */
enum class HighlightColor { YELLOW, GREEN, BLUE, PINK }

/**
 * From the sentence at [anchor] to [endChar] (exclusive) on [endPage]. [quote] is the highlighted text, kept so the
 * quote can be exported and shown even after the PDF moved; like [note] it is the listener's own material: stored and
 * exported, never logged.
 *
 * @property id 0 until stored.
 */
data class Highlight(
    val id: Long,
    val anchor: ResumeAnchor,
    val endPage: Int,
    val endChar: Int,
    val quote: String,
    val note: String = "",
    val color: HighlightColor = HighlightColor.YELLOW,
) {
    init {
        require(endPage >= anchor.page) { "endPage ($endPage) must be >= the start page (${anchor.page})" }
        require(endPage > anchor.page || endChar >= anchor.charStart) { "the highlight must not end before it starts" }
    }

    val bookId: BookId get() = anchor.bookId

    /** Log form: identifiers only, never the quote or the note. */
    val logLabel: String get() = "highlight=$id ${anchor.logLabel} to p$endPage note=${note.isNotBlank()}"
}

/** Removing a book removes its highlights with it (the database cascades). */
interface HighlightStore {
    /** The book's highlights in reading order, again after every change. */
    fun observe(bookId: BookId): Flow<List<Highlight>>

    /** Every book's highlights, by book, then reading order. */
    fun observeAll(): Flow<List<Highlight>>

    suspend fun add(highlight: Highlight): Outcome<Long, StorageError>

    suspend fun updateNote(id: Long, note: String): Outcome<Unit, StorageError>

    suspend fun delete(id: Long): Outcome<Unit, StorageError>
}
