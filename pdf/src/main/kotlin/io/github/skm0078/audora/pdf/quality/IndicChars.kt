// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/quality/IndicChars.kt
// Purpose: Character classes of Devanagari and Bengali that the reorder pass and the quality scorer reason about.

package io.github.skm0078.audora.pdf.quality

/** How a Devanagari or Bengali character takes part in a syllable. Everything else is [NONE]. */
internal enum class IndicClass {
    CONSONANT,
    NUKTA,
    VIRAMA,

    /** A vowel sign drawn to the left of its consonant (ि ি ে ৈ), the one visual-order text puts first. */
    PREBASE_VOWEL,

    /** Any other dependent vowel sign (ा ी ু ো, Bengali au length mark): must follow a consonant. */
    VOWEL_SIGN,

    /** Candrabindu, anusvara, visarga: follow a vowel or a syllable. */
    MODIFIER,
    INDEPENDENT_VOWEL,

    /** Digits, danda, abbreviation signs and other letters of the two blocks that need no base. */
    OTHER,
    NONE,
}

/**
 * Unicode 15 Devanagari (U+0900–097F) and Bengali (U+0980–09FF) classes, by code point. Indic text here is always
 * in the BMP, so a Char is a code point.
 */
internal object IndicChars {
    private const val DEVANAGARI = 0x0900
    private const val BENGALI = 0x0980
    private const val BLOCK_END = 0x09FF

    const val DEVANAGARI_RA = 'र'
    const val BENGALI_RA = 'র'
    const val BENGALI_E = 'ে'
    const val BENGALI_AA = 'া'
    const val BENGALI_AU_LENGTH = 'ৗ'
    const val BENGALI_O = 'ো'
    const val BENGALI_AU = 'ৌ'

    fun isIndic(c: Char): Boolean = c.code in DEVANAGARI..BLOCK_END

    fun isIndicLetter(c: Char): Boolean = classOf(c).let { it != IndicClass.NONE && it != IndicClass.OTHER }

    fun classOf(c: Char): IndicClass = when (c.code) {
        in DEVANAGARI until BENGALI -> devanagari(c.code - DEVANAGARI)
        in BENGALI..BLOCK_END -> bengali(c.code - BENGALI)
        else -> IndicClass.NONE
    }

    fun isVirama(c: Char): Boolean = classOf(c) == IndicClass.VIRAMA

    fun isConsonant(c: Char): Boolean = classOf(c) == IndicClass.CONSONANT

    fun isRa(c: Char): Boolean = c == DEVANAGARI_RA || c == BENGALI_RA

    /** Offsets within the Devanagari block. */
    @Suppress("MagicNumber", "CyclomaticComplexMethod") // Unicode code chart positions, listed once here.
    private fun devanagari(o: Int): IndicClass = when (o) {
        in 0x01..0x03 -> IndicClass.MODIFIER
        in 0x04..0x14, in 0x60..0x61, in 0x72..0x77 -> IndicClass.INDEPENDENT_VOWEL
        in 0x15..0x39, in 0x58..0x5F, in 0x78..0x7F -> IndicClass.CONSONANT
        0x3C -> IndicClass.NUKTA
        0x4D -> IndicClass.VIRAMA
        0x3F, 0x4E -> IndicClass.PREBASE_VOWEL
        0x3A, 0x3B, in 0x3E..0x4C, 0x4F, in 0x55..0x57, in 0x62..0x63 -> IndicClass.VOWEL_SIGN
        else -> IndicClass.OTHER
    }

    /** Offsets within the Bengali block. Unassigned positions fall to OTHER; text never holds them. */
    @Suppress("MagicNumber", "CyclomaticComplexMethod")
    private fun bengali(o: Int): IndicClass = when (o) {
        in 0x01..0x03 -> IndicClass.MODIFIER
        in 0x05..0x14, in 0x60..0x61 -> IndicClass.INDEPENDENT_VOWEL
        in 0x15..0x39, in 0x5C..0x5F, 0x4E, 0x70, 0x71 -> IndicClass.CONSONANT
        0x3C -> IndicClass.NUKTA
        0x4D -> IndicClass.VIRAMA
        0x3F, 0x47, 0x48 -> IndicClass.PREBASE_VOWEL
        in 0x3E..0x44, 0x4B, 0x4C, 0x57, in 0x62..0x63 -> IndicClass.VOWEL_SIGN
        else -> IndicClass.OTHER
    }
}
