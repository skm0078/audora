// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/units/FoldedText.kt
// Purpose: Builds a unit's text with whitespace folded to one space and leaders read as a pause ([Leaders]).

package io.github.skm0078.audora.pdf.units

/**
 * A unit's text as it is built character by character: each run of whitespace is one space, none at the start, and
 * none between a leader's pause and the punctuation after it ("is …."). The caller says what each character is.
 */
internal class FoldedText(capacity: Int) {
    val text = StringBuilder(capacity)
    private var pendingSpace = false
    private var afterLeader = false

    /**
     * Adds [c] ([leader]: the pause a leader reads as; [space]: whitespace; null: nothing). Returns the offset [c]
     * lands at in [text], or -1 when it adds no character of its own.
     */
    fun add(c: Char?, leader: Boolean, space: Boolean): Int {
        if (c == null || space) {
            if (c != null) pendingSpace = text.isNotEmpty()
            return -1
        }
        if (pendingSpace && !(afterLeader && c in Leaders.TIGHT_AFTER)) text.append(' ')
        pendingSpace = false
        afterLeader = leader
        text.append(c)
        return text.length - 1
    }
}
