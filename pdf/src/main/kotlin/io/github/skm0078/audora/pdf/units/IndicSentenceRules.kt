// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/units/IndicSentenceRules.kt
// Purpose: Corrects BreakIterator's sentence breaks: danda, Indic and English abbreviations, verse numbers.

package io.github.skm0078.audora.pdf.units

import io.github.skm0078.audora.core.config.SentenceConfig
import java.text.Normalizer

/**
 * BreakIterator already ends a sentence at the danda on ICU (Android) and on the JDK, but both get Hindi and
 * Bengali wrong in the same few ways, and the JDK differs from ICU on closing quotes (P3-02, ADR-028):
 * 1. **Danda.** A break after every danda (। ॥) and the closing quotes and spaces after it, whatever the
 *    iterator did, unless a comma-like mark follows. Both platforms then agree.
 * 2. **Closing quotes** after a sentence end belong to that sentence: `है।" उसने` breaks after the quote, and a
 *    comma after a danda continues the sentence. The JDK breaks before both; ICU already does this.
 * 3. **Abbreviations and initials.** No break after "डॉ." or "ए." ([SentenceConfig]): Indic text ends sentences with
 *    the danda, so a full stop after one of these words is part of a name. When unsure, two sentences become one
 *    unit, which is cheaper than a name cut in half.
 *    The same holds in English after a title or Latin abbreviation ("Dr.", "e.g.", [SentenceConfig]) and after
 *    capital initials ("A. P. J.", "U.S."): both platforms break "Dr. | Smith" before a capital, ICU because
 *    Android does not turn on its abbreviation suppressions.
 * 4. **Verse numbers.** "॥ १ ॥" after a verse is read with that verse, not as a unit of its own.
 *
 * The rules move breaks only; the ranges still cover the text in order.
 */
internal class IndicSentenceRules(config: SentenceConfig) {
    private val nonBreaking: Set<String> =
        (config.abbreviations + config.initials + config.englishAbbreviations).mapTo(HashSet(), ::nfc)
    private val initials: Set<String> = config.initials.mapTo(HashSet(), ::nfc)
    private val references = config.referenceAbbreviations
    private val labels = config.letterLabels

    /** [breaks]: the iterator's sentence ends inside [text] (not 0 or its length). Returns the corrected ones. */
    fun refine(text: String, breaks: Collection<Int>): List<Int> {
        val corrected = sortedSetOf<Int>()
        breaks.mapTo(corrected) { moveAfterClosers(text, it) }
        dandaBreaks(text).forEach(corrected::add)
        corrected.removeAll {
            it <= 0 || it >= text.length || text[skipSpaces(text, it)] in CONTINUERS || followsNonBreakingWord(text, it)
        }
        removeVerseNumberBreaks(text, corrected)
        return corrected.toList()
    }

    private fun dandaBreaks(text: String): List<Int> {
        val breaks = ArrayList<Int>()
        var i = 0
        while (i < text.length) {
            if (text[i] !in DANDAS) {
                i++
                continue
            }
            var end = i
            while (end < text.length && (text[end] in DANDAS || text[end] in CLOSERS)) end++
            end = skipSpaces(text, end)
            if (end < text.length && text[end] !in CONTINUERS) breaks.add(end)
            i = end
        }
        return breaks
    }

    /** A break right after a sentence end and before closing quotes moves past the quotes and the spaces after them. */
    private fun moveAfterClosers(text: String, at: Int): Int {
        val afterSentenceEnd = at in 1 until text.length && text[at - 1] in TERMINATORS
        if (!afterSentenceEnd || text[at] !in CLOSERS) return at
        var end = at
        while (end < text.length && text[end] in CLOSERS) end++
        return if (end == text.length || text[end].isWhitespace()) skipSpaces(text, end) else at
    }

    /**
     * The text before [at] ends with "word." where the word is a listed abbreviation or initial, or a run of listed
     * initials ("ए.पी.जे.", "पी.एच.डी."); closing brackets and quotes may follow the full stop ("(कि.मी.) में").
     */
    private fun followsNonBreakingWord(text: String, at: Int): Boolean {
        var stop = at - 1
        while (stop >= 0 && (text[stop].isWhitespace() || text[stop] in CLOSERS)) stop--
        if (stop < 1 || text[stop] != '.') return false
        var start = stop
        while (start > 0 && !text[start - 1].isWhitespace() && text[start - 1] !in OPENERS) start--
        val word = text.substring(start, stop)
        val listed = nfc(word) in nonBreaking || isIndicInitials(word, initials)
        return listed || isInitial(text, start, word) || isReference(text, at, word) ||
            insideListedWord(text, start, at)
    }

    /**
     * [at] falls inside a dotted word that starts at [start]: ICU (Android) breaks "कि.|मी." and "उ.|प्र." at the
     * inner full stop, where the JDK does not, so with no space after [at] the word's letters, marks and full stops
     * are looked up whole, whatever punctuation is stuck to its end ("कि.मी.।", "उ.प्र.,", "कि.मी./घंटा"): a
     * listed word, or a run of initials ("ए.|पी.जे.").
     */
    private fun insideListedWord(text: String, start: Int, at: Int): Boolean {
        if (at >= text.length || text[at].isWhitespace()) return false
        var end = at
        while (end < text.length && inDottedWord(text[end])) end++
        val word = text.substring(start, end).trimEnd('.')
        return nfc(word) in nonBreaking || isIndicInitials(word, initials)
    }

    /** [word] points at a number or bracket that starts the next piece at [at]: "No. 5", "Fig. (a)". */
    private fun isReference(text: String, at: Int, word: String): Boolean {
        val next = text.getOrNull(skipSpaces(text, at))
        return word in references && next != null && (next.isDigit() || next == '(')
    }

