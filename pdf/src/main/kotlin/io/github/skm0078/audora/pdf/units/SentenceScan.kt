// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/units/SentenceScan.kt
// Purpose: Small scans of page text the sentence rules share: spaces, words and dot leaders around a break.

package io.github.skm0078.audora.pdf.units

/** Scans around a sentence break in [IndicSentenceRules]; offsets are into the text being split. */
internal object SentenceScan {
    /** Full stops and middle dots: the leaders a contents page sets before its page numbers. */
    const val LEADER_DOTS = ".\u00B7"

    /** How many leader dots end just before [at] (and the spaces before it), one space apart at most. */
    fun leaderDotsBefore(text: String, at: Int): Int {
        var i = at - 1
        while (i >= 0 && text[i].isWhitespace()) i--
        val mark = text.getOrNull(i)?.takeIf { it in LEADER_DOTS } ?: return 0
        var count = 0
        while (i >= 0 && inLeader(text, i, mark)) {
            if (text[i] == mark) count++
            i--
        }
        return count
    }

    /** [text] at [i] is the leader's [mark], or one space between two of them. */
    private fun inLeader(text: String, i: Int, mark: Char): Boolean =
        text[i] == mark || (text[i] == ' ' && text.getOrNull(i - 1) == mark)

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
}
