// File: core/src/main/kotlin/io/github/skm0078/audora/core/library/LibraryQuery.kt
// Purpose: Library search, sort and filter (plan item 22, ADR-069): titles and authors, shelves by progress, and
//          narrowing by author, format, language or collection; pure so the library screen and tests share it.

package io.github.skm0078.audora.core.library

enum class LibrarySort {
    /** Most recently opened or added first: the library's own order. */
    RECENT,
    TITLE,

    /** By author, then title; books without an author last. */
    AUTHOR,

    /** Newest added first. */
    ADDED,

    /** Furthest read first; books never read last. */
    PROGRESS,
}

enum class LibraryFilter { ALL, IN_PROGRESS, NOT_STARTED, FINISHED }

/**
 * What the listener narrowed the library to besides its shelf; null is "any". [author] and [language] are the book's
 * own values as stored (the filter lists offer only those the library holds).
 */
data class LibraryNarrow(
    val author: String? = null,
    val format: DocumentFormat? = null,
    val language: String? = null,
    val collection: String? = null,
) {
    val isEmpty: Boolean get() = author == null && format == null && language == null && collection == null

    /** How many are set, for the Filters chip's count. */
    val count: Int get() = listOfNotNull(author, format, language, collection).size

    /**
     * Without any value the library no longer has (a deleted collection, an author edited away), so the list never
     * stays empty behind a filter the listener cannot see in the filter lists any more.
     */
    fun keptBy(facets: LibraryFacets, collections: Set<String>): LibraryNarrow = LibraryNarrow(
        author = author?.takeIf { name -> facets.authors.any { it.first == name } },
        format = format?.takeIf { kind -> facets.formats.any { it.first == kind } },
        language = language?.takeIf { tag -> facets.languages.any { it.first == tag } },
        collection = collection?.takeIf { it in collections },
    )
}

/**
 * @property text matched against titles and authors: every word must appear in one of them, ignoring case, width and
 *   accents on Latin letters (Devanagari and Bengali signs are kept, since they change the word).
 */
data class LibraryQuery(
    val text: String = "",
    val sort: LibrarySort = LibrarySort.RECENT,
    val filter: LibraryFilter = LibraryFilter.ALL,
    val narrow: LibraryNarrow = LibraryNarrow(),
) {
    val isDefault: Boolean
        get() = text.isBlank() && sort == LibrarySort.RECENT && filter == LibraryFilter.ALL && narrow.isEmpty

    /**
     * [items] (in the library's own recent-first order) searched, filtered and sorted; the sort is stable. [index]
     * holds each book's folded title and author, made once per change of the library instead of on every keystroke;
     * a book it lacks is folded here. [members] is each book's collections, for [LibraryNarrow.collection].
     */
    fun <T> apply(
        items: List<T>,
        book: (T) -> Book,
        progress: (T) -> BookProgress?,
        index: LibraryIndex = LibraryIndex.EMPTY,
        members: Map<BookId, Set<String>> = emptyMap(),
    ): List<T> {
        val words = SearchKey.fold(text).split(WHITESPACE).filter { it.isNotEmpty() }
        val kept = items.filter { item ->
            val it = book(item)
            matchesNarrow(it, members) && matches(progress(item)) && (words.isEmpty() || index.keyOf(it).has(words))
        }
        return when (sort) {
            LibrarySort.RECENT -> kept

            LibrarySort.TITLE -> kept.sortedBy { index.keyOf(book(it)).title }

            LibrarySort.AUTHOR -> kept.sortedWith(
                compareBy<T> { index.keyOf(book(it)).author.isEmpty() }
                    .thenBy { index.keyOf(book(it)).author }
                    .thenBy { index.keyOf(book(it)).title },
            )

            LibrarySort.ADDED -> kept.sortedByDescending { book(it).addedAtMillis }

            LibrarySort.PROGRESS -> kept.sortedByDescending { progress(it)?.fraction ?: -1f }
        }
    }

    private fun matchesNarrow(book: Book, members: Map<BookId, Set<String>>): Boolean =
        (narrow.author == null || book.author == narrow.author) &&
            (narrow.format == null || book.format == narrow.format) &&
            (narrow.language == null || book.language == narrow.language) &&
            (narrow.collection == null || members[book.id]?.contains(narrow.collection) == true)

    private fun matches(progress: BookProgress?): Boolean = when (filter) {
        LibraryFilter.ALL -> true
        LibraryFilter.NOT_STARTED -> progress == null
        LibraryFilter.FINISHED -> progress != null && progress.page == progress.pageCount - 1
        LibraryFilter.IN_PROGRESS -> progress != null && progress.page < progress.pageCount - 1
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
