// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/djvu/DjvuParserTest.kt
// Purpose: A DjVu with a text layer becomes a text book: chapters from the outline, the title from the metadata, and
//          a scan is told apart so it can go to OCR instead.

package io.github.skm0078.audora.playback.djvu

import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.text.BlockKind
import io.github.skm0078.audora.playback.text.TextBookError
import io.github.skm0078.audora.playback.text.TextDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DjvuParserTest {
    private fun parsed(pages: DjvuPages, max: Long = Long.MAX_VALUE): TextDocument =
        (DjvuParser.parse(pages, max) as Outcome.Success).value

    @Test
    fun `the outline makes chapters at every depth and the metadata names the book`() {
        val document = parsed(DjvuFixtures.textBook())
        assertEquals("A DjVu Journey Book", document.title)
        assertEquals(listOf("Chapter One", "Chapter Two", "Hindi page"), document.chapters.map { it.title })
        assertEquals(BlockKind.HEADING, document.chapters[0].blocks[0].kind)
        assertEquals("Chapter Two starts on the second page.", document.chapters[1].blocks[0].text)
        assertEquals("मन जहाँ डर से परे है।", document.chapters[2].blocks.single().text)
    }

    @Test
    fun `without an outline the book is one untitled chapter`() {
        val pages = (1..3).map { DjvuFixtures.text("text-page$it.sexp") }
        val document = parsed(DjvuFixtures.FakePages(pages))
        assertNull(document.title)
        assertEquals(1, document.chapters.size)
        assertNull(document.chapters[0].title)
        assertEquals(6, document.chapters[0].blocks.size)
    }

    @Test
    fun `pages before the first outline entry are a chapter of their own, and bad targets are skipped`() {
        val pages = (1..3).map { DjvuFixtures.text("text-page$it.sexp") }
        val outline = """(bookmarks ("Second" "#2") ("Named" "#intro") ("Past the end" "#9") ("Again" "#2"))"""
        val document = parsed(DjvuFixtures.FakePages(pages, outline))
        assertEquals(listOf(null, "Second"), document.chapters.map { it.title })
        assertEquals("Chapter Two starts on the second page.", document.chapters[1].blocks[0].text)
    }

    @Test
    fun `too much text and no text are refused`() {
        assertTrue(DjvuParser.parse(DjvuFixtures.textBook(), maxTextChars = 50) is Outcome.Failure)
        val tooLarge = (DjvuParser.parse(DjvuFixtures.textBook(), 50) as Outcome.Failure).error
        assertTrue(tooLarge is TextBookError.TooLarge)
        val blank = DjvuFixtures.FakePages(listOf("", "(page 0 0 10 10)"))
        assertEquals(TextBookError.NoText, (DjvuParser.parse(blank, Long.MAX_VALUE) as Outcome.Failure).error)
    }

    @Test
    fun `a text layer on most pages reads as text, a scan does not`() {
        assertTrue(DjvuParser.hasTextLayer(DjvuFixtures.textBook()))
        assertFalse(DjvuParser.hasTextLayer(DjvuFixtures.FakePages(listOf("", "", ""))))
        assertFalse(DjvuParser.hasTextLayer(DjvuFixtures.FakePages(emptyList())))
        // A cover without text in front of text pages is still a text book.
        val withCover = listOf("") + (1..2).map { DjvuFixtures.text("text-page$it.sexp") }
        assertTrue(DjvuParser.hasTextLayer(DjvuFixtures.FakePages(withCover)))
    }

    @Test
    fun `metadata is read from a list of annotations or from annotations one after another`() {
        val printed = DjvuFixtures.text("text-annotations.sexp")
        assertEquals("ListenLock Tests", DjvuParser.metadata(printed, "author"))
        val bridged = """((background #ffffff) (metadata (title "Bridged") (author "  ")))"""
        assertEquals("Bridged", DjvuParser.metadata(bridged, "title"))
        assertNull(DjvuParser.metadata(bridged, "author"))
        assertNull(DjvuParser.metadata("", "title"))
    }
}
