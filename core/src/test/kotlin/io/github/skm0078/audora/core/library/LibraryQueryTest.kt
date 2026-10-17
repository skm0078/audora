// File: core/src/test/kotlin/io/github/skm0078/audora/core/library/LibraryQueryTest.kt
// Purpose: Library search, sort and filter (plan item 22): words, accents, Indic titles, progress filters.

package io.github.skm0078.audora.core.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryQueryTest {
    private data class Item(val book: Book, val progress: BookProgress?)

    private fun item(title: String, added: Long, page: Int? = null, pages: Int = 10) = Item(
        Book(BookId(title), "content://$title", title, 1, "v1:$title", pages, added, null),
        page?.let { BookProgress(it, pages, 0) },
    )

    private val items = listOf(
        item("Moby Dick", added = 3, page = 2),
        item("Les Misérables", added = 1),
        item("गोदान", added = 4, page = 9),
        item("অপরাজিত", added = 2, page = 5),
    )

    private fun run(query: LibraryQuery) = query.apply(items, Item::book, Item::progress).map { it.book.title }

    @Test
    fun `search matches every word, any case, Latin accents ignored`() {
        val cases = listOf(
            "moby" to listOf("Moby Dick"),
            "dick MOBY" to listOf("Moby Dick"),
            "miserables" to listOf("Les Misérables"),
            "गोदा" to listOf("गोदान"),
            "অপরা" to listOf("অপরাজিত"),
            "moby whale" to emptyList(),
            "  " to items.map { it.book.title },
        )
        cases.forEach { (text, expected) -> assertEquals(text, expected, run(LibraryQuery(text))) }
    }

    @Test
    fun `filters by progress`() {
        assertEquals(listOf("Moby Dick", "অপরাজিত"), run(LibraryQuery(filter = LibraryFilter.IN_PROGRESS)))
        assertEquals(listOf("Les Misérables"), run(LibraryQuery(filter = LibraryFilter.NOT_STARTED)))
        assertEquals(listOf("गोदान"), run(LibraryQuery(filter = LibraryFilter.FINISHED)))
    }

    @Test
    fun `sorts by title, added and progress`() {
        assertEquals(items.map { it.book.title }, run(LibraryQuery()))
        assertEquals(
            listOf("गोदान", "Moby Dick", "অপরাজিত", "Les Misérables"),
            run(LibraryQuery(sort = LibrarySort.ADDED)),
        )
        assertEquals(
            listOf("गोदान", "অপরাজিত", "Moby Dick", "Les Misérables"),
            run(LibraryQuery(sort = LibrarySort.PROGRESS)),
        )
        assertEquals("Les Misérables", run(LibraryQuery(sort = LibrarySort.TITLE)).first())
    }
}
