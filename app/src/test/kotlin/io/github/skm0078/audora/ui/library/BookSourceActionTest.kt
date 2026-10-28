// File: app/src/test/kotlin/io/github/skm0078/audora/ui/library/BookSourceActionTest.kt
// Purpose: "Keep on this phone" is offered, in the book's details, only for a book read through another app
//          (ADR 2026-10-28-book-source).

package io.github.skm0078.audora.ui.library

import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookAvailability
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.BookSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookSourceActionTest {
    private fun item(uri: String) = LibraryItem(
        book = Book(BookId("a"), uri, "A", 1L, "fp", 10, 0L, null),
        availability = BookAvailability.AVAILABLE,
    )

    @Test
    fun `a Drive book can be kept on the phone`() {
        val drive = item(DRIVE)
        assertEquals(BookSource.GOOGLE_DRIVE, drive.source)
        assertTrue(BookAction.KEEP_ON_PHONE.appliesTo(drive))
    }

    @Test
    fun `a book already on the phone is not offered a copy`() {
        listOf(PHONE, COPY).forEach { uri ->
            val phone = item(uri)
            assertEquals(uri, BookSource.PHONE, phone.source)
            assertFalse(uri, BookAction.KEEP_ON_PHONE.appliesTo(phone))
        }
    }

    @Test
    fun `the copy sits in the details sheet, not in the menu's eight lines`() {
        assertEquals(MenuPlace.DETAILS, BookAction.KEEP_ON_PHONE.place)
        val choices = BookMenuChoices.of(item(DRIVE))
        assertFalse(BookAction.KEEP_ON_PHONE in choices.rows)
    }

    private companion object {
        const val DRIVE = "content://com.google.android.apps.docs.storage/document/acc%3D1%3Bdoc%3D9"
        const val PHONE = "content://com.android.externalstorage.documents/document/primary%3Agita.pdf"
        const val COPY = "file:///data/user/0/io.github.skm0078.audora/files/shared-pdfs/abc/gita.pdf"
    }
}
