// File: core/src/test/kotlin/io/github/skm0078/audora/core/library/KeepOnPhoneTest.kt
// Purpose: "Keep on this phone" moves a Drive book to ListenLock's own copy, keeps the book itself, gives the Drive
//          grant back, and never leaves a copy behind when the move cannot happen.

package io.github.skm0078.audora.core.library

import io.github.skm0078.audora.core.result.getOrNull
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepOnPhoneTest {
    private val store = FakeBookStore()
    private val docs = FakeDocuments()
    private val dispatcher = StandardTestDispatcher()
    private val library =
        Library(store, docs, BookFingerprinter(), clock = { 1L }, newId = { BookId("gita") }, io = dispatcher)
    private val discarded = mutableListOf<String>()
    private var copies = 0

    /** Copies like SharedPdfCopies: the same bytes under an app-owned file URI; [changed] makes another file. */
    private fun copier(changed: Boolean = false, fails: Boolean = false): suspend (String) -> String? = { uri ->
        if (fails) {
            null
        } else {
            val copy = "file:///data/files/shared-pdfs/copy-${copies++}/gita.pdf"
            val bytes = if (changed) pdfBytes("other") else docs.docs.getValue(uri).bytes
            docs.put(copy, "gita.pdf", bytes)
            copy
        }
    }

    private fun keeper(copy: suspend (String) -> String?) = KeepOnPhone(library, copy) { discarded += it }

    private suspend fun driveBook(): Book {
        docs.put(DRIVE, "gita.pdf", pdfBytes("gita"))
        return requireNotNull(library.add(DRIVE).getOrNull()).book
    }

    @Test
    fun `a Drive book moves to the phone copy and keeps being the same book`() = runTest(dispatcher) {
        val book = driveBook()

        val result = keeper(copier()).keep(book)

        assertEquals(KeepResult.KEPT, result)
        val kept = requireNotNull(store.byId(book.id))
        assertEquals(BookSource.PHONE, BookSources.of(kept.documentUri))
        assertEquals(book.fingerprint, kept.fingerprint)
        assertEquals(book.title, kept.title)
        assertTrue("The Drive grant is given back", DRIVE in docs.released)
        assertTrue(discarded.isEmpty())
    }

    @Test
    fun `a book already on the phone is not copied`() = runTest(dispatcher) {
        docs.put(PHONE, "gita.pdf", pdfBytes("gita"))
        val book = requireNotNull(library.add(PHONE).getOrNull()).book

        val result = keeper(copier()).keep(book)

        assertEquals(KeepResult.ALREADY_ON_PHONE, result)
        assertEquals(0, copies)
    }

    @Test
    fun `no copy from the other app leaves the book as it was`() = runTest(dispatcher) {
        val book = driveBook()

        val result = keeper(copier(fails = true)).keep(book)

        assertEquals(KeepResult.NOT_COPIED, result)
        assertEquals(DRIVE, store.byId(book.id)?.documentUri)
        assertFalse(DRIVE in docs.released)
    }

    @Test
    fun `a copy the book cannot take is deleted`() = runTest(dispatcher) {
        val book = driveBook()

        val result = keeper(copier(changed = true)).keep(book)

        assertEquals(KeepResult.NOT_MOVED, result)
        assertEquals(DRIVE, store.byId(book.id)?.documentUri)
        assertEquals(listOf("file:///data/files/shared-pdfs/copy-0/gita.pdf"), discarded)
    }

    @Test
    fun `a book removed while it was copied leaves no copy behind`() = runTest(dispatcher) {
        val book = driveBook()
        val removeFirst: suspend (String) -> String? = { uri ->
            library.remove(book.id)
            copier()(uri)
        }

        val result = keeper(removeFirst).keep(book)

        assertEquals(KeepResult.NOT_MOVED, result)
        assertNull(store.byId(book.id))
        assertEquals(1, discarded.size)
    }

    private companion object {
        const val DRIVE = "content://com.google.android.apps.docs.storage/document/acc%3D1%3Bdoc%3D9"
        const val PHONE = "content://com.android.externalstorage.documents/document/primary%3Agita.pdf"
    }
}