    /**
     * [word] (before its full stop, starting at [start]) is capital initials: "U.S" or "A.P.J" always; a single
     * capital only after nothing, a full stop or comma ("Dr. A.", "J. A.", "Smith, J."), or a name ("Abbott B."), or
     * before another initial ("by S. K. Mukherjee"), so "says I.", "matrix Q.", "size N." and "Appendix A."
     * ([SentenceConfig.letterLabels]) still end a sentence. A lone initial after a small word before a surname
     * ("with K. Rao") still splits: it reads like "matrix Q. The". So does the last author's initial in a list that
     * writes initials without stops ("West J, Lundberg P. Rapid imaging"): there the stop ends the list.
     */
    private fun isInitial(text: String, start: Int, word: String): Boolean {
        if (!LATIN_INITIALS.matches(word) || word == "I") return false
        val before = wordBefore(text, start)
        val name = before != null && isName(before) && !followsUndottedAuthor(text, start, before)
        return word.length > 1 || before == null || before.last() in ".," || name ||
            initialFollows(text, start + word.length + 1)
    }

    private fun isName(word: String): Boolean =
        word.first().isUpperCase() && word.all { it.isLetter() || it in NAME_JOINERS } && word !in labels

    /** The whitespace-separated word before [start], or null at the start of the text or after an opening bracket. */
    private fun wordBefore(text: String, start: Int): String? {
        var stop = start
        while (stop > 0 && text[stop - 1].isWhitespace()) stop--
        var from = stop
        while (from > 0 && !text[from - 1].isWhitespace()) from--
        val afterOpener = stop == start && start > 0
        return text.substring(from, stop).takeUnless { it.isEmpty() || afterOpener }
    }

    /** Merges a piece made only of a verse number between dandas ("॥ १ ॥") into the piece before it. */
    private fun removeVerseNumberBreaks(text: String, breaks: MutableSet<Int>) {
        val ends = breaks.toList() + text.length
        for (k in 0 until ends.size - 1) {
            val piece = text.substring(ends[k], ends[k + 1]).trim()
            val isVerseNumber = piece.isNotEmpty() && piece.last() in DANDAS && piece.any(Char::isDigit) &&
                piece.all { it.isDigit() || it in DANDAS || it.isWhitespace() || it in VERSE_BRACKETS }
            if (isVerseNumber) breaks.remove(ends[k])
        }
    }

    private companion object {
        /** Danda and double danda (U+0964, U+0965), shared by Devanagari and Bengali. */
        const val DANDAS = "\u0964\u0965"

        /** Sentence ends after which closing quotes still belong to the sentence. */
        const val TERMINATORS = "$DANDAS.?!"

        /** Closing quotes and brackets; the straight quotes count only when a space follows them. */
        const val CLOSERS = "\"'\u2019\u201D\u00BB)]"

        /** Opening quotes and brackets before a word: "(डॉ." is still the word "डॉ". */
        const val OPENERS = "\"'\u2018\u201C\u00AB(["

        /** After a danda these continue the sentence, as in ICU's sentence rules. */
        const val CONTINUERS = ",;:\u2013\u2014"

        const val VERSE_BRACKETS = "()[]"

        /** "A", "U.S", "A.P.J": capital Latin letters, each but the last with its full stop. */
        val LATIN_INITIALS = Regex("(?:[A-Z]\\.)*[A-Z]")

        /** Inside a name before an initial: "O'Brien", "Smith-Jones". */
        const val NAME_JOINERS = "'\u2019-"

        /** Capital initials as the next word, with any closing punctuation stuck to them: "K.", "P.J.", "K.,". */
        val NEXT_INITIALS = Regex("(?:[A-Z]\\.)+[,;:)\\]\u2019\u201D]*")

        fun nfc(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)

        /** [word] is two or more of [initials] joined by full stops: "ए.पी.जे", "पी.एच.डी". */
        fun isIndicInitials(word: String, initials: Set<String>): Boolean {
            val parts = word.split('.')
            return parts.size > 1 && parts.all { nfc(it) in initials }
        }

        /** A letter, vowel sign or full stop: what a dotted word ("कि.मी.") is written with. */
        fun inDottedWord(c: Char): Boolean = c.isLetter() || isCombiningMark(c) || c == '.'

        fun skipSpaces(text: String, from: Int): Int {
            var i = from
            while (i < text.length && text[i].isWhitespace()) i++
            return i
        }

        /** Where the word at [from] ends: the next whitespace or the end of [text]. */
        fun tokenEnd(text: String, from: Int): Int {
            var i = from
            while (i < text.length && !text[i].isWhitespace()) i++
            return i
        }

        /** An author written with initials without stops, then a comma: "West J, ", "Persson AB, ". */
        val UNDOTTED_AUTHOR = Regex("""\p{Lu}[\p{L}'’-]+ \p{Lu}{1,4},\s*$""")

        /** How far before a name [followsUndottedAuthor] looks for the author before it. */
        const val AUTHOR_LOOKBACK = 40

        /** The name [name] that ends before [start] (and its spaces) comes after an [UNDOTTED_AUTHOR]. */
        fun followsUndottedAuthor(text: String, start: Int, name: String): Boolean {
            var stop = start
            while (stop > 0 && text[stop - 1].isWhitespace()) stop--
            val from = stop - name.length
            return UNDOTTED_AUTHOR.containsMatchIn(text.substring(maxOf(0, from - AUTHOR_LOOKBACK), maxOf(0, from)))
        }

        /** The word after the spaces at [from] is capital initials ([NEXT_INITIALS]). */
        fun initialFollows(text: String, from: Int): Boolean {
            val begin = skipSpaces(text, from)
            return NEXT_INITIALS.matches(text.substring(begin, tokenEnd(text, begin)))
        }
    }
}
