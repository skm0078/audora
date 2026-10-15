// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/golden/TextSimilarityTest.kt
// Purpose: What the golden score folds away, and how order, loss and clutter move each metric.

package io.github.skm0078.audora.pdf.golden

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSimilarityTest {
    @Test
    fun `normalize folds only what a listener cannot hear`() {
        val cases = listOf(
            "  two\n\nlines\tand  tabs " to "two lines and tabs",
            "soft\u00ADhyphen" to "softhyphen",
            "zero\u200Bwidth\u200C\u200D\uFEFF" to "zerowidth",
            // Decomposed é (e + U+0301) composes; NFC keeps Indic text as written.
            "cafe\u0301" to "caf\u00E9",
            "मानव अधिकारों" to "मानव अधिकारों",
            // PDFium's line-end hyphen marker is NOT folded: resolving it is the pipeline's job (P2-03).
            "old\u0002fashioned" to "old\u0002fashioned",
            "Case Matters" to "Case Matters",
        )
        cases.forEach { (input, expected) -> assertEquals(input, expected, TextSimilarity.normalize(input)) }
    }

    @Test
    fun `identical text scores 1 on every metric`() {
        val score = TextSimilarity.score("The whale. The sea.", "The  whale.\nThe sea.")
        assertEquals(TextScore(1.0, 1.0, 1.0), score)
    }

    @Test
    fun `swapped columns lower similarity but keep every word`() {
        val left = "In the beginning God created the heaven and the earth."
        val right = "And the earth was without form, and void."
        val score = TextSimilarity.score("$left $right", "$right $left")
        assertTrue(score.similarity < 0.5)
        assertEquals(1.0, score.wordRecall, 0.0)
        assertEquals(1.0, score.wordPrecision, 0.0)
    }

    @Test
    fun `clutter read aloud lowers precision, lost text lowers recall`() {
        val body = "one two three four five six seven eight nine ten"
        val cluttered = TextSimilarity.score(body, "CHAPTER 5. CONNECTIVES 34 $body")
        assertEquals(1.0, cluttered.wordRecall, 0.0)
        assertEquals(10.0 / 14, cluttered.wordPrecision, 1e-9)

        val lost = TextSimilarity.score(body, "one two three four five")
        assertEquals(0.5, lost.wordRecall, 1e-9)
        assertEquals(1.0, lost.wordPrecision, 0.0)
    }

    @Test
    fun `repeated words are matched as a multiset`() {
        val score = TextSimilarity.score("the the cat", "the cat cat")
        assertEquals(2.0 / 3, score.wordRecall, 1e-9)
        assertEquals(2.0 / 3, score.wordPrecision, 1e-9)
    }

    @Test
    fun `similarity never goes below zero and empty sides are defined`() {
        assertEquals(0.0, TextSimilarity.score("ab", "completely different and much longer").similarity, 0.0)
        assertEquals(TextScore(1.0, 1.0, 1.0), TextSimilarity.score("", " \n "))
        val nothingSpoken = TextSimilarity.score("some words", "")
        assertEquals(TextScore(0.0, 0.0, 1.0), nothingSpoken)
    }
}
