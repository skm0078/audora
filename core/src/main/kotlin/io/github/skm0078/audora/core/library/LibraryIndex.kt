// File: core/src/main/kotlin/io/github/skm0078/audora/core/library/LibraryIndex.kt
// Purpose: Each book's title and author folded once for search and sort (ADR-069), so typing in a library of
//          thousands of books on a low-end phone compares strings instead of normalising every title per keystroke.

package io.github.skm0078.audora.core.library

import java.text.Normalizer
import java.util.Locale

/** A book's [title] and [author] as search compares them; an unknown author is "". */
class SearchKey(val title: String, val author: String) {
    /** Every word in the title or the author. */
    fun has(words: List<String>): Boolean = words.all { it in title || it in author }

    companion object {
        private val LATIN_MARKS = Regex("(?<=\\p{IsLatin})\\p{M}+")

        fun of(book: Book) = SearchKey(fold(book.title), book.author?.let(::fold).orEmpty())

        /** Lower case, compatibility forms, Latin accents dropped; Indic signs kept, since they change the word. */
        fun fold(text: String): String {
            val decomposed = Normalizer.normalize(text, Normalizer.Form.NFKD)
            return Normalizer.normalize(LATIN_MARKS.replace(decomposed, ""), Normalizer.Form.NFC).lowercase(Locale.ROOT)
        }
    }
}

/**
 * The keys of a library's books. [of] reuses the previous index's key for every book whose title and author did not
 * change, so a new reading position or one edited book folds nothing else.
 */
class LibraryIndex private constructor(private val keys: Map<BookId, Entry>) {
    private class Entry(val title: String, val author: String?, val key: SearchKey)

    /** The key of [book]; folded here when the index was made before the book changed or arrived. */
    fun keyOf(book: Book): SearchKey {
        val entry = keys[book.id]
        return if (entry != null && entry.title == book.title && entry.author == book.author) {
            entry.key
        } else {
            SearchKey.of(book)
        }
    }

    val size: Int get() = keys.size

    companion object {
        val EMPTY = LibraryIndex(emptyMap())

        fun of(books: List<Book>, previous: LibraryIndex = EMPTY): LibraryIndex = LibraryIndex(
            books.associate { book -> book.id to Entry(book.title, book.author, previous.keyOf(book)) },
        )
    }
}

/** What the library holds to narrow by: the filter lists show only these, each with its number of books. */
data class LibraryFacets(
    val authors: List<Pair<String, Int>>,
    val formats: List<Pair<DocumentFormat, Int>>,
    val languages: List<Pair<String, Int>>,
) {
    companion object {
        val EMPTY = LibraryFacets(emptyList(), emptyList(), emptyList())

        /** Authors by name (folded), formats in their order, languages by tag; a value no book has is left out. */
        fun of(books: List<Book>, index: LibraryIndex = LibraryIndex.EMPTY): LibraryFacets {
            val authors = books.mapNotNull { book -> book.author?.let { it to index.keyOf(book).author } }
                .groupBy({ it.first }, { it.second })
                .map { (author, folded) -> Triple(author, folded.first(), folded.size) }
                .sortedBy { it.second }
                .map { it.first to it.third }
            val formats = books.mapNotNull { it.format }.groupingBy { it }.eachCount().toSortedMap().toList()
            val languages = books.mapNotNull { it.language }.groupingBy { it }.eachCount().toSortedMap().toList()
            return LibraryFacets(authors, formats, languages)
        }
    }
}
