// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/ocr/OcrAaBars.kt
// Purpose: A Bengali vowel sign aa OCR read as "!", "|" or "৷" mid-line is restored, so it is no false stop (RI-3).

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.config.OcrRepairConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.pdf.quality.WordModels

/**
 * On poor Bengali scans Tesseract reads the vowel sign aa (a bar, like a danda) as "!", "|" or "৷" glued to the word:
 * "কর! এবং" for "করা এবং". Read as printed, the voice stops mid-sentence and says the wrong word ("kor"). Unlike
 * `OcrRepair`'s other corrections this takes short and known words ("কর", "ব", "এক"): the bar, not a guess, says a
 * sign is there. It applies only to a word Tesseract was unsure of (`OcrRepairConfig.maxConfidence`) that runs on to
 * another Bengali word on the same line, where a danda is unlikely:
 *
 * - after a consonant, the bar is the sign when the word with it is common ("কর!" → "করা", "ব৷" → "বা"; "মানুষ|" stays,
 *   "মানুষা" is no word);
 * - after the sign itself, it is the same bar read twice ("ইচ্ছা! নিয়মিত" → "ইচ্ছা"), dropped;
 * - after the vowel sign e, "৷" (no sentence end as read) completes the sign o when that word is clearly commoner
 *   (`OcrRepairConfig.zipfMargin`: "কারে৷" → "কারো") and is otherwise the danda ("হবে৷ প্রাথমিক" → "হবে।");
 * - after any other sign, "!" or "|" after the sign e ("যাবে!", a sentence end as read), or at the line's end
 *   (`OcrDandas`), it is left alone.
 */
internal class OcrAaBars(private val config: OcrRepairConfig, private val models: WordModels) {
    /** [text] with a bar running on to [next] read as the sign; null when it is not one. */
    fun runningOn(text: String, confidence: Int?, next: String?): String? {
        val model = models.of(Lang.BN)?.takeIf { isCandidate(text, confidence, next) } ?: return null
        val core = text.dropLast(1)
        val last = core.last()
        val withSign = core + OcrConfusions.AA
        val withO = core.dropLast(1) + SIGN_O
        val zipf = { word: String -> model.words[word] ?: 0 }
        return when {
            last == OcrConfusions.AA[0] -> core
            isConsonant(last) && zipf(withSign) >= config.minZipf -> withSign
            last != SIGN_E || text.last() != CURRENCY_BAR -> null
            zipf(withO) >= config.minZipf && zipf(withO) - zipf(core) >= config.zipfMargin -> withO
            else -> core + DANDA
        }
    }

    /** An unsure Bengali word ending in a bar, with another Bengali word after it on the line. */
    private fun isCandidate(text: String, confidence: Int?, next: String?): Boolean {
        val bar = text.lastOrNull() ?: return false
        val last = text.getOrNull(text.length - 2)
        val runsOn = next?.firstOrNull()?.let { isBengali(it) && Character.isLetterOrDigit(it) } == true
        val unsure = confidence?.let { it < config.maxConfidence } == true
        return bar in OcrConfusions.AA_BARS && last != null && isBengali(last) && runsOn && unsure
    }

    private fun isBengali(c: Char): Boolean = Character.UnicodeScript.of(c.code) == Character.UnicodeScript.BENGALI

    /** A Bengali consonant: a letter that is not an independent vowel (অ…ঔ). */
    private fun isConsonant(c: Char): Boolean = isBengali(c) && Character.isLetter(c) && c !in VOWELS

    private companion object {
        val VOWELS = '\u0985'..'\u0994'
        const val SIGN_E = '\u09C7'
        const val SIGN_O = '\u09CB'
        const val CURRENCY_BAR = '\u09F7'
        const val DANDA = '\u0964'
    }
}
