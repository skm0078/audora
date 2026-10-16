// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/legacy/LegacyTables.kt
// Purpose: Code-to-Unicode tables of the Kruti Dev, Chanakya and Bijoy legacy font layouts (P3-04, ADR-023).

package io.github.skm0078.audora.pdf.legacy

import io.github.skm0078.audora.core.config.LegacyEncoding

/** What a legacy glyph does besides adding its letters. */
internal enum class GlyphRole {
    /** Letters, signs or punctuation, already in logical order. */
    TEXT,

    /** A vowel sign drawn before its consonant cluster (ि, ি, ে, ৈ): it follows the cluster in Unicode. */
    PREBASE,

    /** A reph (ra + virama) drawn after its syllable: it precedes the syllable's consonant cluster in Unicode. */
    REPH,

    /** Kruti Dev's ि with a reph on the same cluster ("okÆ\"kd" = "वार्षिक"). */
    PREBASE_REPH,
}

internal class LegacyGlyph(val text: String, val role: GlyphRole)

/**
 * One layout: longest-match lookup from the codes PDFium extracts (ASCII and Windows-1252 as Unicode, e.g. 0x92 as
 * ’) to Unicode, written in the order glyphs are drawn; [LegacyConverter] then moves pre-base signs and rephs.
 *
 * @property reph the reph's letters (र् or র্), inserted for [GlyphRole.PREBASE_REPH].
 * @property compositions pairs a converted run can hold side by side that Unicode writes as one letter: glyphs
 *   for the two halves of a vowel ("vk" = अ + ा) and the halves a moved pre-base sign leaves (ে + া = ো).
 * @property abbreviationSign what a Latin "0" right after a letter stands for: Hindi typists write "पी0एस0" for
 *   "पी॰एस॰" because the layout has no abbreviation glyph. Null where digits are the script's own (Bijoy).
 */
internal class LegacyTable(
    entries: Map<String, String>,
    roles: Map<String, GlyphRole>,
    val reph: String,
    val compositions: Map<String, String>,
    val abbreviationSign: Char? = null,
) {
    private val byFirst: Map<Char, List<Pair<String, LegacyGlyph>>> = entries.entries
        .map { (code, text) -> code to LegacyGlyph(text, roles[code] ?: GlyphRole.TEXT) }
        .groupBy { it.first[0] }
        .mapValues { (_, glyphs) -> glyphs.sortedByDescending { it.first.length } }

    /** The longest code at [at] that ends by [limit], and its glyph; null when no code starts there. */
    fun match(text: CharSequence, at: Int, limit: Int = text.length): Pair<Int, LegacyGlyph>? {
        val hit = byFirst[text[at]]?.firstOrNull { (code, _) ->
            at + code.length <= limit && text.regionMatches(at, code, 0, code.length)
        }
        return hit?.let { (code, glyph) -> code.length to glyph }
    }

    /** The glyph of [c] alone, or null when [c] has no code of its own. */
    fun glyphOf(c: Char): LegacyGlyph? = byFirst[c]?.firstOrNull { it.first.length == 1 }?.second

    companion object {
        fun of(encoding: LegacyEncoding): LegacyTable? = when (encoding) {
            LegacyEncoding.KRUTIDEV -> KRUTIDEV
            LegacyEncoding.DEVLYS -> DEVLYS
            LegacyEncoding.CHANAKYA -> CHANAKYA
            LegacyEncoding.CHANAKYA_SUPPLEMENT -> CHANAKYA_SUPPLEMENT
            LegacyEncoding.BIJOY -> BIJOY
            LegacyEncoding.UNSUPPORTED -> null
        }

        private const val ABBREVIATION = '॰'

        private val DEVANAGARI_COMPOSITIONS = mapOf(
            "अा" to "आ", "आे" to "ओ", "आै" to "औ", "अॉ" to "ऑ", "आॅ" to "ऑ", "एे" to "ऐ",
            "ाे" to "ो", "ाै" to "ौ", "ाॅ" to "ॉ", "््" to "्",
        )

        private val BENGALI_COMPOSITIONS = mapOf(
            "অা" to "আ",
            "\u09C7\u09BE" to "\u09CB",
            "\u09C7\u09D7" to "\u09CC",
            "্্" to "্",
        )

        private val KRUTIDEV_ROLES = mapOf(
            "f" to GlyphRole.PREBASE,
            "Z" to GlyphRole.REPH,
            "±" to GlyphRole.REPH,
            "Æ" to GlyphRole.PREBASE_REPH,
        )

        val KRUTIDEV: LegacyTable by lazy {
            LegacyTable(KrutiDevCodes.ENTRIES, KRUTIDEV_ROLES, "र्", DEVANAGARI_COMPOSITIONS, ABBREVIATION)
        }

        val DEVLYS: LegacyTable by lazy {
            val entries = KrutiDevCodes.ENTRIES + KrutiDevCodes.DEVLYS_OVERRIDES
            LegacyTable(entries, KRUTIDEV_ROLES, "र्", DEVANAGARI_COMPOSITIONS, ABBREVIATION)
        }

        val CHANAKYA: LegacyTable by lazy {
            val entries = KrutiDevCodes.ENTRIES - KrutiDevCodes.NOT_IN_CHANAKYA + KrutiDevCodes.CHANAKYA_OVERRIDES
            LegacyTable(entries, KRUTIDEV_ROLES, "र्", DEVANAGARI_COMPOSITIONS, ABBREVIATION)
        }

        /** Only the codes seen drawn (ADR-032); any other code is unknown and counts towards OCR. */
        val CHANAKYA_SUPPLEMENT: LegacyTable by lazy {
            LegacyTable(KrutiDevCodes.CHANAKYA_SUPPLEMENT, emptyMap(), "र्", DEVANAGARI_COMPOSITIONS, ABBREVIATION)
        }

        val BIJOY: LegacyTable by lazy {
            val roles = mapOf(
                "w" to GlyphRole.PREBASE,
                "‡" to GlyphRole.PREBASE,
                "†" to GlyphRole.PREBASE,
                "‰" to GlyphRole.PREBASE,
                "ˆ" to GlyphRole.PREBASE,
                "©" to GlyphRole.REPH,
            )
            LegacyTable(BijoyCodes.ENTRIES, roles, "র্", BENGALI_COMPOSITIONS)
        }
    }
}

