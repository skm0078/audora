// File: app/src/test/kotlin/io/github/skm0078/audora/ui/library/LibraryControllerUndoTest.kt
// Purpose: A removed book leaves the list at once but stays in the library until its undo window ends (CF-3):
//          Undo brings it back untouched, the window's end or the screen closing removes it, and a failed removal
//          shows it again.

package io.github.skm0078.audora.ui.library

import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookAvailability
import io.github.skm0078.audora.core.library.BookFingerprinter
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.BookStore
import io.github.skm0078.audora.core.library.DocumentAccess
import io.github.skm0078.audora.core.library.DocumentError
import io.github.skm0078.audora.core.library.DocumentInfo
import io.github.skm0078.audora.core.library.Library
import io.github.skm0078.audora.core.library.StorageError
import io.github.skm0078.audora.core.result.Outcome
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryControllerUndoTest {
    private val books = MemoryBooks()
    private val released = mutableListOf<String>()

    private fun TestScope.controller(scope: CoroutineScope = backgroundScope): LibraryController {
        val unconfined = UnconfinedTestDispatcher(testScheduler)
        val library = Library(books, KeptDocuments(released), BookFingerprinter(), clock = { 1L }, io = unconfined)
        return LibraryController(library, scope).apply { start() }
    }

    private fun LibraryController.shown(): List<BookId> =
        (listState as LibraryListState.Loaded).items.map { it.book.id }

    private fun LibraryController.remove(book: Book, undoMs: Long = UNDO_MS) {
        proposeRemoval(book)
        confirmRemoval(undoMs)
    }

    @Test
    fun `a removed book leaves the list at once and the library only after the undo window`() =
        runTest(UnconfinedTestDispatcher()) {
            books.put(GITA, GORA)
            val controller = controller()

            controller.remove(GITA)

            assertEquals(listOf(GORA.id), controller.shown())
            assertNotNull("Nothing is deleted while Undo is offered", books.stored(GITA.id))
            assertTrue((controller.listState as LibraryListState.Loaded).leaving.contains(GITA.id))
            val message = controller.message as LibraryMessage.Removed
            assertEquals(GITA.id, message.undo)
            assertEquals(UNDO_MS, message.undoMs)

            advanceTimeBy(UNDO_MS + REMOVAL_UNDO_GRACE_MS + 1)
            runCurrent()

            assertNull(books.stored(GITA.id))
            assertEquals(listOf(GITA.documentUri), released)
            assertEquals(listOf(GORA.id), controller.shown())
            assertFalse((controller.listState as LibraryListState.Loaded).leaving.contains(GITA.id))
        }

    @Test
    fun `undo brings the book back and nothing is removed later`() = runTest(UnconfinedTestDispatcher()) {
        books.put(GITA, GORA)
        val controller = controller()
        controller.remove(GITA)

        advanceTimeBy(UNDO_MS / 2)
        controller.undoRemoval(GITA.id)
        advanceTimeBy(UNDO_MS * 2)
        runCurrent()

        assertEquals(GITA, books.stored(GITA.id))
        assertTrue(released.isEmpty())
        assertEquals(setOf(GITA.id, GORA.id), controller.shown().toSet())
        assertTrue((controller.listState as LibraryListState.Loaded).leaving.isEmpty())
    }

    @Test
    fun `an Undo tapped as the snackbar closes still keeps the book`() = runTest(UnconfinedTestDispatcher()) {
        books.put(GITA)
        val controller = controller()
        controller.remove(GITA)

        advanceTimeBy(UNDO_MS + REMOVAL_UNDO_GRACE_MS / 2)
        controller.undoRemoval(GITA.id)
        advanceTimeBy(UNDO_MS)
        runCurrent()

        assertNotNull(books.stored(GITA.id))
    }

    @Test
    fun `the screen closing inside the window still removes the book`() = runTest(UnconfinedTestDispatcher()) {
        books.put(GITA)
        val screen = CoroutineScope(Job() + UnconfinedTestDispatcher(testScheduler))
        val controller = controller(screen)
        controller.remove(GITA)

        screen.cancel()
        runCurrent()

        assertNull(books.stored(GITA.id))
    }

    @Test
    fun `a removal that fails shows the book again and says so`() = runTest(UnconfinedTestDispatcher()) {
        books.put(GITA)
        books.failDeletes = true
        val controller = controller()
        controller.remove(GITA)

        advanceTimeBy(UNDO_MS + REMOVAL_UNDO_GRACE_MS + 1)
        runCurrent()

        assertNotNull(books.stored(GITA.id))
        assertEquals(listOf(GITA.id), controller.shown())
        assertTrue(controller.message is LibraryMessage.Failed)
    }

    @Test
    fun `two removals are undone one at a time`() = runTest(UnconfinedTestDispatcher()) {
        books.put(GITA, GORA)
        val controller = controller()
        controller.remove(GITA)
        controller.remove(GORA)

        assertTrue(controller.shown().isEmpty())
        assertEquals(
            "The library is not empty while books can still come back",
            emptyList<BookId>(),
            (controller.listState as LibraryListState.Loaded).all.map { it.book.id },
        )
        controller.undoRemoval(GORA.id)
        advanceTimeBy(UNDO_MS + REMOVAL_UNDO_GRACE_MS + 1)
        runCurrent()

        assertNull(books.stored(GITA.id))
        assertNotNull(books.stored(GORA.id))
        assertEquals(listOf(GORA.id), controller.shown())
    }

    @Test
    fun `a second confirm for a book already leaving changes nothing`() = runTest(UnconfinedTestDispatcher()) {
        books.put(GITA)
        val controller = controller()
        controller.remove(GITA)
        advanceTimeBy(UNDO_MS / 2)
        controller.remove(GITA)
        controller.undoRemoval(GITA.id)
        advanceTimeBy(UNDO_MS * 2)
        runCurrent()

        assertNotNull("One Undo undoes the one removal", books.stored(GITA.id))
    }

    private class MemoryBooks : BookStore {
        private val books = MutableStateFlow<Map<BookId, Book>>(emptyMap())
        var failDeletes = false

        fun put(vararg added: Book) {
            books.value += added.associateBy { it.id }
        }

        fun stored(id: BookId): Book? = books.value[id]

        override fun observeAll(): Flow<List<Book>> = books.map { it.values.toList() }

        override suspend fun byId(id: BookId): Book? = books.value[id]

        override suspend fun byFingerprint(fingerprint: String): Book? =
            books.value.values.find { it.fingerprint == fingerprint }

        override suspend fun byDocumentUri(documentUri: String): Book? =
            books.value.values.find { it.documentUri == documentUri }

        override suspend fun insert(book: Book): Outcome<Unit, StorageError> = Outcome.Success(put(book))

        override suspend fun update(book: Book): Outcome<Unit, StorageError> = Outcome.Success(put(book))

        override suspend fun delete(id: BookId): Outcome<Unit, StorageError> {
            if (failDeletes) return Outcome.Failure(StorageError("disk full"))
            books.value -= id
            return Outcome.Success(Unit)
        }
    }

    /** Every file is there; [released] records each grant given back, which happens only when a book really goes. */
    private class KeptDocuments(private val released: MutableList<String>) : DocumentAccess {
        override fun keepAccess(documentUri: String): Outcome<Unit, DocumentError> = Outcome.Success(Unit)

        override fun releaseAccess(documentUri: String) {
            released += documentUri
        }

        override fun describe(documentUri: String): Outcome<DocumentInfo, DocumentError> =
            Outcome.Failure(DocumentError.NotFound("not used"))

        override fun openStream(documentUri: String): Outcome<InputStream, DocumentError> =
            Outcome.Failure(DocumentError.NotFound("not used"))

        override fun availability(documentUri: String) = BookAvailability.AVAILABLE

        override fun pruneUnused(inUse: Set<String>) = Unit
    }

    private companion object {
        const val UNDO_MS = 8_000L
        val GITA = book("gita", "Gitanjali")
        val GORA = book("gora", "Gora")

        fun book(id: String, title: String) = Book(BookId(id), "content://test/$id", title, 1_000, "f-$id", 10, 1, null)
    }
}
