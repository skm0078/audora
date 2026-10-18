// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/normalize/InitialsRule.kt
// Purpose: Initials ("A. P. J. Abdul Kalam", "U.S. Army") are read as letters, not as a run of sentence ends (item 57).

package io.github.skm0078.audora.speech.normalize

import io.github.skm0078.audora.core.config.NumberingWords
import io.github.skm0078.audora.core.config.SentenceOpeners

/**
 * The voices' phonemizer (espeak-ng) ends a clause at every full stop followed by a space, so "A. P. J. Abdul
 * Kalam" was read as four sentences: a falling tone and a pause after each letter, as long as the pause after a real
 * sentence (measured on Piper LJSpeech and Kitten nano). The dots of initials are dropped and the letters kept, which
 * the voices read as letter names with no pause:
 * - a run of two or more initials, spaced or not: "A. P. J." and "U.S." become "A P J" and "U S";
 * - one initial between a capitalised word and a capitalised name: "George W. Bush", "Doctor A. Kalam".
 *
 * The last stop stays where it is a sentence end: at the very end of the text ([keepSentenceStop]), and before a
 * word that opens sentences ([SentenceOpeners]: "in the U.S. The economy" → "in the U S. The economy"). A lone
 * initial is never "I" (a pronoun or a numeral: "Elizabeth I. The queen"), nor V or X after a monarch's name
 * ("Henry V."), and it stays at the start of the text ("A. Introduction" may be a list label) and after a numbering
 * word ([NumberingWords], shared with the sentence splitter: "Part C. Results"), where the stop is real.
 */
internal class InitialsRule : NormalizationRule {
    override fun apply(text: String, role: TextRole): String {
        val runs = RUN.replace(text) { run ->
            val letters = LETTER.findAll(run.value).joinToString(" ") { it.value }
            if (opensSentenceAfter(text, run)) "$letters." else keepSentenceStop(text, run, letters)
        }
        return SINGLE.replace(runs) { match ->
            val (word, letter, next) = match.destructured
            if (isInitial(word, letter) && next !in SentenceOpeners.ALL) "$word $letter" else match.value
        }
    }

    private fun isInitial(word: String, letter: String): Boolean = when {
        letter == PRONOUN -> false
        letter in REGNAL_NUMERALS && word in RomanNumerals.REGNAL_NAMES -> false
        else -> word !in NumberingWords.ALL
    }

    private fun opensSentenceAfter(text: String, match: MatchResult): Boolean =
        NEXT_WORD.find(text.substring(match.range.last + 1))?.groupValues?.get(1) in SentenceOpeners.ALL

    private companion object {
        const val PRONOUN = "I"
        val REGNAL_NUMERALS = setOf("V", "X")
        val LETTER = Regex("""\p{Lu}""")
        val NEXT_WORD = Regex("""^\s+(\p{Lu}\p{Ll}*)(?![\p{L}\p{N}])""")

        // Two or more capital letters, each with its full stop, nothing word-like on either side.
        val RUN = Regex("""(?<![\p{L}\p{N}.])\p{Lu}\.(?:\s?\p{Lu}\.)+(?![\p{L}\p{N}])""")

        // A capitalised word, one initial, then a capitalised word ("W." in "George W. Bush").
        val SINGLE = Regex("""(?<![\p{L}\p{N}.])(\p{Lu}\p{Ll}+) (\p{Lu})\.(?=\s+(\p{Lu}\p{Ll}+))""")
    }
}
