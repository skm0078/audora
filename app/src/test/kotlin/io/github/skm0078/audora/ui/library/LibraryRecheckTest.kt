// File: app/src/test/kotlin/io/github/skm0078/audora/ui/library/LibraryRecheckTest.kt
// Purpose: The library's file check on return to the screen (speed audit item 4): quick returns never stack checks,
//          an unchanged check leaves the list alone, and a file deleted meanwhile is still flagged.

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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryRecheckTest {
    private val files = CountedFiles()

    /** The check's I/O waits for [runCurrent], so a test can return to the screen while one is still running. */
    private fun TestScope.controller(): LibraryController {
        val io = StandardTestDispatcher(testScheduler)
        val library = Library(TwoBooks, files, BookFingerprinter(), clock = { 1L }, io = io)
        return LibraryController(library, backgroundScope).apply {
            start()
            runCurrent()
        }
    }

    @Test
    fun `quick returns to the library run one more check, not one each`() = runTest(UnconfinedTestDispatcher()) {
        val controller = controller()
        val atStart = files.batches

        repeat(RETURNS) { controller.refreshAvailability() }
        runCurrent()

        assertEquals("the running check plus one after it", atStart + 2, files.batches)
    }

    @Test
    fun `a return with nothing changed leaves the list as it was`() = runTest(UnconfinedTestDispatcher()) {
        val controller = controller()
        val before = controller.listState

        controller.refreshAvailability()
        runCurrent()

        assertSame("No new list, so nothing re-sorts or redraws", before, controller.listState)
    }

    @Test
    fun `a file deleted while away is flagged on return`() = runTest(UnconfinedTestDispatcher()) {
        val controller = controller()

        files.gone += GORA.documentUri
        controller.refreshAvailability()
        runCurrent()

        val items = (controller.listState as LibraryListState.Loaded).all.associate { it.book.id to it.availability }
        assertEquals(BookAvailability.AVAILABLE, items[GITA.id])
        assertEquals(BookAvailability.MISSING, items[GORA.id])
    }

    /** Every file is there unless in [gone]; [batches] counts the library's checks. */
    private class CountedFiles : DocumentAccess {
        val gone = mutableSetOf<String>()
        var batches = 0

        override fun availability(documentUri: String) =
            if (documentUri in gone) BookAvailability.MISSING else BookAvailability.AVAILABLE

        override fun availability(documentUris: Collection<String>): Map<String, BookAvailability> {
            batches++
            return super.availability(documentUris)
        }

        override fun keepAccess(documentUri: String): Outcome<Unit, DocumentError> = Outcome.Success(Unit)

        override fun releaseAccess(documentUri: String) = Unit

        override fun describe(documentUri: String): Outcome<DocumentInfo, DocumentError> =
            Outcome.Failure(DocumentError.NotFound("not used"))

        override fun openStream(documentUri: String): Outcome<InputStream, DocumentError> =
            Outcome.Failure(DocumentError.NotFound("not used"))
    }

    /** Two books that never change; nothing here writes to the store. */
    private object TwoBooks : BookStore {
        private val books = listOf(GITA, GORA)
        private val readOnly = Outcome.Failure(StorageError("read-only"))

        override fun observeAll(): Flow<List<Book>> = flowOf(books)

        override suspend fun byId(id: BookId): Book? = books.find { it.id == id }

        override suspend fun byFingerprint(fingerprint: String): Book? = books.find { it.fingerprint == fingerprint }

        override suspend fun byDocumentUri(documentUri: String): Book? = books.find { it.documentUri == documentUri }

        override suspend fun insert(book: Book): Outcome<Unit, StorageError> = readOnly

        override suspend fun update(book: Book): Outcome<Unit, StorageError> = readOnly

        override suspend fun delete(id: BookId): Outcome<Unit, StorageError> = readOnly
    }

    private companion object {
        const val RETURNS = 3
        val GITA = book("gita", "Gitanjali")
        val GORA = book("gora", "Gora")

        fun book(id: String, title: String) = Book(BookId(id), "content://test/$id", title, 1_000, "f-$id", 10, 1, null)
    }
}
