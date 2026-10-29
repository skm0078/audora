// File: core/src/test/kotlin/io/github/skm0078/audora/core/home/BookGlancesTest.kt
// Purpose: The Continue card's glances count down with the pages, save sparingly, survive a new process and forget
//          removed books.

package io.github.skm0078.audora.core.home

import io.github.skm0078.audora.core.library.BookId
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookGlancesTest {
    private val dir = Files.createTempDirectory("glances").toFile()
    private val file = File(dir, BookGlanceStore.FILE_NAME)
    private var now = 1_000_000L

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun newProcess() = BookGlances(BookGlanceStore(file), { now }).apply { load() }

    @Test
    fun `the time left counts down with the pages read since`() {
        val glance = BookGlance(msLeft = 60_000, pagesLeft = 10)
        assertEquals(30_000L, glance.msLeftNow(5))
        assertEquals(60_000L, glance.msLeftNow(40))
        assertEquals(0L, glance.msLeftNow(-1))
        assertNull(BookGlance().msLeftNow(5))
    }

    @Test
    fun `the last line and the time left survive a new process`() {
        val glances = newProcess()
        glances.heard(BOOK, "Call me\tIshmael.")
        glances.timeLeft(BOOK, msLeft = 90_000, pagesLeft = 12)
        glances.flush()
        val again = newProcess().glances.value.getValue(BOOK)
        assertEquals("Call me Ishmael.", again.line)
        assertEquals(90_000L, again.msLeft)
        assertEquals(12, again.pagesLeft)
    }

    @Test
    fun `changes inside the interval wait for a flush`() {
        val glances = newProcess()
        glances.heard(BOOK, "First.")
        now += 1_000
        glances.heard(BOOK, "Second.")
        assertEquals("First.", newProcess().glances.value[BOOK]?.line)
        glances.flush()
        assertEquals("Second.", newProcess().glances.value[BOOK]?.line)
    }

    @Test
    fun `a long sentence is cut to fit the card`() {
        val glances = newProcess()
        glances.heard(BOOK, "word ".repeat(100))
        assertTrue(glances.glances.value.getValue(BOOK).line!!.length <= BookGlance.LINE_MAX)
    }

    @Test
    fun `removed books are forgotten`() {
        val glances = newProcess()
        glances.heard(BOOK, "Kept.")
        glances.heard(OTHER, "Gone.")
        glances.prune(setOf(BOOK))
        assertEquals(setOf(BOOK), newProcess().glances.value.keys)
    }

    @Test
    fun `only the books read last are kept`() {
        val glances = newProcess()
        repeat(BookGlances.BOOKS_KEPT + 5) { index ->
            now += 1
            glances.heard(BookId("book-$index"), "Line $index.")
        }
        glances.flush()
        val kept = newProcess().glances.value.keys
        assertEquals(BookGlances.BOOKS_KEPT, kept.size)
        assertTrue(BookId("book-${BookGlances.BOOKS_KEPT + 4}") in kept)
        assertTrue(BookId("book-0") !in kept)
    }

    @Test
    fun `a foreign file reads as no glances`() {
        file.writeText("something else\nbook\t1\t2\t3\tline\n")
        assertTrue(newProcess().glances.value.isEmpty())
    }

    private companion object {
        val BOOK = BookId("book")
        val OTHER = BookId("other")
    }
}
