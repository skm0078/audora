// File: core/src/test/kotlin/io/github/skm0078/audora/core/library/SyntheticLibraryTest.kt
// Purpose: The load tests' made-up library keeps the promises they rely on: a search for a title finds one book, and
//          every read book is still being read.

package io.github.skm0078.audora.core.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntheticLibraryTest {
    private val books = SyntheticLibrary.books(5_000)

    @Test
    fun `no title is part of another, so a search for one finds that book alone`() {
        val titles = books.map { it.title }
        assertEquals(titles.size, titles.toSet().size)
        listOf(0, 9, 99, 999, books.lastIndex).forEach { i ->
            assertEquals(listOf(titles[i]), titles.filter { it.contains(titles[i]) })
        }
    }

    @Test
    fun `every read book has a position in its first half, none finished`() {
        val read = SyntheticLibrary.read(books)
        val anchors = SyntheticLibrary.anchors(books).associateBy { it.bookId }
        assertEquals(read.map { it.id }, anchors.keys.toList())
        read.forEach { book ->
            val progress = checkNotNull(BookProgress.of(book, anchors[book.id])) { "${book.title} has no progress" }
            assertTrue(progress.page < progress.pageCount / 2)
            assertFalse(progress.isFinished)
        }
    }

    @Test
    fun `every book is on one of the shelves`() {
        val shelves = SyntheticLibrary.collections().map { it.id }.toSet()
        assertEquals(SyntheticLibrary.COLLECTIONS, shelves.size)
        assertTrue(SyntheticLibrary.members(books).values.all { it.single() in shelves })
    }
}
