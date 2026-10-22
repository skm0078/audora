// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/normalize/PronunciationFixer.kt
// Purpose: Applies the listener's pronunciation fixes (plan item 1) to a sentence before it is normalized and voiced.

package io.github.skm0078.audora.speech.normalize

import io.github.skm0078.audora.core.notes.PronunciationRule

/**
 * Matching ignores case; longer written forms go first, so "New York" wins over "York". A whole-word rule needs a
 * non-letter, non-digit (or the text's edge) on both sides, which works for Devanagari and Bengali as well as Latin,
 * since vowel signs count as letters. A rule matches by its text, so a Hindi rule changes Hindi words inside an
 * English sentence too. Runs before normalization, so a fix can be written the way a person would say it ("dee
 * em ess"), or as digits and symbols the normalizer then reads.
 */
class PronunciationFixer(rules: List<PronunciationRule>) {
    private val patterns: List<Pair<Regex, String>> = rules
        .sortedByDescending { it.written.length }
        .map { rule -> regexOf(rule) to rule.spoken }

    val isEmpty: Boolean get() = patterns.isEmpty()

    fun apply(text: String): String = patterns.fold(text) { current, (regex, spoken) ->
        regex.replace(current) { spoken }
    }

    private companion object {
        const val WORD_CHAR = "[\\p{L}\\p{M}\\p{N}]"

        fun regexOf(rule: PronunciationRule): Regex {
            val literal = Regex.escape(rule.written.trim())
            val pattern = if (rule.wholeWord) "(?<!$WORD_CHAR)$literal(?!$WORD_CHAR)" else literal
            return Regex(pattern, setOf(RegexOption.IGNORE_CASE))
        }
    }
}
