// File: core/src/main/kotlin/io/github/skm0078/audora/core/notes/PronunciationRule.kt
// Purpose: "Say this word like that" (plan item 1), for one book or every book, kept in the app's database.

package io.github.skm0078.audora.core.notes

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.StorageError
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Wherever [written] appears in [lang] text the voice is given [spoken] instead; the page shows the PDF unchanged.
 * Both are the listener's own entries: stored, never logged.
 *
 * @property id 0 until stored.
 * @property bookId the book it applies to; null for every book.
 * @property wholeWord matches only the whole word (so "US" does not change "USB"); off, any occurrence.
 */
data class PronunciationRule(
    val id: Long,
    val bookId: BookId?,
    val lang: Lang,
    val written: String,
    val spoken: String,
    val wholeWord: Boolean = true,
) {
    init {
        require(written.isNotBlank()) { "written must not be blank" }
    }

    /** Log form: identifiers only. */
    val logLabel: String get() = "rule=$id lang=$lang book=${bookId?.logLabel ?: "all"} whole=$wholeWord"
}

interface PronunciationStore {
    /** The rules for [bookId]: its own and every-book ones, again after every change. */
    fun observe(bookId: BookId?): Flow<List<PronunciationRule>>

    suspend fun add(rule: PronunciationRule): Outcome<Long, StorageError>

    suspend fun delete(id: Long): Outcome<Unit, StorageError>
}
