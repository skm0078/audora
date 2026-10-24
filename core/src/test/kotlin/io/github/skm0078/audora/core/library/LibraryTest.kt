// File: core/src/test/kotlin/io/github/skm0078/audora/core/library/LibraryTest.kt
// Purpose: Adding, recognizing, relocating and removing books, and that read grants are kept and given back.

package io.github.skm0078.audora.core.library

import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.errorOrNull
import io.github.skm0078.audora.core.result.getOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTest {
    private val store = FakeBookStore()
    private val docs = FakeDocuments()
    private var now = 1_000L
    private var nextId = 0
    private val dispatcher = StandardTestDispatcher()
    private val library = Library(
        store = store,
        documents = docs,
        fingerprinter = BookFingerprinter(),
        clock = { now },
        newId = { BookId("book-${nextId++}") },
        io = dispatcher,
    )

    private suspend fun addOk(uri: String): AddResult = requireNotNull(library.add(uri).getOrNull())

    @Test
    fun `a picked PDF is added with its title, size and a kept grant`() = runTest(dispatcher) {
        docs.put(URI_A, "Moby Dick.pdf", pdfBytes("moby"))

        val result = addOk(URI_A)

        assertTrue(result is AddResult.Added)
        val book = result.book
        assertEquals("Moby Dick", book.title)
        assertEquals(pdfBytes("moby").size.toLong(), book.sizeBytes)
        assertNull(book.pageCount)
        assertEquals(now, book.addedAtMillis)
        assertTrue(URI_A in docs.granted)
        assertEquals(listOf(book), library.books().first())
    }

    @Test
    fun `picking the same file again does not add a second copy`() = runTest(dispatcher) {
        docs.put(URI_A, "a.pdf", pdfBytes("a"))
        val first = addOk(URI_A).book

        val again = addOk(URI_A)

        assertEquals(AddResult.AlreadyInLibrary(first), again)
        assertEquals(1, store.all.size)
    }

    @Test
    fun `the same PDF from a new place moves the existing book and gives back the old grant`() = runTest(dispatcher) {
        docs.put(URI_A, "a.pdf", pdfBytes("a"))
        val original = addOk(URI_A).book
        docs.docs.remove(URI_A)
        docs.put(URI_B, "a copy.pdf", pdfBytes("a"))

        val result = addOk(URI_B)

        assertTrue(result is AddResult.FoundAgain)
        assertEquals(original.id, result.book.id)
        assertEquals(URI_B, result.book.documentUri)
        assertEquals("a", result.book.title)
        assertEquals(listOf(URI_B), store.all.map(Book::documentUri))
        assertFalse(URI_A in docs.granted)
    }

    @Test
    fun `a file changed in place keeps its book and takes the new fingerprint`() = runTest(dispatcher) {
        docs.put(URI_A, "a.pdf", pdfBytes("v1"))
        val original = addOk(URI_A).book
        docs.put(URI_A, "a.pdf", pdfBytes("v2 longer"))

        val result = addOk(URI_A)

        assertTrue(result is AddResult.AlreadyInLibrary)
        assertEquals(original.id, result.book.id)
        assertEquals(1, store.all.size)
        assertTrue(result.book.fingerprint != original.fingerprint)
    }

    @Test
    fun `a file not a PDF, EPUB or text is refused and its grant given back`() = runTest(dispatcher) {
        // A real PNG head: binary, with NUL bytes (EPUB and text books are accepted since ADR-042).
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D)
        docs.put(URI_A, "photo.pdf", png)

        assertEquals(LibraryError.NotAPdf, library.add(URI_A).errorOrNull())
        assertTrue(store.all.isEmpty())
        assertEquals(listOf(URI_A), docs.released)
    }

    @Test
    fun `a provider without lasting grants is reported clearly`() = runTest(dispatcher) {
        docs.put(URI_A, "a.pdf", pdfBytes("a"))
        docs.oneTimeOnly += URI_A

        assertEquals(LibraryError.CannotKeepAccess, library.add(URI_A).errorOrNull())
        assertTrue(store.all.isEmpty())
    }

    @Test
    fun `a storage failure on add is reported and the grant given back`() = runTest(dispatcher) {
        docs.put(URI_A, "a.pdf", pdfBytes("a"))
        store.failWrites = true

        assertTrue(library.add(URI_A).errorOrNull() is LibraryError.Storage)
        assertFalse(URI_A in docs.granted)
    }

    @Test
    fun `a missing file is flagged, and a lost grant is told apart`() = runTest(dispatcher) {
        docs.put(URI_A, "a.pdf", pdfBytes("a"))
        docs.put(URI_B, "b.pdf", pdfBytes("b"))
        docs.put(URI_C, "c.pdf", pdfBytes("c"))
        val a = addOk(URI_A).book
        val b = addOk(URI_B).book
        val c = addOk(URI_C).book
        docs.docs.remove(URI_B)
        docs.granted -= URI_C

        val availability = library.availability(listOf(a, b, c))

        assertEquals(BookAvailability.AVAILABLE, availability[a.id])
        assertEquals(BookAvailability.MISSING, availability[b.id])
        assertEquals(BookAvailability.ACCESS_LOST, availability[c.id])
    }

    @Test
    fun `relocating a missing book to the same PDF keeps the book`() = runTest(dispatcher) {
        docs.put(URI_A, "a.pdf", pdfBytes("a"))
        val book = addOk(URI_A).book
        library.markOpened(book.id, pageCount = 42)
        docs.docs.remove(URI_A)
        docs.put(URI_B, "moved.pdf", pdfBytes("a"))

        val moved = requireNotNull(library.relocate(book.id, URI_B).getOrNull())

        assertEquals(book.id, moved.id)
        assertEquals(URI_B, moved.documentUri)
        assertEquals(42, moved.pageCount)
        assertFalse(URI_A in docs.granted)
        assertTrue(URI_B in docs.granted)
    }

    @Test
    fun `relocating to a different PDF is refused and nothing changes`() = runTest(dispatcher) {
        docs.put(URI_A, "a.pdf", pdfBytes("a"))
        val book = addOk(URI_A).book
        docs.put(URI_B, "other.pdf", pdfBytes("other"))

        assertEquals(LibraryError.DifferentFile, library.relocate(book.id, URI_B).errorOrNull())
        assertEquals(URI_A, library.book(book.id)?.documentUri)
        assertFalse(URI_B in docs.granted)
    }

    @Test
    fun `relocating onto a URI held by a stale entry drops that entry`() = runTest(dispatcher) {
        docs.put(URI_A, "a.pdf", pdfBytes("a"))
        docs.put(URI_B, "b.pdf", pdfBytes("b"))
        val a = addOk(URI_A).book
        val b = addOk(URI_B).book
        // The user overwrote b.pdf with a copy of a.pdf, then deleted a.pdf.
        docs.put(URI_B, "b.pdf", pdfBytes("a"))
        docs.docs.remove(URI_A)

        val moved = requireNotNull(library.relocate(a.id, URI_B).getOrNull())

        assertEquals(URI_B, moved.documentUri)
        assertNull(library.book(b.id))
        assertEquals(listOf(a.id), store.all.map(Book::id))
    }

    @Test
    fun `relocating an unknown book is reported`() = runTest(dispatcher) {
        docs.put(URI_A, "a.pdf", pdfBytes("a"))

        assertEquals(LibraryError.NotInLibrary, library.relocate(BookId("nope"), URI_A).errorOrNull())
    }

    @Test
    fun `removing a book deletes the entry and gives back its grant`() = runTest(dispatcher) {
        docs.put(URI_A, "a.pdf", pdfBytes("a"))
        val book = addOk(URI_A).book

        assertTrue(library.remove(book.id) is Outcome.Success)
        assertTrue(store.all.isEmpty())
        assertFalse(URI_A in docs.granted)
        assertTrue(library.remove(book.id) is Outcome.Success)
    }

    @Test
    fun `opened books move to the top and remember their page count`() = runTest(dispatcher) {
        docs.put(URI_A, "a.pdf", pdfBytes("a"))
        docs.put(URI_B, "b.pdf", pdfBytes("b"))
        val a = addOk(URI_A).book
        now = 2_000L
        val b = addOk(URI_B).book
        assertEquals(listOf(b.id, a.id), library.books().first().map(Book::id))

        now = 3_000L
        library.markOpened(a.id, pageCount = 574)

        val books = library.books().first()
        assertEquals(listOf(a.id, b.id), books.map(Book::id))
        assertEquals(574, books.first().pageCount)
        assertEquals(3_000L, books.first().lastOpenedAtMillis)
    }

    @Test
    fun `a move that cannot be saved gives back the new grant and keeps the old`() = runTest(dispatcher) {
        docs.put(URI_A, "a.pdf", pdfBytes("a"))
        addOk(URI_A)
        docs.put(URI_B, "a copy.pdf", pdfBytes("a"))
        store.failWrites = true

        assertTrue(library.add(URI_B) is Outcome.Failure)

        assertEquals(listOf(URI_A), store.all.map(Book::documentUri))
        assertTrue(URI_A in docs.granted)
        assertFalse(URI_B in docs.granted)
    }

    @Test
    fun `pruning hands every book's file to the documents, so unused copies can go`() = runTest(dispatcher) {
        docs.put(URI_A, "a.pdf", pdfBytes("a"))
        docs.put(URI_B, "b.pdf", pdfBytes("b"))
        addOk(URI_A)
        addOk(URI_B)

        library.pruneDocuments()

        assertEquals(listOf(setOf(URI_A, URI_B)), docs.pruned)
    }

    @Test
    fun `pruning runs once per process, so a recreated screen does not prune again`() = runTest(dispatcher) {
        docs.put(URI_A, "a.pdf", pdfBytes("a"))
        addOk(URI_A)

        library.pruneDocuments()
        library.pruneDocuments()

        assertEquals(1, docs.pruned.size)
    }

    @Test
    fun `when the books cannot be read nothing is pruned`() = runTest(dispatcher) {
        docs.put(URI_A, "a.pdf", pdfBytes("a"))
        addOk(URI_A)
        store.failReads = true

        library.pruneDocuments()

        assertTrue(docs.pruned.isEmpty())
    }

    @Test
    fun `an unknown size still fingerprints and records -1`() = runTest(dispatcher) {
        docs.docs[URI_A] = FakeDocuments.Doc("a.pdf", pdfBytes("a"), reportsSize = false)

        assertEquals(-1L, addOk(URI_A).book.sizeBytes)
    }

    @Test
    fun `details filled from the file keep the listener's edits and reach the list`() = runTest(dispatcher) {
        docs.put(URI_A, "scan.pdf", pdfBytes("a"))
        val id = addOk(URI_A).book.id

        library.edit(id, BookEdit(author = "Me"))
        val filled = requireNotNull(library.fill(id, FileDetails(DocumentFormat.PDF, "Real Title", "Them")).getOrNull())

        assertEquals("Real Title", filled.title)
        assertEquals("Me", filled.author)
        assertEquals(filled, library.books().first().single())
        val reset = requireNotNull(library.resetDetails(id).getOrNull())
        assertTrue(reset.edited.isEmpty())
        assertEquals(0, reset.detailsVersion)
    }

    @Test
    fun `editing a book that is not in the library says so`() = runTest(dispatcher) {
        assertEquals(LibraryError.NotInLibrary, library.edit(BookId("gone"), BookEdit(title = "x")).errorOrNull())
    }

    private companion object {
        const val URI_A = "content://provider/document/a"
        const val URI_B = "content://provider/document/b"
        const val URI_C = "content://provider/document/c"
    }
}
