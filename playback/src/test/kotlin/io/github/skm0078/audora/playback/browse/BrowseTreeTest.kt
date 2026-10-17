// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/browse/BrowseTreeTest.kt
// Purpose: The browsable library's tabs, recent books, Up next, the shelf, a book's chapters and spoken search.

package io.github.skm0078.audora.playback.browse

import io.github.skm0078.audora.core.config.BrowseConfig
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.upnext.UpNextQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseTreeTest {
    private val texts = object : BrowseTexts {
        override val recent = "Recent"
        override val upNext = "Up next"
        override val library = "Library"
        override val resume = "Resume"
        override val whereLeftOff = "From where you left off"

        override fun page(number: Int) = "Page $number"

        override fun chapter(number: Int) = "Chapter $number"
    }
    private val tree = BrowseTree(texts, BrowseConfig(recentCount = 2))
    private val gita = book("g", "Bhagavad Gita", openedAt = 300)
    private val physics = book("p", "Medical physics notes", openedAt = 100)
    private val atlas = book("a", "atlas of the body", openedAt = null, addedAt = 200)
    private val books = listOf(physics, gita, atlas)

    @Test
    fun `the root is three tabs`() {
        val root = tree.root()
        assertEquals(listOf(BrowseNode.Recent, BrowseNode.UpNext, BrowseNode.Shelf), root.map { it.node })
        assertTrue(root.all { it.browsable && !it.playable })
    }

    @Test
    fun `recent books are newest first, capped, and play on a tap`() {
        val recent = tree.recent(books)
        assertEquals(listOf(BrowseNode.Resume(gita.id), BrowseNode.Resume(atlas.id)), recent.map { it.node })
        assertTrue(recent.all { it.playable })
    }

    @Test
    fun `up next plays chapters from their page and books from where they were left`() {
        val queue = UpNextQueue()
            .add(physics.id, startPage = 4, startLabel = "Ultrasound")
            .add(gita.id)
            .add(BookId("gone"))
            .add(atlas.id, startPage = 9)

        val rows = tree.upNext(queue, books)

        assertEquals(
            listOf(BrowseNode.Chapter(physics.id, 4), BrowseNode.Resume(gita.id), BrowseNode.Chapter(atlas.id, 9)),
            rows.map { it.node },
        )
        assertEquals(listOf("Ultrasound", texts.whereLeftOff, "Page 10"), rows.map { it.subtitle })
    }

    @Test
    fun `the shelf is every book by title, each a folder`() {
        val shelf = tree.shelf(books)
        assertEquals(listOf(atlas.title, gita.title, physics.title), shelf.map { it.title })
        assertTrue(shelf.all { it.browsable && !it.playable })
    }

    @Test
    fun `a book holds resume then its chapters, numbered where untitled`() {
        val rows = tree.book(gita, listOf(BookChapter("Arjuna's grief", 0), BookChapter("", 14)))

        assertEquals(
            listOf(BrowseNode.Resume(gita.id), BrowseNode.Chapter(gita.id, 0), BrowseNode.Chapter(gita.id, 14)),
            rows.map { it.node },
        )
        assertEquals(listOf("Resume", "Arjuna's grief", "Chapter 2"), rows.map { it.title })
        assertEquals("Page 15", rows.last().subtitle)
    }

    @Test
    fun `a spoken search matches every word, titles starting with it first`() {
        // Older than the Bhagavad Gita, but its title starts with the words spoken.
        val more = book("m", "Gita notes", openedAt = 50)
        val all = books + more

        assertEquals(listOf(more.id, gita.id), tree.search("gita", all).map { (it.node as BrowseNode.Resume).id })
        assertEquals(listOf(gita.id), tree.search("  BHAGAVAD   gita ", all).map { (it.node as BrowseNode.Resume).id })
        assertEquals(emptyList<BrowseEntry>(), tree.search("chemistry", all))
    }

    @Test
    fun `a blank search is the most recent book`() {
        assertEquals(listOf(BrowseNode.Resume(gita.id)), tree.search(" ", books).map { it.node })
    }

    private fun book(id: String, title: String, openedAt: Long?, addedAt: Long = 0) = Book(
        id = BookId(id),
        documentUri = "content://$id",
        title = title,
        sizeBytes = 1,
        fingerprint = id,
        pageCount = 50,
        addedAtMillis = addedAt,
        lastOpenedAtMillis = openedAt,
    )
}
