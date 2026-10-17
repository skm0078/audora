// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/listening/ListeningScore.kt
// Purpose: The listening score: how close the heard words are to a careful reader's script, plus its report files.

package io.github.skm0078.audora.playback.listening

import io.github.skm0078.audora.pdf.quality.CharacterErrorRate
import java.text.Normalizer
import java.util.Locale

/**
 * Scores of one document, each 0..1, higher is better.
 *
 * @property similarity 1 − character error rate between the heard words and the script's words (the app's own
 *   [CharacterErrorRate], as the OCR cross-check measures it). The headline.
 * @property wordRecall share of the script's words that were heard (multiset). Low = text skipped or garbled.
 * @property wordPrecision share of heard words that are in the script (multiset). Low = junk, markers, symbol soup
 *   or table cells read aloud.
 */
data class ListeningScore(val similarity: Double, val wordRecall: Double, val wordPrecision: Double) {
    companion object {
        /**
         * Only words count: the comparison is case-blind and ignores punctuation, Unicode form and spacing, which the
         * listener hears as pauses and intonation, not as words. Letters, combining marks (Indic vowel signs) and
         * digits are kept; zero-width joiners and soft hyphens are dropped without splitting the word.
         */
        fun words(text: String): List<String> {
            val composed = Normalizer.normalize(text, Normalizer.Form.NFC).lowercase(Locale.ROOT)
            val kept = StringBuilder(composed.length)
            composed.codePoints().forEach { cp ->
                val type = Character.getType(cp)
                when {
                    Character.isLetterOrDigit(cp) || type.isMark() -> kept.appendCodePoint(cp)

                    // ZWJ, ZWNJ, soft hyphen and the like are inaudible and sit inside words.
                    type == Character.FORMAT.toInt() -> Unit

                    else -> kept.append(' ')
                }
            }
            return kept.split(' ').filter { it.isNotEmpty() }
        }

        fun of(script: String, heard: String): ListeningScore {
            val expected = words(script)
            val actual = words(heard)
            val similarity = 1.0 - CharacterErrorRate.of(expected.joinToString(" "), actual.joinToString(" "))
            val common = commonCount(expected, actual)
            return ListeningScore(
                similarity = similarity.coerceIn(0.0, 1.0),
                wordRecall = ratio(common, expected.size),
                wordPrecision = ratio(common, actual.size),
            )
        }

        private fun Int.isMark(): Boolean = this == Character.NON_SPACING_MARK.toInt() ||
            this == Character.COMBINING_SPACING_MARK.toInt() || this == Character.ENCLOSING_MARK.toInt()

        private fun commonCount(expected: List<String>, actual: List<String>): Int {
            val remaining = HashMap<String, Int>()
            expected.forEach { remaining.merge(it, 1, Int::plus) }
            return actual.count { word ->
                val left = remaining[word] ?: 0
                if (left > 0) remaining[word] = left - 1
                left > 0
            }
        }

        private fun ratio(common: Int, total: Int): Double = if (total == 0) 1.0 else common.toDouble() / total
    }
}
