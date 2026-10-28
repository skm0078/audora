// File: core/src/main/kotlin/io/github/skm0078/audora/core/search/ActionSearch.kt
// Purpose: Finds the actions, places and settings a typed search names, from a small list kept in memory, so the
//          library's search also answers "audiobook" or "dark" (ADR 2026-10-28-action-search).

package io.github.skm0078.audora.core.search

import io.github.skm0078.audora.core.config.ActionSearchConfig
import io.github.skm0078.audora.core.library.SearchKey

/**
 * One thing search can offer. [key] tells the app what it is; [title] is its name; [terms] are other words it answers
 * to: the English name in another language, and the words people type for it ("dark" for the theme). [context] words
 * narrow a match but never make one alone: a book's title on "Save as an audiobook", so "gora audiobook" finds it and
 * "gora" alone leaves the books to the book grid. Folded once, when the entry is made, so a keystroke only compares
 * strings.
 */
class ActionEntry<K>(
    val key: K,
    title: String,
    terms: List<String> = emptyList(),
    context: List<String> = emptyList(),
) {
    internal val titleWords: List<String> = ActionSearch.words(title)
    internal val termWords: List<String> = terms.flatMap(ActionSearch::words)
    internal val contextWords: List<String> = context.flatMap(ActionSearch::words)
}

object ActionSearch {
    /** Anything but a letter, its marks (Indic vowel signs) or a digit splits words. */
    private val BREAK = Regex("[^\\p{L}\\p{M}\\p{N}]+")

    /** [text] folded as the library's search folds it, in words; what [find] compares. */
    fun words(text: String): List<String> = SearchKey.fold(text).split(BREAK).filter { it.isNotEmpty() }

    /**
     * The keys of the [entries] every typed word matches, best first; ties keep the list's order, which puts the more
     * common choice first. Nothing for a query shorter than [ActionSearchConfig.minQueryLength], so a first letter
     * does not fill the screen.
     */
    fun <K> find(
        query: String,
        entries: List<ActionEntry<K>>,
        config: ActionSearchConfig = ActionSearchConfig(),
    ): List<K> {
        val typed = words(query)
        if (typed.isEmpty() || SearchKey.fold(query).trim().length < config.minQueryLength) return emptyList()
        return entries.asSequence()
            .mapIndexedNotNull { order, entry -> score(typed, entry)?.let { Ranked(entry.key, it, order) } }
            .sortedWith(compareByDescending<Ranked<K>> { it.score }.thenBy { it.order })
            .take(config.maxResults)
            .map { it.key }
            .toList()
    }

    /**
     * Null unless every typed word matches and one of them names the entry itself, not only its context; a whole title
     * word counts most, a word inside a title word least.
     */
    private fun <K> score(typed: List<String>, entry: ActionEntry<K>): Int? {
        var total = 0
        var named = false
        for (word in typed) {
            val own = wordScore(word, entry)
            total += when {
                own > 0 -> own
                entry.contextWords.any { it.startsWith(word) } -> CONTEXT
                else -> return null
            }
            named = named || own > 0
        }
        return total.takeIf { named }
    }

    private fun <K> wordScore(word: String, entry: ActionEntry<K>): Int = when {
        entry.titleWords.any { it == word } -> TITLE_WORD
        entry.titleWords.any { it.startsWith(word) } -> TITLE_PREFIX
        entry.termWords.any { it == word } -> TERM_WORD
        entry.termWords.any { it.startsWith(word) } -> TERM_PREFIX
        word.length >= INSIDE_MIN && entry.titleWords.any { word in it } -> INSIDE
        else -> 0
    }

    private class Ranked<K>(val key: K, val score: Int, val order: Int)

    private const val TITLE_WORD = 5
    private const val TITLE_PREFIX = 4
    private const val TERM_WORD = 3
    private const val TERM_PREFIX = 2
    private const val INSIDE = 1
    private const val CONTEXT = 1

    /** "book" finds "audiobook", but "a" or "bo" inside a word would match nearly everything. */
    private const val INSIDE_MIN = 3
}
