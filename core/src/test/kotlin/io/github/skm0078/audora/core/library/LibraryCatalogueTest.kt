// File: core/src/test/kotlin/io/github/skm0078/audora/core/library/LibraryCatalogueTest.kt
// Purpose: The catalogue (ADR-069): search by author, narrowing by author, format, language and collection, sort by
//          author, the filter lists, folding each book once, and a 5,000-book library.

package io.github.skm0078.audora.core.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCatalogueTest {
    private fun book(
        title: String,
        author: String? = null,
        format: DocumentFormat? = DocumentFormat.PDF,
        language: String? = null,
    ) = Book(BookId(title), "content://$title", title, 1, "v1:$title", 10, 0, null, author, language, format)

    private val books = listOf(
        book("Gitanjali", "Rabindranath Tagore", DocumentFormat.EPUB, "bn"),
        book("Godaan", "Premchand", DocumentFormat.PDF, "hi"),
        book("Notes", author = null, DocumentFormat.TEXT, "en"),
        book("Gora", "Rabindranath Tagore", DocumentFormat.PDF, "bn"),
        book("Émile", "Jean-Jacques Rousseau", DocumentFormat.PDF, "en"),
    )

    private fun run(query: LibraryQuery, members: Map<BookId, Set<String>> = emptyMap()) =
        query.apply(books, { it }, { null }, LibraryIndex.of(books), members).map { it.title }

    @Test
    fun `search finds a book by its author too, every word in the title or the author`() {
        assertEquals(listOf("Gitanjali", "Gora"), run(LibraryQuery("tagore")))
        assertEquals(listOf("Gora"), run(LibraryQuery("gora rabindranath")))
        assertEquals(listOf("Émile"), run(LibraryQuery("rousseau emile")))
        assertEquals(emptyList<String>(), run(LibraryQuery("tagore premchand")))
    }

    @Test
    fun `narrows by author, format, language and collection, together`() {
        assertEquals(
            listOf("Gitanjali", "Gora"),
            run(LibraryQuery(narrow = LibraryNarrow(author = "Rabindranath Tagore"))),
        )
        assertEquals(
            listOf("Godaan", "Gora", "Émile"),
            run(LibraryQuery(narrow = LibraryNarrow(format = DocumentFormat.PDF))),
        )
        assertEquals(
            listOf("Gora"),
            run(LibraryQuery(narrow = LibraryNarrow(format = DocumentFormat.PDF, language = "bn"))),
        )
        val members = mapOf(BookId("Godaan") to setOf("school"), BookId("Notes") to setOf("school", "bed"))
        assertEquals(
            listOf("Godaan", "Notes"),
            run(LibraryQuery(narrow = LibraryNarrow(collection = "school")), members),
        )
        assertEquals(
            listOf("Notes"),
            run(LibraryQuery("notes", narrow = LibraryNarrow(collection = "school")), members),
        )
    }

    @Test
    fun `sort by author goes by author then title, books without one last`() {
        assertEquals(
            listOf("Émile", "Godaan", "Gitanjali", "Gora", "Notes"),
            run(LibraryQuery(sort = LibrarySort.AUTHOR)),
        )
    }

    @Test
    fun `the filter lists hold only what the library has, with counts`() {
        val facets = LibraryFacets.of(books)
        assertEquals(
            listOf("Jean-Jacques Rousseau" to 1, "Premchand" to 1, "Rabindranath Tagore" to 2),
            facets.authors,
        )
        assertEquals(
            listOf(DocumentFormat.PDF to 3, DocumentFormat.EPUB to 1, DocumentFormat.TEXT to 1),
            facets.formats,
        )
        assertEquals(listOf("bn" to 2, "en" to 2, "hi" to 1), facets.languages)
    }

    @Test
    fun `a filter on something the library no longer has is dropped`() {
        val facets = LibraryFacets.of(books)
        val narrow = LibraryNarrow("Nobody", DocumentFormat.RTF, "ta", collection = "gone")
        assertTrue(narrow.keptBy(facets, setOf("school")).isEmpty)
        val kept = LibraryNarrow("Premchand", DocumentFormat.PDF, "hi", collection = "school")
        assertEquals(kept, kept.keptBy(facets, setOf("school")))
        assertEquals(4, kept.count)
    }

    @Test
    fun `the index folds a book again only when its title or author changes`() {
        val first = LibraryIndex.of(books)
        val renamed = books.map { if (it.title == "Godaan") it.copy(author = "Munshi Premchand") else it }
        val second = LibraryIndex.of(renamed, first)
        assertSame(first.keyOf(books[0]), second.keyOf(renamed[0]))
        assertEquals("munshi premchand", second.keyOf(renamed[1]).author)
        assertEquals(books.size, second.size)
    }

    @Test
    fun `collection names are trimmed, spaces collapsed, cut, and never blank`() {
        assertEquals("For school", CollectionNames.clean("  For   school "))
        assertNull(CollectionNames.clean("   "))
        assertEquals(CollectionNames.MAX_LENGTH, CollectionNames.clean("a".repeat(100))?.length)
    }

    /**
     * A big library on a slow phone: search, filter and sort over 5,000 books stay correct, and a new reading
     * position (the library publishing again) folds no title again.
     */
    @Test
    fun `five thousand books search, narrow and sort, reusing every key`() {
        val many = (0 until LARGE_LIBRARY).map { n ->
            book("Book $n", author = "Author ${n % AUTHORS}", language = if (n % 2 == 0) "bn" else "hi")
        }
        val index = LibraryIndex.of(many)
        val again = LibraryIndex.of(many.map { it.copy(lastOpenedAtMillis = 1) }, index)
        many.forEach { assertSame(index.keyOf(it), again.keyOf(it)) }

        val query = LibraryQuery("author 7", LibrarySort.TITLE, narrow = LibraryNarrow(language = "hi"))
        val found = query.apply(many, { it }, { null }, again)
        val expected = many.filter { b ->
            b.language == "hi" &&
                listOf("author", "7").all { it in b.title.lowercase() || it in b.author!!.lowercase() }
        }
        assertEquals(expected.toSet(), found.toSet())
        assertTrue(found.isNotEmpty())
        assertEquals(found.map { it.title.lowercase() }.sorted(), found.map { it.title.lowercase() })
        assertEquals(AUTHORS, LibraryFacets.of(many, again).authors.size)
    }

    private companion object {
        const val LARGE_LIBRARY = 5_000
        const val AUTHORS = 100
    }
}
