// File: core/src/main/kotlin/io/github/skm0078/audora/core/text/TextCuts.kt
// Purpose: Cuts text only between whole characters: never inside a surrogate pair, a mark run or an Indic conjunct.

package io.github.skm0078.audora.core.text

import java.text.BreakIterator

/**
 * Where text may be cut. java.text's character boundaries keep surrogate pairs and combining marks with their base;
 * on top of that a cut never falls right after a virama followed by a letter or beside a zero-width joiner, which
 * would split a Hindi or Bengali conjunct (क्ष, র‍্যা) into a dangling half.
 */
object TextCuts {
    private const val DEVANAGARI_VIRAMA = '\u094D'
    private const val BENGALI_VIRAMA = '\u09CD'
    private const val ZWNJ = '\u200C'
    private const val ZWJ = '\u200D'

    /** The last allowed cut at or before [index] (0 if none). */
    fun boundaryAtOrBefore(text: String, index: Int): Int {
        if (index >= text.length) return text.length
        val characters = BreakIterator.getCharacterInstance().apply { setText(text) }
        var cut = if (characters.isBoundary(index.coerceAtLeast(0))) index else characters.preceding(index)
        while (cut > 0 && !isAllowed(text, cut)) cut = characters.preceding(cut)
        return cut.coerceAtLeast(0)
    }

    /** The first allowed cut at or after [index] ([text]'s length if none). */
    fun boundaryAtOrAfter(text: String, index: Int): Int {
        if (index <= 0 || index >= text.length) return index.coerceIn(0, text.length)
        val characters = BreakIterator.getCharacterInstance().apply { setText(text) }
        var cut = if (characters.isBoundary(index)) index else characters.following(index)
        while (cut in 1 until text.length && !isAllowed(text, cut)) cut = characters.following(cut)
        return if (cut == BreakIterator.DONE) text.length else cut
    }

    /** [text] cut to at most [maxChars] UTF-16 units at an allowed place. */
    fun take(text: String, maxChars: Int): String =
        if (text.length <= maxChars) text else text.substring(0, boundaryAtOrBefore(text, maxChars))

    private fun isAllowed(text: String, cut: Int): Boolean {
        if (cut <= 0 || cut >= text.length) return true
        val before = text[cut - 1]
        val after = text[cut]
        // A virama that ends a word (जगत्, before a space or punctuation) joins nothing, so a cut after it is fine.
        val joiningVirama = (before == DEVANAGARI_VIRAMA || before == BENGALI_VIRAMA) && after.isLetter()
        return !joiningVirama && before != ZWJ && before != ZWNJ && after != ZWJ && after != ZWNJ
    }
}
