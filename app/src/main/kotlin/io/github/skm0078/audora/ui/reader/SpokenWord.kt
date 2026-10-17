// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/SpokenWord.kt
// Purpose: Which word of the spoken sentence is being said (plan item 14, version one): the sentence's time spread
//          evenly over its letters.

package io.github.skm0078.audora.ui.reader

/**
 * The characters of the word being said [positionMs] into a sentence of [text] that lasts [durationMs], or null
 * when the timing is unknown or nothing is being said yet.
 *
 * Version one gives every non-space character the same share of the time, so a long word holds the highlight
 * longer; it drifts by about ±150 ms where the voice pauses at punctuation. Words are what spaces separate, which
 * holds for English, Hindi and Bengali alike and never splits a conjunct.
 */
internal fun spokenWord(text: String, positionMs: Long, durationMs: Long?): IntRange? {
    val letters = text.count { !it.isWhitespace() }
    val duration = durationMs?.takeIf { it > 0 && positionMs >= 0 && letters > 0 } ?: return null
    val target = (positionMs.coerceAtMost(duration - 1) * letters / duration).toInt()
    val index = nthLetterIndex(text, target)
    var start = index
    while (start > 0 && !text[start - 1].isWhitespace()) start--
    var end = index
    while (end < text.lastIndex && !text[end + 1].isWhitespace()) end++
    return start..end
}

/** Index in [text] of its [n]th (0-based) non-space character; [n] is below the count of such characters. */
private fun nthLetterIndex(text: String, n: Int): Int {
    var seen = 0
    text.forEachIndexed { i, c ->
        if (!c.isWhitespace()) {
            if (seen == n) return i
            seen++
        }
    }
    error("text has only $seen non-space characters, asked for #$n")
}