/**
 * Kruti Dev 010. A capital or a symbol is usually the half form (virama) of the consonant whose full form adds "k"
 * (the ā stroke): "L" = स्, "Lk" = स. ASCII digits are Latin digits in this layout and pass through.
 */
private object KrutiDevCodes {
    val ENTRIES: Map<String, String> = mapOf(
        // Independent vowels.
        "v" to "अ", "vk" to "आ", "vks" to "ओ", "vkS" to "औ", "v‚" to "ऑ",
        "b" to "इ", "bZ" to "ई", "Ã" to "ई", "b±" to "ईं", "m" to "उ", "Å" to "ऊ", "_" to "ऋ", "," to "ए", ",s" to "ऐ",
        // Consonants: full and half forms.
        "d" to "क", "D" to "क्", "Dk" to "क", "[k" to "ख", "[" to "ख्", "x" to "ग", "X" to "ग्", "Xk" to "ग",
        "?k" to "घ", "?" to "घ्", "Ä" to "घ", "³" to "ङ",
        "p" to "च", "P" to "च्", "Pk" to "च", "N" to "छ", "t" to "ज", "T" to "ज्", "Tk" to "ज", ">" to "झ",
        "÷" to "झ्", "¥" to "ञ",
        "V" to "ट", "B" to "ठ", "M" to "ड", "<" to "ढ", ".k" to "ण", "." to "ण्",
        "r" to "त", "R" to "त्", "Rk" to "त", "Fk" to "थ", "F" to "थ्", "n" to "द", "/k" to "ध", "/" to "ध्",
        "èk" to "ध", "è" to "ध्", "Ë" to "ध्", "u" to "न", "U" to "न्", "Uk" to "न",
        "i" to "प", "I" to "प्", "Ik" to "प", "Q" to "फ", "¶" to "फ्", "c" to "ब", "C" to "ब्", "Ck" to "ब",
        "Hk" to "भ", "H" to "भ्", "e" to "म", "E" to "म्", "Ek" to "म",
        ";" to "य", "¸" to "य्", "j" to "र", "y" to "ल", "Y" to "ल्", "Yk" to "ल", "G" to "ळ", "o" to "व",
        "O" to "व्", "Ok" to "व",
        "'k" to "श", "'" to "श्", "”k" to "श", "”" to "श्", "“k" to "श", "“" to "श्", "Ük" to "श", "Ü" to "श्",
        "\"k" to "ष", "\"" to "ष्", "’k" to "ष", "’" to "ष्", "l" to "स", "L" to "स्", "Lk" to "स", "g" to "ह",
        // Conjuncts drawn as one glyph.
        "{k" to "क्ष", "{" to "क्ष्", "K" to "ज्ञ", "=" to "त्र", "«" to "त्र्", "J" to "श्र",
        "Ø" to "क्र", "ä" to "क्त", "—" to "कृ", "Ñ" to "कृ", "–" to "दृ", "Dr" to "क्त", "ô" to "क्क",
        "Ùk" to "त्त", "Ù" to "त्त्", "Ÿk" to "त्त", "Ÿ" to "त्त्", "é" to "न्न", "™" to "न्न्",
        "à" to "ह्न", "á" to "ह्य", "â" to "हृ", "ã" to "ह्म", "º" to "ह्", "ºz" to "ह्र",
        "í" to "द्द", "Ì" to "द्द", "|" to "द्य", "}" to "द्व", ")" to "द्ध", "æ" to "द्र",
        "ê" to "ट्ट", "Í" to "ट्ट", "ë" to "ट्ठ", "Î" to "ट्ठ", "ì" to "ड्ड", "Ï" to "ड्ड", "ï" to "ड्ढ", "Ô" to "ड्ढ",
        "Ö" to "झ्", "Ò" to "भ", "ç" to "प्र", "Á" to "प्र", "Ý" to "फ्र", "#" to "रु", ":" to "रू",
        "Nî" to "छ्य", "Vî" to "ट्य", "Bî" to "ठ्य", "Mî" to "ड्य", "<î" to "ढ्य", "î" to "्य", "Ó" to "्य",
        "Vª" to "ट्र", "Mª" to "ड्र", "<ª" to "ढ्र", "Nª" to "छ्र", "ª" to "्र", "z" to "्र",
        // Vowel signs and marks.
        "k" to "ा", "h" to "ी", "È" to "ीं", "q" to "ु", "w" to "ू", "`" to "ृ", "s" to "े", "S" to "ै",
        "ks" to "ो", "kS" to "ौ", "‚" to "ॉ", "W" to "ॅ", "a" to "ं", "¡" to "ँ", "%" to "ः", "~" to "्", "+" to "़",
        "f" to "ि", "Z" to "र्", "±" to "र्ं", "Æ" to "र्ि",
        // Devanagari digits and signs.
        "å" to "०", "ƒ" to "१", "„" to "२", "…" to "३", "†" to "४", "‡" to "५", "ˆ" to "६", "‰" to "७", "Š" to "८",
        "‹" to "९", "•" to "ऽ", "·" to "ऽ", "ñ" to "॰", "Œ" to "॰",
        // Punctuation: the layout gives several ASCII keys to letters, so these move.
        "A" to "।", "-" to ".", "\u0002" to ". ", "&" to "-", "@" to "/", "]" to ",", "(" to ";", "\\" to "?",
        "¼" to "(", "½" to ")", "¿" to "{", "À" to "}", "¾" to "=", "^" to "‘", "*" to "’", "Þ" to "“", "ß" to "”",
    )

