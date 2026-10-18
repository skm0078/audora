// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/normalize/IndicTextTable.kt
// Purpose: The Hindi and Bengali words the text pack says (plan item 3): months, ordinals, units, abbreviations.

package io.github.skm0078.audora.speech.normalize

import java.text.Normalizer

/**
 * One language's words for [IndicTextPack]. Every word is NFC, like the number words, so a voice gets one spelling.
 *
 * @property eras the era abbreviations, said only right after a year ("1947 ई.", "১৯৪৭ খ্রিঃ").
 * @property abbreviations written abbreviation → what is said; matched as whole words, longest first.
 * @property numberedAbbreviations abbreviations said this way only right before a number ("सं. 5" is "संख्या पाँच";
 *   elsewhere "सं." may be संपादक or संवत, so it is left as written).
 * @property letterNames how the language writes the English letter names that initials use ("एम", "এইচ"); a single
 *   akshara ("ए", "पी", "জে") or a Latin capital is an initial too ([initialsPattern]).
 * @property shortWords one-akshara words that end sentences ("है", "हाँ", "না"), so "है. हाँ." is not a run of
 *   initials; letters that are initials too ("के" for K, "জে" for J) are left out.
 * @property romanKeywords words a Roman numeral follows as a number ("अध्याय IV" is "अध्याय चार").
 * @property clock how clock times are said ([IndicClock]).
 */
