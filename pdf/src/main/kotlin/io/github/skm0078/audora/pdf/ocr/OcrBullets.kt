// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/ocr/OcrBullets.kt
// Purpose: Turns a bullet OCR misread as a letter, digit or sign ("o", "०", "*") back into a bullet (RI-10).

package io.github.skm0078.audora.pdf.ocr

/**
 * Tesseract has no bullet in its Hindi and Bengali models and often misses it in English: "•" and "○" come out as
 * "o", the Devanagari or Bengali zero ("०", "০", sometimes with a stray "." or "»"), "*", "+", "°" or "©". Read as
 * printed, a scanned list says "zero" before every item and never counts as a list. A line's first word that is one of
 * those, followed by a word starting with a letter, becomes "•", which the list finder takes and speech drops.
 */
internal object OcrBullets {
    const val BULLET = "•"
    private val MISREAD = Regex("""[*+o०০°©•◦]|[o०০][.»,]""")

    /** [words] (one OCR line) with a misread bullet at its start restored. */
    fun restore(words: List<OcrWord>): List<OcrWord> {
        val first = words.firstOrNull() ?: return words
        val next = words.getOrNull(1)?.text?.firstOrNull()
        return if (next != null && next.isLetter() && MISREAD.matches(first.text)) {
            listOf(OcrWord(BULLET, first.box, first.confidence)) + words.drop(1)
        } else {
            words
        }
    }
}