    /** DevLys 010 draws श at 0x92 ("fo’ks\"k" = विशेष); Kruti Dev 010 draws ष there ("o’kZ" = वर्ष). */
    val DEVLYS_OVERRIDES: Map<String, String> = mapOf("’k" to "श", "’" to "श्")

    /** Codes Chanakya draws differently: "/" is the full ध there ("izkf/dkj" = प्राधिकार). */
    val NOT_IN_CHANAKYA: Set<String> = setOf("/k", "/")

    val CHANAKYA_OVERRIDES: Map<String, String> = mapOf(
        "/" to "ध",
        "ö" to ".",
        "¹" to "[",
        "º" to "]",
        "µ" to "—",
        "=k" to "त्र",
        "=" to "त्र्",
    )

    /** The Rajasthan Board paper's supplement Chanakya ("flf[f]dk;aZ" = सिद्धिकार्यं, "fu[E]ukaf[V]rs"kq" = निम्नाङ्कितेषु). */
    val CHANAKYA_SUPPLEMENT: Map<String, String> = mapOf("f" to "द्ध", "V" to "ङ्क")
}

/**
 * Bijoy (SutonnyMJ). Half forms (্-ending) and phala signs (্-starting) combine into conjuncts; ASCII digits are
 * Bengali digits in this layout.
 */
