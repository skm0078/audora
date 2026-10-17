// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/ocr/OcrDandas.kt
// Purpose: Restores a danda OCR misread at a line's end ("৷", a lone "|"), so the sentence still ends there (RI-3).

package io.github.skm0078.audora.pdf.ocr

/**
 * Tesseract's Bengali model reads a line-final danda ("।") as "৷" (U+09F7, a currency sign drawn as the same bar) and,
 * on poor scans, as a lone "|" after a misread word ("যাবে Al |" for "যাবে না।"). Left as read, the sentence runs on
 * into the next one: neither ends a sentence, and the text layer's pipe repair (`UnicodeRepair.dandasFromPipes`)
 * needs an Indic letter right before the pipe. Only a line's last word gets a danda, where a sentence end is likely;
 * mid-line, "৷" is the vowel sign aa (`OcrRepair`'s job, run before this). Right after the vowel sign aa ("করা৷"), at
 * any place, "৷" is that sign's bar read twice and is dropped. A lone "|" counts only on a line holding at least
 * `minIndicWords` Hindi or Bengali words, so an English line or a table rule keeps it.
 */
internal class OcrDandas(private val minIndicWords: Int) {
    /** [words] (one OCR line) with doubled vowel-sign bars dropped and a misread danda at its end restored. */
    fun restore(words: List<OcrWord>): List<OcrWord> {
        val cleaned = words.map { word ->
            if (word.text.endsWith(DOUBLED_AA)) OcrWord(word.text.dropLast(1), word.box, word.confidence) else word
        }
        val last = cleaned.lastOrNull()
        val restored = last?.let { restored(it.text, cleaned) } ?: return cleaned
        return cleaned.dropLast(1) + OcrWord(restored, last.box, last.confidence)
    }

    /** The line's last word [text] with its danda restored; null when it is not a misread danda. */
    private fun restored(text: String, words: List<OcrWord>): String? = when {
        text.length > 1 && text.last() == CURRENCY_BAR && isBengaliSign(text[text.length - 2]) ->
            text.dropLast(1) + DANDA

        text == PIPE && words.count(::isIndic) >= minIndicWords -> DANDA.toString()

        text == DOUBLE_PIPE && words.count(::isIndic) >= minIndicWords -> DOUBLE_DANDA.toString()

        else -> null
    }

    /** A Bengali letter or vowel sign, not a digit or the bar itself. */
    private fun isBengaliSign(c: Char): Boolean =
        Character.UnicodeScript.of(c.code) == Character.UnicodeScript.BENGALI && c != CURRENCY_BAR &&
            (Character.isLetter(c) || Character.getType(c) in MARK_TYPES)

    private fun isIndic(word: OcrWord): Boolean = word.text.any { c ->
        Character.isLetter(c) && Character.UnicodeScript.of(c.code) in INDIC_SCRIPTS
    }

    private companion object {
        const val CURRENCY_BAR = '\u09F7'
        const val DOUBLED_AA = "\u09BE\u09F7"
        const val DANDA = '\u0964'
        const val DOUBLE_DANDA = '\u0965'
        const val PIPE = "|"
        const val DOUBLE_PIPE = "||"
        val INDIC_SCRIPTS = setOf(Character.UnicodeScript.BENGALI, Character.UnicodeScript.DEVANAGARI)
        val MARK_TYPES = setOf(
            Character.NON_SPACING_MARK.toInt(),
            Character.COMBINING_SPACING_MARK.toInt(),
        )
    }
}
