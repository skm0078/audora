// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/PlayerBookmarkNotesTest.kt
// Purpose: The player's one-tap bookmark (CF-2): saved at once with no note, then Add note, Undo or nothing; a
//          sentence already bookmarked opens its note instead of getting a twin.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.StorageError
import io.github.skm0078.audora.core.notes.Bookmark
import io.github.skm0078.audora.core.notes.BookmarkStore
import io.github.skm0078.audora.core.notes.PronunciationRule
import io.github.skm0078.audora.core.notes.PronunciationStore
import io.github.skm0078.audora.core.result.Outcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerBookmarkNotesTest {
    private class MemoryBookmarks : BookmarkStore {
        val rows = MutableStateFlow<List<Bookmark>>(emptyList())
        var gate: CompletableDeferred<Unit>? = null
        var failing = false
        private var nextId = 1L

        override fun observe(bookId: BookId): Flow<List<Bookmark>> =
            rows.map { list -> list.filter { it.bookId == bookId } }

        override suspend fun add(bookmark: Bookmark): Outcome<Long, StorageError> {
            gate?.await()
            if (failing) return Outcome.Failure(StorageError("disk full"))
            val id = nextId++
            rows.value = rows.value + bookmark.copy(id = id)
            return Outcome.Success(id)
        }

        override suspend fun updateNote(id: Long, note: String): Outcome<Unit, StorageError> {
            rows.value = rows.value.map { if (it.id == id) it.copy(note = note) else it }
            return Outcome.Success(Unit)
        }

        override suspend fun delete(id: Long): Outcome<Unit, StorageError> {
            rows.value = rows.value.filterNot { it.id == id }
            return Outcome.Success(Unit)
        }
    }

    private object NoRules : PronunciationStore {
        override fun observe(bookId: BookId?): Flow<List<PronunciationRule>> = emptyFlow()
        override suspend fun add(rule: PronunciationRule): Outcome<Long, StorageError> = Outcome.Success(0)
        override suspend fun delete(id: Long): Outcome<Unit, StorageError> = Outcome.Success(Unit)
    }

    private val bookmarks = MemoryBookmarks()

    private fun TestScope.notes() =
        BookNotesModel(BOOK, NoteStores(bookmarks, NoRules), backgroundScope, PIPELINE, clock = { NOW })
            .also { it.start() }

    @Test
    fun `one tap saves the sentence with no note and offers Add note and Undo`() = runTest(UnconfinedTestDispatcher()) {
        val notes = notes()
        val unit = unitsOf(page = 4)[1]
        notes.quickBookmark(unit)
        val saved = notes.state.value.bookmarks.single()
        assertEquals(4, saved.anchor.page)
        assertEquals(unit.charStart, saved.anchor.charStart)
        assertEquals("", saved.note)
        assertEquals(saved.id, notes.state.value.quickBookmark?.id)
        assertNull("no dialog interrupts listening", notes.state.value.editing)
        assertTrue(notes.state.value.isBookmarked(unit))
        assertFalse(notes.state.value.isBookmarked(unitsOf(page = 4)[0]))
    }

    @Test
    fun `Add note opens the note of the bookmark just saved`() = runTest(UnconfinedTestDispatcher()) {
        val notes = notes()
        notes.quickBookmark(unitsOf(page = 1)[0])
        notes.noteQuickBookmark()
        assertNull(notes.state.value.quickBookmark)
        assertEquals(notes.state.value.bookmarks.single().id, notes.state.value.editing?.id)
        notes.saveNote("Quote this")
        assertEquals("Quote this", notes.state.value.bookmarks.single().note)
    }

    @Test
    fun `Undo deletes it, and the notice timing out keeps it`() = runTest(UnconfinedTestDispatcher()) {
        val notes = notes()
        notes.quickBookmark(unitsOf(page = 1)[0])
        notes.undoQuickBookmark()
        assertTrue(notes.state.value.bookmarks.isEmpty())
        assertNull(notes.state.value.quickBookmark)

        notes.quickBookmark(unitsOf(page = 1)[1])
        notes.dismissQuickBookmark()
        assertEquals(1, notes.state.value.bookmarks.size)
        assertNull(notes.state.value.quickBookmark)
    }

    @Test
    fun `a sentence with a bookmark opens its note instead of a twin`() = runTest(UnconfinedTestDispatcher()) {
        val notes = notes()
        val unit = unitsOf(page = 2)[2]
        notes.quickBookmark(unit)
        notes.dismissQuickBookmark()
        notes.quickBookmark(unit)
        assertEquals(1, notes.state.value.bookmarks.size)
        assertEquals(notes.state.value.bookmarks.single().id, notes.state.value.editing?.id)
        assertNull(notes.state.value.quickBookmark)
    }

    @Test
    fun `a second tap while the first is saving saves nothing more`() = runTest(UnconfinedTestDispatcher()) {
        val notes = notes()
        bookmarks.gate = CompletableDeferred()
        val unit = unitsOf(page = 2)[0]
        notes.quickBookmark(unit)
        notes.quickBookmark(unit)
        bookmarks.gate?.complete(Unit)
        assertEquals(1, notes.state.value.bookmarks.size)
    }

    @Test
    fun `a failed save is said and offers nothing to undo`() = runTest(UnconfinedTestDispatcher()) {
        bookmarks.failing = true
        val notes = notes()
        notes.quickBookmark(unitsOf(page = 0)[0])
        assertTrue(notes.state.value.failed)
        assertNull(notes.state.value.quickBookmark)
        bookmarks.failing = false
        notes.quickBookmark(unitsOf(page = 0)[0])
        assertEquals("a later tap still saves", 1, notes.state.value.bookmarks.size)
    }

    private companion object {
        const val PIPELINE = 10
        const val NOW = 1_700_000_000_000L
    }
}
