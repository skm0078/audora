// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/ocr/OcrBullets.kt
// Purpose: Turns a bullet OCR misread as a letter, digit or sign ("o", "०", "*") back into a bullet (RI-10).

package io.github.skm0078.audora.pdf.ocr

/**
 * Tesseract has no bullet in its Hindi and Bengali models and often misses it in English: "•" and "○" come out as
 * "o", the Devanagari or Bengali zero ("०", "০", sometimes with a stray "." or "»"), "*", "+", "°" or "©". Read as
 * printed, a scanned list says "zero" before every item and never counts as a list. A line's first word that is one of
 * those, followed close by (`isBulletGap`) a word starting with a letter, becomes "•", which the list finder takes
 * and speech drops.
 */
internal object OcrBullets {
    const val BULLET = "•"
    private val MISREAD = Regex("""[*+o०০°©•◦]|[o०০][.»,]""")

    /** [words] (one OCR line) with a misread bullet at its start restored. */
    fun restore(isBulletGap: (OcrWord, OcrWord) -> Boolean, words: List<OcrWord>): List<OcrWord> {
        val first = words.getOrNull(0)
        val second = words.getOrNull(1)
        val restores = first != null && second != null && second.text.firstOrNull()?.isLetter() == true &&
            MISREAD.matches(first.text) && isBulletGap(first, second)
        return if (restores && first != null) {
            listOf(OcrWord(BULLET, first.box, first.confidence)) + words.drop(1)
        } else {
            words
        }
    }
}
