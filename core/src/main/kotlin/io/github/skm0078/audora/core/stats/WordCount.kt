// File: core/src/main/kotlin/io/github/skm0078/audora/core/stats/WordCount.kt
// Purpose: How many words a sentence holds, for "words heard": runs of letters or digits split by spaces.

package io.github.skm0078.audora.core.stats

/**
 * English, Hindi and Bengali all put spaces between words, so a word is a space-separated piece holding at least one
 * letter or digit; a dash or a quote mark on its own is not one. Matras and virama are marks, not letters, but every
 * Devanagari or Bengali word has a letter too.
 */
object WordCount {
    fun of(text: String): Int = text.split(SPACES).count { piece -> piece.any(Char::isLetterOrDigit) }

    private val SPACES = Regex("\\s+")
}
