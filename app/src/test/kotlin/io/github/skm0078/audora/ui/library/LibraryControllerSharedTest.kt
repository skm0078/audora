// File: app/src/test/kotlin/io/github/skm0078/audora/ui/library/LibraryControllerSharedTest.kt
// Purpose: A PDF shared from another app is added and opened, through a copy when its grant cannot be kept, and is
//          reported done only when the add finished: a screen closed mid-add (a rotation) leaves it to try again. A
//          comic archive goes to the comic hook instead of the library (ADR 2026-10-26-more-ebook-formats).

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
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryControllerSharedTest {
    private val documents = SharedDocuments()
    private val opened = mutableListOf<BookId>()
    private var doneCount = 0

    private val store = MemoryBooks()
    private val comicsTaken = mutableListOf<String>()

    private fun library(dispatcher: TestDispatcher) = Library(
        store = store,
        documents = documents,
        fingerprinter = BookFingerprinter(),
        clock = { 1L },
        io = dispatcher,
    )

    @Test
    fun `a shared PDF is added, opened and reported done`() = runTest(UnconfinedTestDispatcher()) {
        documents.put(LENT, "Moby Dick.pdf")
        val controller = LibraryController(library(UnconfinedTestDispatcher(testScheduler)), backgroundScope)

        controller.addShared(LENT, opened::add) { doneCount++ }

        assertEquals(1, opened.size)
        assertEquals(1, doneCount)
        assertFalse(controller.busy)
    }

    @Test
    fun `a grant that cannot be kept is added through a copy`() = runTest(UnconfinedTestDispatcher()) {
        documents.put(LENT, "Moby Dick.pdf")
        documents.put(COPY, "Moby Dick.pdf")
        documents.oneTimeOnly += LENT
        val controller = LibraryController(
            library(UnconfinedTestDispatcher(testScheduler)),
            backgroundScope,
            importShared = { COPY },
        )

        controller.addShared(LENT, opened::add) { doneCount++ }

        assertEquals(1, opened.size)
        assertEquals(1, doneCount)
    }

    @Test
    fun `an add cut short by the screen closing is not reported done`() = runTest(UnconfinedTestDispatcher()) {
        documents.put(LENT, "Moby Dick.pdf")
        documents.oneTimeOnly += LENT
        val screen = CoroutineScope(Job() + UnconfinedTestDispatcher(testScheduler))
        val controller = LibraryController(
            library(UnconfinedTestDispatcher(testScheduler)),
            screen,
            importShared = { awaitCancellation() },
        )

        controller.addShared(LENT, opened::add) { doneCount++ }
        assertTrue(controller.busy)
        screen.cancel()

        assertEquals(0, doneCount)
        assertTrue(opened.isEmpty())
    }

    @Test
    fun `a shared comic goes to the comic hook, is not added, and is reported done`() =
        runTest(UnconfinedTestDispatcher()) {
            documents.put(COMIC, "Strip.cbz")
            val controller = LibraryController(
                library(UnconfinedTestDispatcher(testScheduler)),
                backgroundScope,
                comics = { comicsTaken.add(it) },
            )

            controller.addShared(COMIC, opened::add) { doneCount++ }

            assertEquals(listOf(COMIC), comicsTaken)
            assertEquals(1, doneCount)
            assertTrue(opened.isEmpty())
            assertNull(store.byDocumentUri(COMIC))
        }

    @Test
    fun `a picked comic goes to the comic hook and a picked PDF still to the library`() =
        runTest(UnconfinedTestDispatcher()) {
            documents.put(COMIC, "Strip.cbz")
            documents.put(LENT, "Moby Dick.pdf")
            val controller = LibraryController(
                library(UnconfinedTestDispatcher(testScheduler)),
                backgroundScope,
                comics = { it == COMIC && comicsTaken.add(it) },
            )

            controller.add(COMIC)
            controller.add(LENT)

            assertEquals(listOf(COMIC), comicsTaken)
            assertNull(store.byDocumentUri(COMIC))
            assertNotNull(store.byDocumentUri(LENT))
        }

    private class MemoryBooks : BookStore {
        private val books = MutableStateFlow<Map<BookId, Book>>(emptyMap())

        override fun observeAll(): Flow<List<Book>> = books.map { it.values.toList() }

        override suspend fun byId(id: BookId): Book? = books.value[id]

        override suspend fun byFingerprint(fingerprint: String): Book? =
            books.value.values.find { it.fingerprint == fingerprint }

        override suspend fun byDocumentUri(documentUri: String): Book? =
            books.value.values.find { it.documentUri == documentUri }

        override suspend fun insert(book: Book): Outcome<Unit, StorageError> = write(book)

        override suspend fun update(book: Book): Outcome<Unit, StorageError> = write(book)

        override suspend fun delete(id: BookId): Outcome<Unit, StorageError> {
            books.value -= id
            return Outcome.Success(Unit)
        }

        private fun write(book: Book): Outcome<Unit, StorageError> {
            books.value += book.id to book
            return Outcome.Success(Unit)
        }
    }

    /** Documents by URI; one in [oneTimeOnly] lends its grant only for a moment, like a chat app's share. */
    private class SharedDocuments : DocumentAccess {
        private val names = mutableMapOf<String, String>()
        val oneTimeOnly = mutableSetOf<String>()

        fun put(uri: String, name: String) {
            names[uri] = name
        }

        override fun keepAccess(documentUri: String): Outcome<Unit, DocumentError> = when (documentUri) {
            in oneTimeOnly -> Outcome.Failure(DocumentError.CannotKeepAccess("no persistable grant"))
            !in names -> Outcome.Failure(DocumentError.NotFound("gone"))
            else -> Outcome.Success(Unit)
        }

        override fun releaseAccess(documentUri: String) = Unit

        override fun describe(documentUri: String): Outcome<DocumentInfo, DocumentError> {
            val name = names[documentUri] ?: return Outcome.Failure(DocumentError.NotFound("gone"))
            return Outcome.Success(DocumentInfo(name, PDF.size.toLong()))
        }

        override fun openStream(documentUri: String): Outcome<InputStream, DocumentError> = if (documentUri in names) {
            Outcome.Success(ByteArrayInputStream(PDF))
        } else {
            Outcome.Failure(DocumentError.NotFound("gone"))
        }

        override fun availability(documentUri: String) = BookAvailability.AVAILABLE

        override fun pruneUnused(inUse: Set<String>) = Unit
    }

    private companion object {
        const val LENT = "content://chat/lent.pdf"
        const val COPY = "content://audora/copy.pdf"
        const val COMIC = "content://files/strip.cbz"
        val PDF = "%PDF-1.7\n% moby\n".toByteArray(Charsets.US_ASCII)
    }
}
