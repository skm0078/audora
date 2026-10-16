// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/golden/TextSimilarity.kt
// Purpose: The golden score: how close the spoken text is to the expected text, in order and as a bag of words.

package io.github.skm0078.audora.pdf.golden

import io.github.skm0078.audora.pdf.extract.characterErrorRate
import java.text.Normalizer

/**
 * Scores of one document. All three are in 0..1, higher is better.
 *
 * @property similarity 1 − character error rate (Levenshtein distance over the expected length, capped at 1). Order
 *   matters: interleaved columns or a misplaced footnote cost about twice their length. The headline metric.
 * @property wordRecall share of expected words that were spoken (multiset). Low = text lost or garbled.
 * @property wordPrecision share of spoken words that were expected (multiset). Low = clutter read aloud
 *   (headers, page numbers, figure labels) or garbled words.
 */
data class TextScore(val similarity: Double, val wordRecall: Double, val wordPrecision: Double)

object TextSimilarity {
    /**
     * What the comparison ignores, because a listener cannot hear it: Unicode normalization form, whitespace
     * amount, soft hyphens and zero-width characters (ZWSP, ZWNJ, ZWJ, BOM). Everything else counts, including
     * PDFium's line-end hyphen marker (U+0002 per char), which P2-03 must resolve.
     */
    fun normalize(text: String): String {
        val composed = Normalizer.normalize(text, Normalizer.Form.NFC)
        val visible = composed.filterNot { it in INAUDIBLE }
        return visible.split(WHITESPACE).filter { it.isNotEmpty() }.joinToString(" ")
    }

    fun score(expected: String, actual: String): TextScore {
        val e = normalize(expected)
        val a = normalize(actual)
        val similarity = (1.0 - characterErrorRate(e, a)).coerceIn(0.0, 1.0)
        val expectedWords = words(e)
        val actualWords = words(a)
        val common = commonCount(expectedWords, actualWords)
        return TextScore(
            similarity = similarity,
            wordRecall = ratio(common, expectedWords.size),
            wordPrecision = ratio(common, actualWords.size),
        )
    }

    private fun words(normalized: String): List<String> =
        if (normalized.isEmpty()) emptyList() else normalized.split(' ')

    private fun commonCount(expected: List<String>, actual: List<String>): Int {
        val remaining = HashMap<String, Int>()
        expected.forEach { remaining.merge(it, 1, Int::plus) }
        return actual.count { word ->
            val left = remaining[word] ?: 0
            if (left > 0) remaining[word] = left - 1
            left > 0
        }
    }

    /** Nothing expected counts as all found; nothing spoken counts as nothing misspoken. */
    private fun ratio(common: Int, total: Int): Double = if (total == 0) 1.0 else common.toDouble() / total

    private const val INAUDIBLE = "\u00AD\u200B\u200C\u200D\uFEFF"
    private val WHITESPACE = Regex("\\s+")
}
