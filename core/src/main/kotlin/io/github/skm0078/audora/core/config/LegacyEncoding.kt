// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/LegacyEncoding.kt
// Purpose: The pre-Unicode Hindi and Bengali font encodings the legacy-font converter knows (P3-04, ADR-023).

package io.github.skm0078.audora.core.config

/**
 * How a pre-Unicode font lays Devanagari or Bengali glyphs on ASCII and Latin-1 codes. Text in these fonts
 * extracts as Latin gibberish ("izsl foKfIr" for "प्रेस विज्ञप्ति"); the converter maps it back.
 */
enum class LegacyEncoding {
    /** Kruti Dev 010 and the fonts that copy its layout (Kundli, MINNO-GOPI). Hindi. */
    KRUTIDEV,

    /** DevLys 010: Kruti Dev's layout, but ’ (0x92) is श where Kruti Dev has ष. Hindi. */
    DEVLYS,

    /** Chanakya (the Gazette of India's font): Kruti Dev's layout with a few codes of its own. Hindi. */
    CHANAKYA,

    /**
     * A Chanakya font used only for a glyph or two inside a Kruti Dev or DevLys word, for conjuncts the main font
     * draws badly (a Rajasthan Board paper: f = द्ध, V = ङ्क). Never named in [QualityConfig.legacyFonts]: the
     * converter gives a Chanakya run this encoding from its place in the word (ADR-032). Hindi.
     */
    CHANAKYA_SUPPLEMENT,

    /** Bijoy, the layout of SutonnyMJ and the other "MJ" fonts. Bengali. */
    BIJOY,

    /** A legacy font with no converter (Shusha): its pages go to OCR. */
    UNSUPPORTED,
}