private object BijoyCodes {
    val ENTRIES: Map<String, String> = mapOf(
        // Independent vowels.
        "A" to "অ", "Av" to "আ", "B" to "ই", "C" to "ঈ", "D" to "উ", "E" to "ঊ", "F" to "ঋ", "G" to "এ", "H" to "ঐ",
        "I" to "ও", "J" to "ঔ",
        // Consonants.
        "K" to "ক", "L" to "খ", "M" to "গ", "N" to "ঘ", "O" to "ঙ", "P" to "চ", "Q" to "ছ", "R" to "জ", "S" to "ঝ",
        "T" to "ঞ", "U" to "ট", "V" to "ঠ", "W" to "ড", "X" to "ঢ", "Y" to "ণ", "Z" to "ত", "_" to "থ", "`" to "দ",
        "a" to "ধ", "b" to "ন", "c" to "প", "d" to "ফ", "e" to "ব", "f" to "ভ", "g" to "ম", "h" to "য", "i" to "র",
        "j" to "ল", "k" to "শ", "l" to "ষ", "m" to "স", "n" to "হ", "o" to "ড়", "p" to "ঢ়", "q" to "য়", "r" to "ৎ",
        // Half forms and phala (conjunct second-part) signs.
        "•" to "ঙ্", "”" to "চ্", "˜" to "দ্", "š" to "ন্", "›" to "ন্", "¤" to "ম্", "®" to "ষ্", "¯" to "স্",
        "&" to "্",
        "¨" to "্য", "Ö" to "্র", "ª" to "্র", "«" to "্র", "^" to "্ব", "¦" to "্ব", "¡" to "্ব", "Ÿ" to "্ব",
        "œ" to "্ন", "¥" to "্ম", "§" to "্ম", "¬" to "্ল", "­" to "্ল", "ø" to "্ল", "è" to "্ণ", "Í" to "্ত",
        "—" to "্ত", "¿" to "্ত্র", "’" to "্থ", "‘" to "্তু", "™" to "্দ", "‹" to "্ক", "Œ" to "্ক্র", "ú" to "্প",
        "¢" to "্ভ", "£" to "্ভ্র",
        // Conjuncts drawn as one glyph.
        "°" to "ক্ক", "±" to "ক্ট", "²" to "ক্ষ্ণ", "³" to "ক্ত", "´" to "ক্ম", "µ" to "ক্র", "¶" to "ক্ষ",
        "ÿ" to "ক্ষ", "·" to "ক্স", "¸" to "গু", "º" to "গ্দ", "»" to "গ্ধ", "¼" to "ঙ্ক", "½" to "ঙ্গ", "¾" to "জ্জ",
        "À" to "জ্ঝ",
        "Á" to "জ্ঞ", "¹" to "জ্ঞ", "Â" to "ঞ্চ", "Ã" to "ঞ্ছ", "Ä" to "ঞ্জ", "Å" to "ঞ্ঝ", "Æ" to "ট্ট", "Ç" to "ড্ড",
        "È" to "ণ্ট", "É" to "ণ্ঠ", "Ê" to "ণ্ড", "Ë" to "ত্ত", "Ì" to "ত্থ", "Î" to "ত্র", "Ï" to "দ্দ", "×" to "দ্ধ",
        "Ø" to "দ্ব", "Ù" to "দ্ম", "Ú" to "ন্ঠ", "Û" to "ন্ড", "Ü" to "ন্ধ", "Ý" to "ন্স", "Þ" to "প্ট", "ß" to "প্ত",
        "à" to "প্প", "á" to "প্স", "â" to "ব্জ", "ã" to "ব্দ", "ä" to "ব্ধ", "å" to "ভ্র", "ç" to "ম্ফ", "é" to "ল্ক",
        "ê" to "ল্গ", "ë" to "ল্ট", "ì" to "ল্ড", "í" to "ল্প", "î" to "ল্ফ", "ï" to "শু", "ð" to "শ্চ", "ñ" to "শ্ছ",
        "ò" to "ষ্ণ", "ó" to "ষ্ট", "ô" to "ষ্ঠ", "õ" to "ষ্ফ", "ö" to "স্খ", "÷" to "স্ট", "ù" to "স্ফ", "û" to "হু",
        "ü" to "হৃ", "ý" to "হ্ন", "þ" to "হ্ম", "i“" to "রু", "iæ" to "রু", "iƒ" to "রূ",
        // Vowel signs and marks.
        "v" to "া", "w" to "ি", "x" to "ী", "y" to "ু", "z" to "ু", "“" to "ু", "–" to "ু", "æ" to "ু", "~" to "ূ",
        "ƒ" to "ূ", "‚" to "ূ", "„" to "ৃ", "…" to "ৃ", "‡" to "ে", "†" to "ে", "‰" to "ৈ", "ˆ" to "ৈ", "Š" to "ৗ",
        "s" to "ং", "t" to "ঃ", "u" to "ঁ", "©" to "র্",
        // Digits are Bengali in this layout.
        "0" to "০", "1" to "১", "2" to "২", "3" to "৩", "4" to "৪", "5" to "৫", "6" to "৬", "7" to "৭", "8" to "৮",
        "9" to "৯",
        // Punctuation.
        "|" to "।", "Ô" to "‘", "Õ" to "’", "Ò" to "“", "Ó" to "”", "Ð" to "–", "Ñ" to "—",
    )
}
