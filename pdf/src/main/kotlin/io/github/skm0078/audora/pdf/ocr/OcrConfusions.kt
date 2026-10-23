// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/ocr/OcrConfusions.kt
// Purpose: The character confusions Tesseract makes, per script, that OcrRepair may undo (RI-3).

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.model.Lang

/**
 * What Tesseract read → what was printed: letters of near-identical shape, seen in the P3-05 scans (tessdata_best,
 * 200 and 300 dpi) or well known for the script. Each pair is tried at every place it occurs in an unknown word, at
 * `OcrRepairConfig.confusionCost`. In Hindi and Bengali these are the only letter changes `OcrRepair` makes; vowel
 * signs may change freely.
 */
internal object OcrConfusions {
    private val LATIN = listOf(
        "rn" to "m", "m" to "rn", "cl" to "d", "vv" to "w", "li" to "h", "ii" to "u", "1" to "l", "l" to "i",
        "I" to "l", "0" to "o", "c" to "e", "e" to "c",
    )

    private val DEVANAGARI = symmetric(
        "घ" to "ध", "ध" to "भ", "ब" to "व", "म" to "भ", "प" to "ष", "थ" to "य", "ड" to "ङ", "ट" to "ठ", "ण" to "ए",
    ) + listOf("श्ष" to "क्ष", "्" to "्र")

    private val BENGALI = symmetric(
        "ব" to "ষ",
        "স" to "ম",
        "স" to "ন",
        "ঘ" to "য",
        "ত" to "ড",
        "ঢ" to "চ",
        "র" to "ব",
    )

    fun of(lang: Lang): List<Pair<String, String>> = when (lang) {
        Lang.EN -> LATIN
        Lang.HI -> DEVANAGARI
        Lang.BN -> BENGALI
    }

    /**
     * The sign a punctuation mark right after a word may really be: a colon for the visarga in both scripts, and in
     * Bengali "!", "|" or "৷" (U+09F7) for the vowel sign aa, drawn as the same bar as a danda.
     */
    fun signFor(lang: Lang, mark: Char): String? = when {
        lang == Lang.HI && mark == ':' -> "ः"
        lang == Lang.BN && mark == ':' -> "ঃ"
        lang == Lang.BN && mark in AA_BARS -> AA
        else -> null
    }

    /** The Bengali vowel sign aa, and what Tesseract reads its bar as. */
    const val AA = "\u09BE"
    const val AA_BARS = "!|\u09F7"

    private fun symmetric(vararg pairs: Pair<String, String>) = pairs.flatMap { listOf(it, it.second to it.first) }
}
