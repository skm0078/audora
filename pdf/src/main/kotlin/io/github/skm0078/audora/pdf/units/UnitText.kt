// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/units/UnitText.kt
// Purpose: A unit's text with its whitespace collapsed, and the bold terms in it as spans of that text.

package io.github.skm0078.audora.pdf.units

import io.github.skm0078.audora.core.model.TermSpan

/**
 * A unit's text is its raw slice of the reading text with every run of whitespace made one space, and none at either
 * end. Whitespace is Unicode's White_Space, the same on the JVM and on Android: tab, line breaks, vertical tab, form
 * feed, next line (U+0085) and every space and line or paragraph separator, the no-break space included (Android's
 * `\s` matched all of these, the JVM's only the ASCII ones, so the golden run now reads what the phone reads).
 * A dot leader or fill-in rule ([Leaders]) counts as whitespace too.
 *
 * Its bold terms (plan item 64) are the stretches of bold characters, joined across spaces between bold words,
 * without the punctuation at their ends ("Note:" gives "Note"); a stretch with no word of at least `minLetters`
 * letters (vowel signs and viramas count, digits do not) is none: "4.33", "z", "e c".
 */
internal object UnitText {
    /** The text of [raw] and its bold terms, [isBold] telling for each offset of [raw]. */
    fun of(raw: String, minLetters: Int, minLeaderRun: Int, isBold: (Int) -> Boolean): Pair<String, List<TermSpan>> {
        val leaders = Leaders.mask(raw, minLeaderRun)
        val text = StringBuilder(raw.length)
        val terms = ArrayList<TermSpan>()
        var termStart = -1
        var termEnd = -1
        var pendingSpace = false
        for (i in raw.indices) {
            val c = raw[i]
            if (isSpace(c) || leaders[i]) {
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

    /** Unicode White_Space: the ASCII controls, next line, and the space, line and paragraph separators. */
    private fun isSpace(c: Char): Boolean =
        c in ASCII_SPACES || c == NEXT_LINE || Character.getType(c).toByte() in SPACES

    private const val ASCII_SPACES = "\t\n\u000B\u000C\r"
    private const val NEXT_LINE = '\u0085'
    private val SPACES =
        setOf(Character.SPACE_SEPARATOR, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR)
    private val MARKS = setOf(Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK)
}
