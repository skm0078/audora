// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/units/UnitText.kt
// Purpose: A unit's text with its whitespace collapsed, and the bold terms in it as spans of that text.

package io.github.skm0078.audora.pdf.units

import io.github.skm0078.audora.core.model.TermSpan

/**
 * A unit's text is its raw slice of the reading text with every run of whitespace (as `\s` matches it: space, tab,
 * line breaks, vertical tab, form feed; not a no-break space) made one space, and none at either end. Its bold terms
 * (plan item 64) are the stretches of bold characters, joined across spaces between bold words, without the
 * punctuation at their ends ("Note:" gives "Note"); a stretch with no word of at least `minLetters` letters (vowel
 * signs and viramas count, digits do not) is none: "4.33", "z", "e c".
 */
internal object UnitText {
    /** The text of [raw] and its bold terms, [isBold] telling for each offset of [raw]. */
    fun of(raw: String, minLetters: Int, isBold: (Int) -> Boolean): Pair<String, List<TermSpan>> {
        val text = StringBuilder(raw.length)
        val terms = ArrayList<TermSpan>()
        var termStart = -1
        var termEnd = -1
        var pendingSpace = false
        for (i in raw.indices) {
            val c = raw[i]
            if (c in WHITESPACE) {
                pendingSpace = text.isNotEmpty()
                continue
            }
            if (pendingSpace) text.append(' ')
            pendingSpace = false
            if (isBold(i)) {
                if (termStart < 0) termStart = text.length
                termEnd = text.length + 1
            } else if (termStart >= 0) {
                term(text, termStart, termEnd, minLetters)?.let(terms::add)
                termStart = -1
            }
            text.append(c)
        }
        if (termStart >= 0) term(text, termStart, termEnd, minLetters)?.let(terms::add)
        return text.toString() to terms
    }

    /**
     * [start] until [end] of [text] without the punctuation and symbols at its ends, if what is left holds a word of
     * at least [minLetters] letters.
     */
    private fun term(text: CharSequence, start: Int, end: Int, minLetters: Int): TermSpan? {
        var from = start
        var to = end
        while (from < to && !isWordPart(text[from])) from++
        while (to > from && !isWordPart(text[to - 1])) to--
        return if (from < to) TermSpan(from, to).takeIf { hasWord(text, it, minLetters) } else null
    }

    /** [span] of [text] holds a run of at least [minLetters] letters and marks. */
    private fun hasWord(text: CharSequence, span: TermSpan, minLetters: Int): Boolean {
        var run = 0
        for (i in span.start until span.end) {
            run = if (text[i].isLetter() || isMark(text[i])) run + 1 else 0
            if (run >= minLetters) return true
        }
        return false
    }

    private fun isMark(c: Char): Boolean = Character.getType(c).toByte() in MARKS

    /** A letter, a digit, or a mark (a Devanagari or Bengali vowel sign, a virama) that belongs to one. */
    private fun isWordPart(c: Char): Boolean = c.isLetterOrDigit() || isMark(c)

    private const val WHITESPACE = " \t\n\u000B\u000C\r"
    private val MARKS = setOf(Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK)
}
