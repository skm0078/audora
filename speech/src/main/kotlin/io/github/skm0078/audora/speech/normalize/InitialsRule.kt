// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/normalize/InitialsRule.kt
// Purpose: Initials ("A. P. J. Abdul Kalam", "U.S. Army") are read as letters, not as a run of sentence ends (item 57).

package io.github.skm0078.audora.speech.normalize

/**
 * The voices' phonemizer (espeak-ng) ends a clause at every full stop followed by a space, so "A. P. J. Abdul
 * Kalam" was read as four sentences: a falling tone and a pause after each letter, as long as the pause after a real
 * sentence (measured on Piper LJSpeech and Kitten nano). The dots of initials are dropped and the letters kept, which
 * the voices read as letter names with no pause:
 * - a run of two or more initials, spaced or not: "A. P. J." and "U.S." become "A P J" and "U S";
 * - one initial between a capitalised word and a capitalised name: "George W. Bush", "Doctor A. Kalam".
 *
 * The unit's sentence boundaries were decided upstream, so a dot is a sentence end only at the very end of the text,
 * where the last one is kept ([keepSentenceStop]). A lone initial at the start of the text stays ("A. Introduction"
 * may be a list label), and so does one after a numbering word ("Part C. Results", "Chapter I. Origins"), where the
 * stop is real.
 */
internal class InitialsRule : NormalizationRule {
    override fun apply(text: String, role: TextRole): String {
        val runs = RUN.replace(text) { run ->
            keepSentenceStop(text, run, LETTER.findAll(run.value).joinToString(" ") { it.value })
        }
        return SINGLE.replace(runs) { match ->
            val (word, letter) = match.destructured
            if (word in NUMBERING_WORDS) match.value else "$word $letter"
        }
    }

    private companion object {
        val LETTER = Regex("""\p{Lu}""")

        // Two or more capital letters, each with its full stop, nothing word-like on either side.
        val RUN = Regex("""(?<![\p{L}\p{N}.])\p{Lu}\.(?:\s?\p{Lu}\.)+(?![\p{L}\p{N}])""")

        // A capitalised word, one initial, then a capitalised word ("W." in "George W. Bush").
        val SINGLE = Regex("""(?<![\p{L}\p{N}.])(\p{Lu}\p{Ll}+) (\p{Lu})\.(?=\s+\p{Lu}\p{Ll})""")

        /** Words a letter or Roman numeral follows as a label; the full stop after that label is a real stop. */
        val NUMBERING_WORDS = setOf(
            "Act", "Annex", "Appendix", "Article", "Block", "Book", "Chapter", "Class", "Exhibit", "Figure", "Grade",
            "Group", "Lesson", "Level", "Model", "Option", "Paper", "Part", "Phase", "Plan", "Room", "Scene",
            "Schedule", "Section", "Stage", "Step", "Table", "Team", "Type", "Unit", "Vitamin", "Volume", "War",
        )
    }
}
