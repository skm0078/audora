// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/search/SearchTerms.kt
// Purpose: Splits English, Hindi and Bengali text into search terms, the same way for the index and the query.

package io.github.skm0078.audora.playback.search

import io.github.skm0078.audora.core.config.SearchConfig
import java.text.Normalizer
import java.util.Locale

/**
 * SQLite's FTS4 tokenizers differ between Android versions and none knows Indic punctuation (a danda sticks to the
 * word before it), so terms are made here and the index's "simple" tokenizer only splits them on spaces.
 *
 * A term is a run of letters, digits and combining marks (Indic vowel signs, virama, nukta) in NFC, lower-cased.
 * Zero-width joiners are dropped, so a word typed with or without one matches; everything else separates terms.
 */
object SearchTerms {
    private const val ZWNJ = 0x200C
    private const val ZWJ = 0x200D

    fun of(text: String): List<String> {
        val normal = Normalizer.normalize(text, Normalizer.Form.NFC).lowercase(Locale.ROOT)
        val terms = mutableListOf<String>()
        val term = StringBuilder()
        var i = 0
        while (i < normal.length) {
            val cp = normal.codePointAt(i)
            when {
                cp == ZWNJ || cp == ZWJ -> Unit
                isTermChar(cp) -> term.appendCodePoint(cp)
                term.isNotEmpty() -> terms += term.toString().also { term.clear() }
            }
            i += Character.charCount(cp)
        }
        if (term.isNotEmpty()) terms += term.toString()
        return terms
    }

    /** What the index stores for [text]: its terms, space-separated. */
    fun indexed(text: String): String = of(text).joinToString(" ")

    /**
     * The FTS MATCH expression for what the listener typed: every word must be in the sentence, the last one as a
     * prefix (so results appear while typing). Null when there is too little to search for.
     */
    fun match(query: String, config: SearchConfig = SearchConfig()): String? {
        val terms = of(query).take(config.maxQueryTerms)
        if (terms.sumOf { it.length } < config.minQueryChars) return null
        // Terms hold only letters, digits and marks, lower-cased: never an FTS operator (those are upper-case).
        return terms.mapIndexed { i, term -> if (i == terms.lastIndex) "$term*" else term }.joinToString(" ")
    }

    private fun isTermChar(cp: Int): Boolean = Character.isLetterOrDigit(cp) ||
        when (Character.getType(cp).toByte()) {
            Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK -> true
            else -> false
        }
}
