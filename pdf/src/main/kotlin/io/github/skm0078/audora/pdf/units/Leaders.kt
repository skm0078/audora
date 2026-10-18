// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/units/Leaders.kt
// Purpose: Finds dot leaders and fill-in rules ("Introduction ........ 5", "Name: ______") for units to leave out.

package io.github.skm0078.audora.pdf.units

/**
 * A leader is a run of one of full stop, middle dot, underscore or hyphen, at least `ReadingUnitConfig.minLeaderRun`
 * long, the dots perhaps one space apart (". . . . ."): a contents page's dots to the page number, a form's blank,
 * a ruled divider. Read aloud it is a string of "dot"s or a long silence and says nothing, so a unit's text treats it
 * as a space: "Introduction ........ 5" reads "Introduction 5", "Name: ______" reads "Name:". An ellipsis ("...",
 * "…") and a sentence's own full stop after one ("wait....") are shorter than a leader.
 */
internal object Leaders {
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

    private const val MARKS = "._-·"

    /** Marks a leader may space out one space apart. */
    private const val SPACED = ".·"
}
