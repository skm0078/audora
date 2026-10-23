// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/ocr/OcrDandasTest.kt
// Purpose: A danda OCR misread at a line's end ("৷", a lone "|") is restored; mid-line look-alikes are left alone.

package io.github.skm0078.audora.pdf.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrDandasTest {
    private val dandas = OcrDandas(minIndicWords = 2)

    private fun words(line: String) = line.split(' ').filter { it.isNotEmpty() }.map { OcrWord(it, null, CONFIDENT) }

    private fun restored(line: String, next: String = "") =
        dandas.restore(words(line), words(next)).joinToString(" ") { it.text }

    @Test
    fun `line-end danda misreads are restored, doubled vowel bars dropped`() {
        val cases = mapOf(
            // Bengali currency sign U+09F7, drawn as the same bar, at a line's end after a word: the sentence end.
            "অধিকার প্রত্যেকেরই রয়েছে৷" to "অধিকার প্রত্যেকেরই রয়েছে।",
            "নির্দোষ গণ্য হওয়ার অধিকার থাকবে৷" to "নির্দোষ গণ্য হওয়ার অধিকার থাকবে।",
            // A lone "|" ending a Bengali or Hindi line, even after a misread word ("না" read as "Al" or "=").
            "হতে বাধ্য করা যাবে Al |" to "হতে বাধ্য করা যাবে Al ।",
            "শাস্তিও দেওয়া চলবে = |" to "শাস্তিও দেওয়া চলবে = ।",
            "का अधिकार है |" to "का अधिकार है ।",
            "का अधिकार है ||" to "का अधिकार है ॥",
            // After the vowel sign aa, the bar was read twice: dropped, mid-line or at the end, never a danda.
            "সম্পত্তি থেকে বঞ্চিত করা৷" to "সম্পত্তি থেকে বঞ্চিত করা",
            "আচরণ করা৷ উচিৎ।" to "আচরণ করা উচিৎ।",
        )
        for ((line, expected) in cases) assertEquals(line, expected, restored(line))
    }

    @Test
    fun `look-alikes that are not a sentence end stay`() {
        val lines = listOf(
            // Mid-line, U+09F7 is the vowel sign aa (OcrRepair's job), not a danda.
            "সংরক্ষণ কর৷ অতি প্রয়োজনীয়;",
            // A word that is only the sign, or a sign after a digit, is not a word's end.
            "অধিকার ৷",
            "মূল্য ১৷",
            // A "|" ending an English line, or a line with too few Indic words, is a table rule or a pipe.
            "a | b | c |",
            "Total 12 |",
            "নাম |",
            // Already right.
            "যাবে না।",
        )
        for (line in lines) assertEquals(line, line, restored(line))
    }

    @Test
    fun `a line-end pipe is a separator between non-Indic words, and an opening bracket closed on the next line`() {
        // Digits or Latin on both sides: a table rule or a separator ("28400 | 988"), never a danda.
        assertEquals("प्रदीप कुमार मजदूर 28400 |", restored("प्रदीप कुमार मजदूर 28400 |", next = "988 2320"))
        assertEquals("রাত সময় ৮:১৫ JPG |", restored("রাত সময় ৮:১৫ JPG |", next = "PNG ফাইল"))
        // The next line closes a bracket it never opened, at its end: the pipe was "(" ("JPG (সাইজ ... মধ্যে)").
        assertEquals(
            "তথ্য আপনাকে আপলোড করতে হবে JPEG / JPG (",
            restored("তথ্য আপনাকে আপলোড করতে হবে JPEG / JPG |", next = "সাইজ 2০০ কেবির মধ্যে)"),
        )
        // Still a danda: a Bengali word after it, or a bracket the next line opens itself, or one closed mid-line.
        assertEquals("কাজ করা যাবে Al ।", restored("কাজ করা যাবে Al |", next = "ধারা ২১"))
        assertEquals("কাজ করা যাবে Al ।", restored("কাজ করা যাবে Al |", next = "ধারা (২১)"))
        assertEquals("उत्तर प्रदेश ।", restored("उत्तर प्रदेश |", next = "संख्या: NW) 7-062"))
    }

    @Test
    fun `an empty line is returned as is`() {
        assertEquals(emptyList<OcrWord>(), dandas.restore(emptyList()))
    }

    private companion object {
        const val CONFIDENT = 90
    }
}
