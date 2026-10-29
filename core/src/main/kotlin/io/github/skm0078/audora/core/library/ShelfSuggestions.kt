// File: core/src/main/kotlin/io/github/skm0078/audora/core/library/ShelfSuggestions.kt
// Purpose: "Shelves you might like" (personal touch 17): collections worked out from the books' own details (series by
//          title, author, language), offered to make with one tap and never made by themselves.

package io.github.skm0078.audora.core.library

import java.util.Locale

/** What a suggestion groups by: shown to the listener as its reason. */
enum class SuggestionKind { SERIES, AUTHOR, LANGUAGE }

/** A collection to offer: [name] as it would be made, holding [books]. */
data class ShelfSuggestion(val kind: SuggestionKind, val name: String, val books: List<BookId>)

/**
 * Series first (the most particular), then authors, then languages; within each the largest first, at most [MAX].
 * A group is left out when a collection of that name exists, or one collection already holds all its books. A
 * language that every book has is no shelf at all. [languageName] names a language's shelf ("Bengali books").
 */
object ShelfSuggestions {
    const val MAX = 5
    private const val MIN_SERIES = 2
    private const val MIN_AUTHOR = 2
    private const val MIN_LANGUAGE = 3
    private const val MIN_SERIES_NAME = 3

    // "Name 2", "Name, Book 2", "Name: Part 2", "Name (Vol. 3)", "Name #4", in any script's digits.
    private val volume = Regex(
        """^(.*?)[\s,:(\-–#]*(?:book|vol\.?|volume|part|no\.?)?\s*#?\s*\(?\p{Nd}+\)?\s*$""",
        RegexOption.IGNORE_CASE,
    )

    fun of(
        books: List<Book>,
        collections: List<BookCollection>,
        members: Map<BookId, Set<String>>,
        languageName: (String) -> String,
    ): List<ShelfSuggestion> {
        val taken = collections.map { fold(it.name) }.toSet()
        val bySeries = groups(books, MIN_SERIES) { seriesOf(it.title) }
            .map { (name, group) -> ShelfSuggestion(SuggestionKind.SERIES, name, group) }
        val byAuthor = groups(books, MIN_AUTHOR) { it.author?.trim()?.takeIf(String::isNotEmpty) }
            .map { (name, group) -> ShelfSuggestion(SuggestionKind.AUTHOR, name, group) }
        val byLanguage = groups(books, MIN_LANGUAGE) { it.language }
            .filter { (_, group) -> group.size < books.size }
            .map { (tag, group) -> ShelfSuggestion(SuggestionKind.LANGUAGE, languageName(tag), group) }
        return (bySeries + byAuthor + byLanguage)
            .filter { fold(it.name) !in taken && !heldByOne(it.books, members) }
            .take(MAX)
    }

    /** The series a title belongs to: the title without its trailing number; null when it has none. */
    fun seriesOf(title: String): String? {
        val match = volume.matchEntire(title.trim()) ?: return null
        return match.groupValues[1].trim().takeIf { it.length >= MIN_SERIES_NAME }
    }

    /** Groups of at least [min] books by [key] (folded, so case and spacing do not split one), largest first. */
    private fun groups(books: List<Book>, min: Int, key: (Book) -> String?): List<Pair<String, List<BookId>>> =
        books.mapNotNull { book -> key(book)?.let { it to book } }
            .groupBy { fold(it.first) }
            .values
            .filter { it.size >= min }
            .map { group -> group.first().first to group.map { it.second.id } }
            .sortedByDescending { it.second.size }

    private fun heldByOne(books: List<BookId>, members: Map<BookId, Set<String>>): Boolean =
        books.map { members[it].orEmpty() }.reduce { shared, next -> shared intersect next }.isNotEmpty()

    private fun fold(name: String): String = name.trim().replace(SPACES, " ").lowercase(Locale.ROOT)

    private val SPACES = Regex("""\s+""")
}
