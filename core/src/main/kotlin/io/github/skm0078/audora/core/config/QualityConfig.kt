// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/QualityConfig.kt
// Purpose: Tunables for the page quality scorer, the Indic reorder pass, the legacy-font converter (P3-04) and the
//          OCR-sample cross-check (P3-03).

package io.github.skm0078.audora.core.config

/**
 * Build Spec §5 steps 2 and 3b, ADR-011 §3, ADR-021. Rates are shares of a page's letters unless stated otherwise.
 *
 * Page routing:
 * @property minPageLetters a page with fewer letters than this has no usable text layer (a scan): OCR.
 * @property maxUnicodeMapErrorRate share of PDFium characters with a Unicode-map error above which the page goes
 *   to OCR (ADR-011: 2 %).
 * @property maxInvisibleShare share of characters drawn invisibly (render mode 3, a scanner's hidden OCR layer)
 *   above which the page goes to our own OCR.
 * @property maxGarbageRate share of letters that are junk (U+FFFD, private-use glyphs, ASCII glued inside an Indic
 *   word) above which the page goes to OCR.
 * @property maxInvalidIndicRate share of Indic letters in an impossible position (a vowel sign or virama with no
 *   consonant before it), counted after the reorder pass, above which the page goes to OCR.
 * @property legacyFonts font-name fragments of pre-Unicode Hindi and Bengali fonts (matched case-insensitively,
 *   subset prefix ignored, first match wins) and their encodings. Their glyphs extract as ASCII; the legacy-font
 *   converter (P3-04, ADR-023) maps every run in one of them back to Unicode, whatever the page's route.
 * @property minLegacyLatinShare a legacy-font run counts only when at least this share of its letters are Latin
 *   (ASCII, Latin-1 and the Windows-1252 extras such as š and Ÿ, where these fonts put their glyphs); a Unicode font
 *   whose name matches, such as ChanakyaUni, is read as is.
 * @property minLegacyShare the page is routed as a legacy-font page when legacy runs hold at least this share of its
 *   letters.
 * @property maxLegacyUnknownRate share of a legacy page's legacy-run letters that the converter has no mapping for
 *   (a font variant it does not know, or [LegacyEncoding.UNSUPPORTED]) above which the page goes to OCR.
 * @property maxSupplementLetters a Chanakya run of at most this many letters set right inside a Kruti Dev or DevLys
 *   word is a supplement font for a conjunct or two, with codes of its own ([LegacyEncoding.CHANAKYA_SUPPLEMENT]).
 *   The Gazette sets whole words in Chanakya, so its runs are never this short inside another font's word.
 *
 * Indic reorder (visual-order text layers):
 * @property minVisualOrderSigns a page decides its own order only with at least this many pre-base vowel signs
 *   (ि ি ে ৈ); a page with fewer follows the pages around it.
 * @property minWordInitialShare the page is in visual order when at least this share of its pre-base vowel signs
 *   start a word before a consonant. Unicode text never does that; visual-order text does it for a quarter or more.
 * @property minRephs a page decides whether its rephs (ra + virama) are in visual order only with at least this many.
 * @property minRephAtSyllableEndShare rephs are in visual order when at least this share of them has no consonant
 *   after it (drawn after their syllable, "ধমর্"). In Unicode text a reph is always followed by its consonant.
 *
 * OCR-sample cross-check (Indic documents):
 * @property minIndicShare a page is Indic when at least this share of its letters is Devanagari or Bengali.
 * @property minSampleLetters the sample page needs at least this many letters, so one page's error rate means
 *   something.
 * @property maxSampleCer the text layer is trusted when its character error rate against OCR of the same page is
 *   at most this (ADR-011: 5 %).
 * @property recheckEveryPages after the first check, one Indic page in this many is checked again.
 */
data class QualityConfig(
    val minPageLetters: Int = 40,
    val maxUnicodeMapErrorRate: Float = 0.02f,
    val maxInvisibleShare: Float = 0.5f,
    val maxGarbageRate: Float = 0.02f,
    val maxInvalidIndicRate: Float = 0.01f,
    val legacyFonts: Map<String, LegacyEncoding> = DEFAULT_LEGACY_FONTS,
    val minLegacyLatinShare: Float = 0.8f,
    val minLegacyShare: Float = 0.2f,
    val maxLegacyUnknownRate: Float = 0.02f,
    val maxSupplementLetters: Int = 2,
    val minVisualOrderSigns: Int = 8,
    val minWordInitialShare: Float = 0.1f,
    val minRephs: Int = 3,
    val minRephAtSyllableEndShare: Float = 0.25f,
    val minIndicShare: Float = 0.2f,
    val minSampleLetters: Int = 200,
    val maxSampleCer: Float = 0.05f,
    val recheckEveryPages: Int = 50,
) {
    init {
        val shares = listOf(
            maxUnicodeMapErrorRate, maxInvisibleShare, maxGarbageRate, maxInvalidIndicRate, minLegacyLatinShare,
            minLegacyShare, maxLegacyUnknownRate, minWordInitialShare, minRephAtSyllableEndShare, minIndicShare,
            maxSampleCer,
        )
        require(shares.all { it in 0f..1f }) { "rates and shares must be in 0..1" }
        require(minPageLetters >= 0) { "minPageLetters must be >= 0, was $minPageLetters" }
        require(maxSupplementLetters >= 0) { "maxSupplementLetters must be >= 0, was $maxSupplementLetters" }
        require(minVisualOrderSigns > 0 && minRephs > 0) { "minVisualOrderSigns and minRephs must be > 0" }
        require(minSampleLetters > 0 && recheckEveryPages > 0) { "minSampleLetters and recheckEveryPages must be > 0" }
    }

    companion object {
        /**
         * Kruti Dev-layout and Chanakya Hindi fonts and Bijoy-layout Bengali fonts, as named in the S2 corpus's
         * legacy-font files (test-pdfs/MANIFEST.md). Shusha has its own layout, which nothing converts yet.
         */
        val DEFAULT_LEGACY_FONTS: Map<String, LegacyEncoding> = linkedMapOf(
            "krutidev" to LegacyEncoding.KRUTIDEV,
            "kruti dev" to LegacyEncoding.KRUTIDEV,
            "devlys" to LegacyEncoding.DEVLYS,
            "kundli" to LegacyEncoding.KRUTIDEV,
            "minno-gopi" to LegacyEncoding.KRUTIDEV,
            "chanakya" to LegacyEncoding.CHANAKYA,
            "shusha" to LegacyEncoding.UNSUPPORTED,
            "sutonnymj" to LegacyEncoding.BIJOY,
            "sutonny mj" to LegacyEncoding.BIJOY,
            "sutonnyomj" to LegacyEncoding.BIJOY,
            "bijoy" to LegacyEncoding.BIJOY,
            "boishakhimj" to LegacyEncoding.BIJOY,
        )
    }
}
