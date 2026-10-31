// File: core/src/test/kotlin/io/github/skm0078/audora/core/library/ShelfSuggestionsTest.kt
// Purpose: "Shelves you might like": series by title, authors and languages, never one the listener already has.

package io.github.skm0078.audora.core.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShelfSuggestionsTest {
    private var next = 0

    private fun book(title: String, author: String? = null, language: String? = "en"): Book {
        next++
        return Book(BookId("b$next"), "content://b$next", title, 1, "f$next", 1, 0, null, author, language)
    }

    private fun suggest(
        books: List<Book>,
        collections: List<BookCollection> = emptyList(),
        members: Map<BookId, Set<String>> = emptyMap(),
    ) = ShelfSuggestions.of(books, collections, members) { "$it books" }

    @Test
    fun `a series is read from the number at the end of a title, in any script`() {
        assertEquals("Harry Potter", ShelfSuggestions.seriesOf("Harry Potter 2"))
        assertEquals("Foundation", ShelfSuggestions.seriesOf("Foundation (Book 1)"))
        assertEquals("Feluda", ShelfSuggestions.seriesOf("Feluda, Vol. ৩"))
        assertEquals("Class Notes", ShelfSuggestions.seriesOf("Class Notes: Part 4"))
        assertNull(ShelfSuggestions.seriesOf("1984"))
        assertNull(ShelfSuggestions.seriesOf("Gora"))
    }

    @Test
    fun `series, then authors, then languages, largest first`() {
        val books = listOf(
            book("Feluda 1", "Satyajit Ray", "bn"),
            book("Feluda 2", "Satyajit Ray", "bn"),
            book("Gora", "Rabindranath Tagore", "bn"),
            book("Chokher Bali", "rabindranath tagore ", "bn"),
            book("Ghare Baire", "Rabindranath Tagore", "bn"),
            book("Godaan", "Premchand", "hi"),
        )

        val names = suggest(books).map { it.kind to it.name }

        assertEquals(
            listOf(
                SuggestionKind.SERIES to "Feluda",
                SuggestionKind.AUTHOR to "Rabindranath Tagore",
                SuggestionKind.AUTHOR to "Satyajit Ray",
                SuggestionKind.LANGUAGE to "bn books",
            ),
            names,
        )
    }

    @Test
    fun `a language every book has is no shelf`() {
        val books = List(4) { book("Untitled ${'a' + it}") }

        assertEquals(emptyList<ShelfSuggestion>(), suggest(books).filter { it.kind == SuggestionKind.LANGUAGE })
    }

    @Test
    fun `one already made, by name or by holding the books, is not offered again`() {
        val books = listOf(book("Feluda 1", "Ray"), book("Feluda 2", "Ray"))
        val shelf = BookCollection("c", "feluda", 1)

        assertEquals(listOf("Ray"), suggest(books, listOf(shelf)).map { it.name })

        val held = books.associate { it.id to setOf("c2") }
        assertEquals(emptyList<ShelfSuggestion>(), suggest(books, members = held))
    }

    @Test
    fun `at most five are offered`() {
        val books = (1..7).flatMap { listOf(book("Series$it 1"), book("Series$it 2")) }

        assertEquals(ShelfSuggestions.MAX, suggest(books).size)
    }
}
