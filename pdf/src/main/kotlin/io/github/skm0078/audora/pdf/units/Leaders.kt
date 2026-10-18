// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/units/Leaders.kt
// Purpose: Finds dot leaders and fill-in rules ("Introduction ........ 5", "Name: ______") and how units read them.

package io.github.skm0078.audora.pdf.units

/**
 * A leader is a run of one of full stop, middle dot, underscore or hyphen, at least `ReadingUnitConfig.minLeaderRun`
 * long, the dots perhaps one space apart (". . . . ."): a contents page's dots to the page number, a form's blank,
 * a ruled divider. Read aloud it is a string of "dot"s or a long silence, so a unit's text writes it as one
 * ellipsis ([readings]), which the voice reads as a short pause: "Introduction ........ 5" reads "Introduction … 5",
 * and a blank keeps its place in "भारत की राजधानी … है।" instead of making it a statement. A leader that ends the
 * text says nothing and is left out ("Name: ______" reads "Name:"). An ellipsis ("...", "…") and a sentence's own
 * full stop after one ("wait....") are shorter than a leader. A contents page's entries are cut apart in
 * [ContentsEntries].
 */
internal object Leaders {
    /** What a leader inside the text reads as: the voice's ellipsis pause (the pack's `ClauseSplitter`). */
    const val PAUSE = '\u2026'

    /**
     * What a table cell holding only a rule reads as ("-----" for nil): a dash, so the cell is not emptied. A cell of
     * dots or underscores (a blank to fill in, "______") is left out instead: it says nothing, where a rule says nil.
     */
    const val NIL = '\u2013'

    /** For each offset of [text], whether it belongs to a leader of at least [minRun] marks. */
    fun mask(text: CharSequence, minRun: Int): BooleanArray {
        val out = BooleanArray(text.length)
        var i = 0
        while (i < text.length) {
            val mark = text[i]
            if (mark !in MARKS) {
                i++
                continue
            }
            var end = i
            var count = 0
            while (end < text.length) {
                when {
                    text[end] == mark -> count++
                    mark in SPACED && text[end] == ' ' && text.getOrNull(end + 1) == mark -> Unit
                    else -> break
                }
                end++
            }
            if (count >= minRun) out.fill(true, i, end)
            i = end
        }
        return out
    }

    /**
     * How each leader of [text] (marked in [mask]) reads, keyed by its first offset: [PAUSE] when text follows it,
     * null (left out) when only spaces and leaders follow, and [alone] when [text] holds nothing else at all.
     */
    fun readings(text: CharSequence, mask: BooleanArray, alone: Char?): Map<Int, Char?> {
        val last = text.indices.lastOrNull { !mask[it] && !text[it].isWhitespace() }
        val out = HashMap<Int, Char?>()
        for (i in text.indices) {
            if (!mask[i] || (i > 0 && mask[i - 1])) continue
            out[i] = when {
                last == null -> alone?.takeIf { text[i] == RULE }
                i > last -> null
                else -> PAUSE
            }
        }
        return out
    }

    /** After a leader read as [PAUSE], these follow it with no space: "is ______." reads "is …." */
    const val TIGHT_AFTER = ".,;:!?)]}\u0964\u0965\"'\u2019\u201D\u00BB"

    private const val MARKS = "._-·"

    /** The only mark that draws a rule; the pack reads a lone dash cell as nil. */
    private const val RULE = '-'

    /** Marks a leader may space out one space apart. */
    private const val SPACED = ".·"
}
