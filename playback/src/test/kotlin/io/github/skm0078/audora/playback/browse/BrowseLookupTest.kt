// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/browse/BrowseLookupTest.kt
// Purpose: Folders list their rows, rows are found by id, and each playable row opens its book the right way.

package io.github.skm0078.audora.playback.browse

import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.upnext.UpNextQueue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowseLookupTest {
    private val texts = object : BrowseTexts {
        override val recent = "Recent"
        override val upNext = "Up next"
        override val library = "Library"
        override val resume = "Resume"
        override val whereLeftOff = "Where you left off"

        override fun page(number: Int) = "Page $number"

        override fun chapter(number: Int) = "Chapter $number"
    }
    private val book = Book(BookId("b1"), "content://b1", "Gita", 1, "f", 40, 0, 10)
    private val played = ArrayList<String>()
    private val lookup = BrowseLookup(
        BrowseTree(texts),
        BrowseSources(
            books = { listOf(book) },
            upNext = { UpNextQueue().add(book.id, startPage = 3, startLabel = "Two") },
            chapters = { listOf(BookChapter("One", 0), BookChapter("Two", 3)) },
            resume = { id ->
                played += "resume ${id.value}"
                Outcome.Success(Unit)
            },
            playFrom = { id, page ->
                played += "from ${id.value} p$page"
                Outcome.Success(Unit)
            },
        ),
    )

    @Test
    fun `a book's folder is resume and its chapters`() = runBlocking {
        val rows = requireNotNull(lookup.children(BrowseNode.Book(book.id)))
        assertEquals(listOf("Resume", "One", "Two"), rows.map { it.title })
    }

    @Test
    fun `a playable row is not a folder, and a removed book has none`() = runBlocking {
        assertNull(lookup.children(BrowseNode.Resume(book.id)))
        assertNull(lookup.children(BrowseNode.Book(BookId("gone"))))
    }

    @Test
    fun `rows are found by id as their folders show them`() = runBlocking {
        assertEquals("Two", lookup.entry(BrowseNode.Chapter(book.id, 3))?.title)
        assertEquals("Gita", lookup.entry(BrowseNode.Resume(book.id))?.title)
        assertEquals("Up next", lookup.entry(BrowseNode.UpNext)?.title)
        assertNull(lookup.entry(BrowseNode.Chapter(book.id, 7)))
    }

    @Test
    fun `resume, book and chapter rows play their way, folders play nothing`() = runBlocking {
        lookup.play(BrowseNode.Resume(book.id))
        lookup.play(BrowseNode.Book(book.id))
        lookup.play(BrowseNode.Chapter(book.id, 3))

        assertNull(lookup.play(BrowseNode.Shelf))
        assertEquals(listOf("resume b1", "resume b1", "from b1 p3"), played)
    }
}
