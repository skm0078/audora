// File: app/src/test/kotlin/io/github/skm0078/audora/ui/library/BookEditDraftTest.kt
// Purpose: Edit details saves only what the listener changed, so untouched fields keep following the file (ADR-068).

package io.github.skm0078.audora.ui.library

import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookEdit
import io.github.skm0078.audora.core.library.BookField
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.CoverChoice
import io.github.skm0078.audora.core.library.DocumentFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookEditDraftTest {
    private val pdf = Book(
        BookId("b"), "content://b", "Moby Dick", 10, "f", 300, 1, null,
        author = "Herman Melville", language = "en", format = DocumentFormat.PDF,
    )

    @Test
    fun `an untouched draft saves nothing`() {
        assertEquals(BookEdit(), BookEditDraft.of(pdf).toEdit(pdf))
    }

    @Test
    fun `only the changed fields are saved`() {
        val draft = BookEditDraft.of(pdf).copy(author = " H. Melville ", language = "bn")

        assertEquals(BookEdit(author = "H. Melville", language = "bn"), draft.toEdit(pdf))
    }

    @Test
    fun `As the book says hands an edited language back to the file`() {
        val edited = pdf.copy(language = "hi", edited = setOf(BookField.LANGUAGE))
        val draft = BookEditDraft.of(edited)
        assertEquals("hi", draft.language)

        val edit = draft.copy(language = "").toEdit(edited)

        assertEquals(BookEdit(fromFile = setOf(BookField.LANGUAGE)), edit)
    }

    @Test
    fun `a page cover needs a page of the book, typed from 1`() {
        val draft = BookEditDraft.of(pdf).copy(cover = CoverOption.PAGE, page = "12")
        assertEquals(CoverChoice.Page(11), draft.toEdit(pdf).cover)
        assertTrue(draft.savable(pdf))

        listOf("", "0", "301").forEach { assertFalse(it, draft.copy(page = it).savable(pdf)) }
    }

    @Test
    fun `a gallery cover needs a picture, unless the book already has one`() {
        val draft = BookEditDraft.of(pdf).copy(cover = CoverOption.GALLERY)
        assertFalse(draft.savable(pdf))
        assertTrue(draft.copy(picture = "content://pic").savable(pdf))
        assertTrue(
            BookEditDraft.of(pdf.copy(cover = CoverChoice.Picture)).savable(pdf.copy(cover = CoverChoice.Picture)),
        )
    }

    @Test
    fun `a blank title cannot be saved`() {
        assertFalse(BookEditDraft.of(pdf).copy(title = "  ").savable(pdf))
    }

    @Test
    fun `covers offered fit the format`() {
        assertEquals(
            listOf(CoverOption.FIRST_PAGE, CoverOption.PAGE, CoverOption.GALLERY),
            BookEditDraft.coverOptions(pdf),
        )
        assertEquals(
            listOf(CoverOption.FIRST_PAGE, CoverOption.GALLERY, CoverOption.FROM_BOOK),
            BookEditDraft.coverOptions(pdf.copy(format = DocumentFormat.EPUB, pageCount = null)),
        )
    }
}
