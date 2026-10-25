// File: core/src/test/kotlin/io/github/skm0078/audora/core/resume/FakeResumeAnchorStore.kt
// Purpose: In-memory anchor store and unit builders shared by the resume tests.

package io.github.skm0078.audora.core.resume

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.StorageError
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeResumeAnchorStore : ResumeAnchorStore {
    val anchors = mutableMapOf<BookId, ResumeAnchor>()
    val writes = mutableListOf<ResumeAnchor>()
    var failWrites = false

    override suspend fun get(bookId: BookId): ResumeAnchor? = anchors[bookId]

    override fun observeAll(): Flow<List<ResumeAnchor>> = flowOf(anchors.values.toList())

    override suspend fun latest(): ResumeAnchor? = anchors.values.maxByOrNull { it.savedAtMillis }

    override suspend fun put(anchor: ResumeAnchor): Outcome<Unit, StorageError> {
        if (failWrites) return Outcome.Failure(StorageError("disk full"))
        anchors[anchor.bookId] = anchor
        writes += anchor
        return Outcome.Success(Unit)
    }

    override suspend fun clear(bookId: BookId): Outcome<Unit, StorageError> {
        if (failWrites) return Outcome.Failure(StorageError("disk full"))
        anchors.remove(bookId)
        return Outcome.Success(Unit)
    }
}

val BOOK = BookId("book-1")

/** Cuts [sentences] into units laid end to end from [startOffset], one space apart, like the :pdf builder. */
fun unitsOf(page: Int, vararg sentences: String, startOffset: Int = 0): List<ReadingUnit> {
    var offset = startOffset
    return sentences.mapIndexed { index, text ->
        ReadingUnit(page, index, offset, offset + text.length, text, Lang.EN, 0, null)
            .also { offset += text.length + 1 }
    }
}
