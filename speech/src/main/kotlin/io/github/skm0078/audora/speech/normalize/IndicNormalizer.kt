// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/normalize/IndicNormalizer.kt
// Purpose: Hindi and Bengali normalization for every engine: numbers in Devanagari, Bengali or ASCII digits as words.

package io.github.skm0078.audora.speech.normalize

import io.github.skm0078.audora.core.config.NormalizerConfig
import io.github.skm0078.audora.core.model.Lang

/**
 * Reads the numbers in a Hindi or Bengali part of a sentence as words of that language (P3-02, ADR-028). Voices
 * differ in what they do with digits: some read them in English, some skip them, and character-based voices only
 * know the letters they were trained on. Words work with every voice. Runs at render time on the part's text,
 * never on `ReadingUnit.text` (ADR-019).
 *
 * Digits may be Devanagari (०–९), Bengali (০–৯) or ASCII; the same rules as the English normalizer then apply, from
 * specific to general: rupee amounts (₹), percentages, ranges ("2019-20"), hyphenated codes digit by digit, then
 * plain numbers. Grouping follows the text (1,50,000 and 150,000 are both one lakh fifty thousand: Hindi and Bengali
 * count in lakh and crore); a decimal reads its digits one by one; leading zeros and runs longer than
 * [NormalizerConfig.maxCardinalDigits] read digit by digit; a bare year from 1100 to 1999 reads as "उन्नीस सौ
 * सैंतालीस". Clock times, dates, ordinals, units, abbreviations, initials and Roman numerals after a
 * numbering word come first ([IndicTextPack], plan items 3 and 57). A slash between
 * words reads "या"/"বা" and "&" reads "और"/"এবং" (RI-6). Text with none of these is
 * returned unchanged.
 */
