// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/BookNotesHighlightTest.kt
// Purpose: The reader's highlights follow the sentences showing, and Remove clears every highlight a band shows.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.StorageError
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.notes.Bookmark
import io.github.skm0078.audora.core.notes.BookmarkStore
import io.github.skm0078.audora.core.notes.Highlight
import io.github.skm0078.audora.core.notes.HighlightStore
import io.github.skm0078.audora.core.notes.PronunciationRule
import io.github.skm0078.audora.core.notes.PronunciationStore
import io.github.skm0078.audora.core.result.Outcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookNotesHighlightTest {
    private class MemoryHighlights : HighlightStore {
        val rows = MutableStateFlow<List<Highlight>>(emptyList())
        private var nextId = 1L

        override fun observe(bookId: BookId): Flow<List<Highlight>> =
            rows.map { list -> list.filter { it.anchor.bookId == bookId } }

        override fun observeAll(): Flow<List<Highlight>> = rows

        override suspend fun add(highlight: Highlight): Outcome<Long, StorageError> {
            val id = nextId++
            rows.value = rows.value + highlight.copy(id = id)
            return Outcome.Success(id)
        }

        override suspend fun updateNote(id: Long, note: String): Outcome<Unit, StorageError> = Outcome.Success(Unit)

        override suspend fun delete(id: Long): Outcome<Unit, StorageError> {
            rows.value = rows.value.filterNot { it.id == id }
            return Outcome.Success(Unit)
        }
    }

    private object NoBookmarks : BookmarkStore {
        override fun observe(bookId: BookId): Flow<List<Bookmark>> = emptyFlow()

        override suspend fun add(bookmark: Bookmark): Outcome<Long, StorageError> = Outcome.Success(0)

        override suspend fun updateNote(id: Long, note: String): Outcome<Unit, StorageError> = Outcome.Success(Unit)

        override suspend fun delete(id: Long): Outcome<Unit, StorageError> = Outcome.Success(Unit)
    }

    private object NoRules : PronunciationStore {
        override fun observe(bookId: BookId?): Flow<List<PronunciationRule>> = emptyFlow()

        override suspend fun add(rule: PronunciationRule): Outcome<Long, StorageError> = Outcome.Success(0)

        override suspend fun delete(id: Long): Outcome<Unit, StorageError> = Outcome.Success(Unit)
    }

    private val highlights = MemoryHighlights()

    private fun TestScope.notes() =
        BookNotesModel(BOOK, NoteStores(NoBookmarks, NoRules, highlights), backgroundScope, PIPELINE, clock = { 0L })
            .also { it.start() }

    private fun unitsOf(vararg sentences: String): List<ReadingUnit> {
        var offset = 0
        return sentences.mapIndexed { index, text ->
            ReadingUnit(PAGE, index, offset, offset + text.length, text, Lang.EN, 0, null).also {
                offset += text.length + 1
            }
        }
    }

    @Test
    fun `highlights merged into one sentence share its band and Remove clears both`() =
        runTest(UnconfinedTestDispatcher()) {
            val notes = notes()
            val before = unitsOf(FIRST, DR, HELLO)
            notes.showing(before)
            notes.toggleHighlight(before[1])
            notes.toggleHighlight(before[2])

            val merged = unitsOf(FIRST, "$DR $HELLO")
            notes.showing(merged)
            assertEquals(2, notes.state.value.highlightsOn(merged[1]).size)

            notes.toggleHighlight(merged[1])
            assertEquals(emptyList<Highlight>(), highlights.rows.value)
            assertEquals(emptyList<Highlight>(), notes.state.value.highlightsOn(merged[1]))
        }

    @Test
    fun `a highlight shows only on the page showing`() = runTest(UnconfinedTestDispatcher()) {
        val notes = notes()
        val page = unitsOf(FIRST, DR)
        notes.showing(page)
        notes.toggleHighlight(page[1])

        val next = page.map { it.copy(page = PAGE + 1) }
        notes.showing(next)
        assertEquals(emptyList<Highlight>(), notes.state.value.highlightsOn(next[1]))
        assertEquals(emptyList<Highlight>(), notes.state.value.highlightsOn(page[1]))
    }

    private companion object {
        val BOOK = BookId("book")
        const val PAGE = 2
        const val PIPELINE = 9
        const val FIRST = "It was the best of times, it was the worst of times."
        const val DR = "I met Dr."
        const val HELLO = "Hello."
    }
}