@Suppress("LongParameterList") // A table of words: each list is its own kind, named at the two call sites.
internal class IndicTextTable private constructor(
    months: List<String>,
    val units: Map<String, String>,
    eras: Map<String, String>,
    abbreviations: Map<String, String>,
    numberedAbbreviations: Map<String, String>,
    letterNames: Set<String>,
    shortWords: Set<String>,
    romanKeywords: Set<String>,
    val clock: IndicClock,
    ordinalSuffixes: List<String>,
    private val ordinal: (n: Int, suffix: String, words: IndicNumberWords) -> String?,
    private val day: (n: Int, words: IndicNumberWords) -> String,
) {
    val months: List<String> = months.map(::nfc)
    val eras: Map<String, String> = eras.entries.associate { (k, v) -> nfc(k) to nfc(v) }
    val abbreviations: Map<String, String> = abbreviations.entries.associate { (k, v) -> nfc(k) to nfc(v) }

    val eraPattern = Regex("""(?<=\p{Nd})\s?(${alternatives(this.eras.keys)})(?![\p{L}\p{M}])""")
    val abbreviationPattern =
        Regex("""(?<![\p{L}\p{M}.])(?:${alternatives(this.abbreviations.keys)})(?![\p{L}\p{M}])""")
    val numberedAbbreviations: Map<String, String> =
        numberedAbbreviations.entries.associate { (k, v) -> nfc(k) to nfc(v) }
    val numberedAbbreviationPattern: Regex? = this.numberedAbbreviations.keys.takeIf { it.isNotEmpty() }?.let { keys ->
        Regex("""(?<![\p{L}\p{M}.])(${alternatives(keys)})(?![\p{L}\p{M}])""")
    }

    /**
     * Two or more initials, each with its full stop, spaced or not: a letter name, one Devanagari or Bengali akshara
     * (a letter and its vowel signs, no conjunct) that is not one of [shortWords], or a Latin capital. The voices end
     * a sentence at each of those stops (item 57).
     */
    val initialsPattern: Regex = run {
        val words = alternatives(shortWords.map(::nfc) + shortWords)
        val akshara = """(?!(?:$words)\.)(?=[\u0900-\u09FF])\p{L}\p{M}*"""
        val initial = """(?:${alternatives(letterNames.map(::nfc))}|$akshara|\p{Lu})\."""
        Regex("""(?<![\p{L}\p{M}\p{N}.])$initial(?:\s?$initial)+(?![\p{L}\p{M}\p{N}])""")
    }

    /** A keyword then a Roman numeral in one case: "अध्याय IV", "ভাগ ii". */
    val romanPattern = Regex(
        """(?<![\p{L}\p{M}])(${alternatives(romanKeywords.map(::nfc))})\s+([IVXLCDM]+|[ivxlcdm]+)(?![\w'’])""",
    )

    // Each ending as listed and in NFC, so text stored either way matches.
    val ordinalPattern = Regex(
        """(?<![\w.,])(\d{1,3})(${alternatives(
            (ordinalSuffixes + ordinalSuffixes.map(::nfc)).distinct(),
        )})(?![\p{L}\p{M}])""",
    )

    fun ordinal(n: Int, suffix: String, words: IndicNumberWords): String? = ordinal.invoke(n, suffix, words)?.let(::nfc)

    fun dayOf(n: Int, words: IndicNumberWords): String = nfc(day(n, words))

    companion object {
        private fun nfc(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)

        private fun alternatives(keys: Collection<String>): String =
            keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }

        /** Latin unit symbols both languages share; each table says them in its own words. */
        private val UNIT_SYMBOLS = listOf(
            "km/h", "kmph", "km²", "sq km", "m²", "°C", "°F", "km", "cm", "mm", "mg", "ml", "mL", "kg", "kW", "MW",
            "GB", "MB", "ha", "m", "g", "l", "L",
        )
        val UNIT_KEYS: String = alternatives(UNIT_SYMBOLS)

        private fun unitsOf(vararg spoken: String): Map<String, String> {
            require(spoken.size == UNIT_SYMBOLS.size) { "one word per unit symbol" }
            return UNIT_SYMBOLS.zip(spoken).toMap()
        }

        private const val HINDI_TENS_SUFFIX_FROM = 5
        private val HINDI_IRREGULAR = mapOf(1 to "पहल", 2 to "दूसर", 3 to "तीसर", 4 to "चौथ", 6 to "छठ")

        /** The ending's vowel picks the form: ा masculine, ी feminine, े oblique or plural. */
        private fun hindiOrdinal(n: Int, suffix: String, words: IndicNumberWords): String {
            val vowel = when {
                'ी' in suffix -> "ी"
                'े' in suffix -> "े"
                else -> "ा"
            }
            val stem = HINDI_IRREGULAR[n]
            return when {
                stem != null -> stem + vowel
                n < HINDI_TENS_SUFFIX_FROM -> words.cardinal(n.toLong())
                vowel == "ी" -> words.cardinal(n.toLong()) + "वीं"
                vowel == "े" -> words.cardinal(n.toLong()) + "वें"
                else -> words.cardinal(n.toLong()) + "वाँ"
            }
        }

        private val BENGALI_ORDINALS = (
            "প্রথম দ্বিতীয় তৃতীয় চতুর্থ পঞ্চম ষষ্ঠ সপ্তম অষ্টম নবম দশম " +
                "একাদশ দ্বাদশ ত্রয়োদশ চতুর্দশ পঞ্চদশ ষোড়শ সপ্তদশ অষ্টাদশ ঊনবিংশ বিংশ"
            ).split(' ')
        private val BENGALI_DAY_ENDINGS = setOf("লা", "রা", "ঠা", "ই", "শে")
        private val BENGALI_FIRST_DAYS = listOf("পয়লা", "দোসরা", "তেসরা", "চৌঠা")
        private const val BENGALI_E_DAYS_FROM = 19

        /** A date's day: পয়লা to চৌঠা, then পাঁচই to আঠারোই, then উনিশে to একত্রিশে (the cardinals end in শ). */
        private fun bengaliDay(n: Int, words: IndicNumberWords): String = when {
            n <= BENGALI_FIRST_DAYS.size -> BENGALI_FIRST_DAYS[n - 1]
            n < BENGALI_E_DAYS_FROM -> words.cardinal(n.toLong()) + "ই"
            else -> words.cardinal(n.toLong()) + "ে"
        }

        private fun bengaliOrdinal(n: Int, suffix: String, words: IndicNumberWords): String? = when {
            suffix in BENGALI_DAY_ENDINGS -> if (n in 1..MAX_DAY) bengaliDay(n, words) else null
            n in 1..BENGALI_ORDINALS.size -> BENGALI_ORDINALS[n - 1]
            n > BENGALI_ORDINALS.size -> words.cardinal(n.toLong()) + "তম"
            else -> null
        }

        private const val MAX_DAY = 31

        val HINDI = IndicTextTable(
            months = listOf(
                "जनवरी", "फ़रवरी", "मार्च", "अप्रैल", "मई", "जून",
                "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर",
            ),
            units = unitsOf(
                "किलोमीटर प्रति घंटा", "किलोमीटर प्रति घंटा", "वर्ग किलोमीटर", "वर्ग किलोमीटर", "वर्ग मीटर",
                "डिग्री सेल्सियस", "डिग्री फ़ारेनहाइट", "किलोमीटर", "सेंटीमीटर", "मिलीमीटर", "मिलीग्राम",
                "मिलीलीटर", "मिलीलीटर", "किलोग्राम", "किलोवाट", "मेगावाट", "गीगाबाइट", "मेगाबाइट", "हेक्टेयर",
                "मीटर", "ग्राम", "लीटर", "लीटर",
            ),
            eras = mapOf("ई.पू." to "ईसा पूर्व", "ई." to "ईसवी"),
            abbreviations = mapOf(
                "ई.पू." to "ईसा पूर्व", "श्री." to "श्री", "श्रीमती." to "श्रीमती",
                "डॉ." to "डॉक्टर", "प्रो." to "प्रोफ़ेसर", "स्व." to "स्वर्गीय", "कु." to "कुमारी",
                "पृ.सं." to "पृष्ठ संख्या", "पृ." to "पृष्ठ", "क्र." to "क्रमांक", "नं." to "नंबर", "दि." to "दिनांक",
                "वि.सं." to "विक्रम संवत", "वि. सं." to "विक्रम संवत",
                "कि.मी." to "किलोमीटर", "कि.ग्रा." to "किलोग्राम",
                "से.मी." to "सेंटीमीटर",
                "मि.मी." to "मिलीमीटर", "मि.ली." to "मिलीलीटर", "प्रा." to "प्राइवेट", "लि." to "लिमिटेड",
                "उ.प्र." to "उत्तर प्रदेश", "म.प्र." to "मध्य प्रदेश",
            ),
            numberedAbbreviations = mapOf("सं." to "संख्या"),
            letterNames = setOf(
                "एफ", "एफ़", "एच", "एल", "एम", "एन", "एस", "एक्स", "आर", "आई", "क्यू", "डब्ल्यू", "डबल्यू", "वाई",
                "ज़ेड", "जेड", "यू",
            ),
            shortWords = setOf(
                "है", "हैं", "था", "थी", "थे", "हो", "हूँ", "हूं", "ना", "न", "हाँ", "हां",
                "तो", "भी", "ही", "का", "की", "को", "से", "ने", "में",
            ),
            romanKeywords = setOf(
                "अध्याय", "भाग", "खंड", "खण्ड", "पाठ", "कक्षा", "अनुच्छेद", "सर्ग", "अंक", "परिशिष्ट", "पुस्तक",
            ),
            clock = IndicClock.HINDI,
            ordinalSuffixes = listOf(
                "वाँ", "वां", "वीं", "वें", "वी", "वे",
                "ला", "ली", "ले", "रा", "री", "रे",
                "था", "थी", "थे", "ठा", "ठी", "ठे",
            ),
            ordinal = ::hindiOrdinal,
            day = { n, words -> words.cardinal(n.toLong()) },
        )

        val BENGALI = IndicTextTable(
            months = listOf(
                "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
                "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর",
            ),
            units = unitsOf(
                "কিলোমিটার প্রতি ঘণ্টা", "কিলোমিটার প্রতি ঘণ্টা", "বর্গকিলোমিটার", "বর্গকিলোমিটার", "বর্গমিটার",
                "ডিগ্রি সেলসিয়াস", "ডিগ্রি ফারেনহাইট", "কিলোমিটার", "সেন্টিমিটার", "মিলিমিটার", "মিলিগ্রাম",
                "মিলিলিটার", "মিলিলিটার", "কিলোগ্রাম", "কিলোওয়াট", "মেগাওয়াট", "গিগাবাইট", "মেগাবাইট", "হেক্টর",
                "মিটার", "গ্রাম", "লিটার", "লিটার",
            ),
            eras = mapOf(
                "খ্রিঃপূঃ" to "খ্রিস্টপূর্ব",
                "খ্রি.পূ." to "খ্রিস্টপূর্ব",
                "খ্রিঃ" to "খ্রিস্টাব্দ",
                "খ্রি." to "খ্রিস্টাব্দ",
            ),
            abbreviations = mapOf(
                "খ্রিঃপূঃ" to "খ্রিস্টপূর্ব", "খ্রি.পূ." to "খ্রিস্টপূর্ব", "শ্রী." to "শ্রী", "ড." to "ডক্টর",
                "ডঃ" to "ডক্টর", "ডা." to "ডাক্তার", "অধ্যাঃ" to "অধ্যাপক", "পৃঃ" to "পৃষ্ঠা", "পৃ." to "পৃষ্ঠা",
                "কিমি" to "কিলোমিটার", "কি.মি." to "কিলোমিটার", "কিগ্রা" to "কিলোগ্রাম", "কি.গ্রা." to "কিলোগ্রাম",
                "সেমি" to "সেন্টিমিটার", "সে.মি." to "সেন্টিমিটার", "মিমি" to "মিলিমিটার", "নং" to "নম্বর",
                "বিঃদ্রঃ" to "বিশেষ দ্রষ্টব্য", "বি.দ্র." to "বিশেষ দ্রষ্টব্য", "তাং" to "তারিখ", "লিঃ" to "লিমিটেড",
                "প্রাঃ" to "প্রাইভেট", "মোঃ" to "মোহাম্মদ", "পঃবঃ" to "পশ্চিমবঙ্গ",
            ),
            numberedAbbreviations = emptyMap(),
            letterNames = setOf(
                "এফ", "এইচ", "এল", "এম", "এন", "এস", "এক্স", "আর", "আই", "ইউ", "কিউ", "ডব্লিউ", "ওয়াই", "জেড",
            ),
            shortWords = setOf("না", "কি", "কী", "যে", "সে", "তো", "তা", "যা", "হ্যাঁ", "নি"),
            romanKeywords = setOf(
                "অধ্যায়", "ভাগ", "খণ্ড", "খন্ড", "পর্ব", "পাঠ", "শ্রেণি", "শ্রেণী", "অনুচ্ছেদ", "পরিশিষ্ট",
            ),
            clock = IndicClock.BENGALI,
            // য় appears both ways in PDFs: one code point (U+09DF) and য plus nukta, which NFC gives.
            ordinalSuffixes = listOf(
                "তম", "র্থ", "ষ্ঠ", "শে", "লা", "রা", "ঠা",
                "ম", "\u09DF", "\u09AF\u09BC", "শ", "ই",
            ),
            ordinal = ::bengaliOrdinal,
            day = ::bengaliDay,
        )
    }
}