class IndicNormalizer internal constructor(
    private val words: IndicNumberWords,
    private val config: NormalizerConfig,
    private val lang: Lang,
    private val table: IndicTextTable,
) : TextNormalizer {

    private val pack = IndicTextPack(words, config, table)

    override fun normalize(text: String, role: TextRole): String {
        val written = if (role == TextRole.EQUATION) {
            MathSymbols.speak(text, lang)
        } else {
            pack.romanNumerals(pack.abbreviations(text))
        }
        val spoken = speakSymbols(written)
        val ascii = asciiDigits(spoken)
        if (ascii.none(Char::isAsciiDigit)) return if (spoken == text) text else tidy(spoken)
        var result = pack.units(pack.ordinals(pack.dates(pack.times(ascii))))
        result = CURRENCY.replace(result) { match -> money(match.groupValues[1]) }
        result = PERCENT.replace(result) { match -> "${read(match.groupValues[1])} ${words.percent}" }
        result = RANGE.replace(result) { match ->
            val (from, to) = match.destructured
            "${read(from)} ${words.to} ${read(to)}"
        }
        result = HYPHENATED_CODE.replace(result) { match ->
            match.value.split('-').joinToString(", ", transform = words::digits)
        }
        result = PLAIN.replace(result) { match -> read(match.value) }
        return tidy(result)
    }

    private fun tidy(text: String): String = EXTRA_SPACES.replace(text, " ").trim()

    /**
     * "हाँ/नहीं" → "हाँ या नहीं", "60 किमी/घंटा" → "60 किमी प्रति घंटा", "&" → "और"; the text itself when it has
     * neither.
     */
    private fun speakSymbols(text: String): String {
        if ('/' !in text && '&' !in text) return text
        val rated = RATE_SLASH.replace(text, " ${words.per} ")
        val slashed = WORD_SLASH.replace(rated, " ${words.or} ")
        return AMPERSAND.replace(slashed, " ${words.and} ")
    }

    private fun read(token: String, allowYear: Boolean = true): String {
        val whole = token.substringBefore('.').replace(",", "")
        val fraction = token.substringAfter('.', missingDelimiterValue = "")
        val grouped = whole.length != token.substringBefore('.').length
        val wholeWords = when {
            whole.length > config.maxCardinalDigits -> words.digits(whole)
            whole.length > 1 && whole.startsWith('0') && fraction.isEmpty() -> words.digits(whole)
            allowYear && !grouped && fraction.isEmpty() && isYear(whole) -> words.year(whole.toInt())
            else -> words.cardinal(whole.toLong())
        }
        return if (fraction.isEmpty()) wholeWords else "$wholeWords ${words.point} ${words.digits(fraction)}"
    }

    /** "₹1,250.50" → "one thousand two hundred fifty rupees fifty paise", in the part's language. */
    private fun money(token: String): String {
        val whole = token.substringBefore('.').replace(",", "")
        val fraction = token.substringAfter('.', missingDelimiterValue = "")
        if (whole.length > config.maxCardinalDigits) return "${words.digits(whole)} ${words.currency}"
        val amount = whole.toLong()
        val rupees = "${words.cardinal(amount)} ${words.currency}"
        val paise = fraction.toLongOrNull()?.takeIf { fraction.length == PAISE_DIGITS && it > 0 }
        return when {
            paise != null -> "$rupees ${words.cardinal(paise)} ${words.paise}"
            fraction.isNotEmpty() && fraction.any { it != '0' } -> "${read(token, allowYear = false)} ${words.currency}"
            else -> rupees
        }
    }

    private fun isYear(digits: String): Boolean = digits.length == YEAR_DIGITS && digits.toInt() in config.yearRange

    companion object {
        private const val YEAR_DIGITS = 4
        private const val PAISE_DIGITS = 2
        private const val DEVANAGARI_ZERO = '०'
        private const val BENGALI_ZERO = '০'
        private const val DIGIT_COUNT = 10
        private const val NUMBER = NumberPatterns.NUMBER

        /** The rupee sign, "Rs.", "रु." or Bengali "টা." before an amount, read after it in the part's language. */
        private val CURRENCY = Regex("""(?:₹|Rs\.?|रु\.?|টা\.)\s?($NUMBER)(?![\w])""")
        private val PERCENT = Regex("""(?<![\w.])($NUMBER)\s?%""")

        /** Hyphen or dash, up to four digits a side: "10-12", "2019-20". Longer runs are codes. */
        private val RANGE = Regex("""(?<![\w.,-])(\d{1,4})\s?[-–—]\s?(\d{1,4})(?![\w-]|[.,]\d)""")
        private val HYPHENATED_CODE = Regex("""(?<![\w.,-])\d+(?:-\d+)+(?![\w-])""")
        private val PLAIN = Regex("""(?<![\w.])$NUMBER(?![\w])""")
        private val EXTRA_SPACES = Regex("""[ \t]{2,}""")

        /**
         * A slash after a short unit (up to 4 letters, "किमी") that follows a number, any script's digits: a rate, read
         * "per". A longer word ("10–12 देखें/पढ़ें") is not a unit, so its slash stays "or".
         */
        private val RATE_SLASH = Regex("""(?<=\p{Nd}\s?[\p{L}\p{M}]{1,4})/(?=[\p{L}\p{M}]{2})""")

        /** A slash between two words, Indic or Latin, two letters or more a side ("w/o" stays). */
        private val WORD_SLASH = Regex("""(?<=[\p{L}\p{M}]{2})/(?=[\p{L}\p{M}]{2})""")
        private val AMPERSAND = Regex("""\s*&\s*""")

        /** The normalizer for [lang]'s parts, or null for a language the Indic rules do not cover. */
        fun forLang(lang: Lang, config: NormalizerConfig = NormalizerConfig()): IndicNormalizer? = when (lang) {
            Lang.HI -> IndicNormalizer(IndicNumberWords.HINDI, config, Lang.HI, IndicTextTable.HINDI)
            Lang.BN -> IndicNormalizer(IndicNumberWords.BENGALI, config, Lang.BN, IndicTextTable.BENGALI)
            Lang.EN -> null
        }

        /** Devanagari and Bengali digits as ASCII digits, so one set of patterns reads all three. */
        private fun asciiDigits(text: String): String {
            if (text.none { isIndicDigit(it, DEVANAGARI_ZERO) || isIndicDigit(it, BENGALI_ZERO) }) return text
            return buildString(text.length) {
                for (c in text) {
                    append(
                        when {
                            isIndicDigit(c, DEVANAGARI_ZERO) -> '0' + (c - DEVANAGARI_ZERO)
                            isIndicDigit(c, BENGALI_ZERO) -> '0' + (c - BENGALI_ZERO)
                            else -> c
                        },
                    )
                }
            }
        }

        private fun isIndicDigit(c: Char, zero: Char): Boolean = c - zero in 0 until DIGIT_COUNT
    }
}
