// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/normalize/NumberReader.kt
// Purpose: Reads one written numeric token (grouped, decimal, year-like, leading zeros) as English words.

package io.github.skm0078.audora.speech.normalize

import io.github.skm0078.audora.core.config.NormalizerConfig

/** Regex fragments for a written number, shared by every rule so they agree on what a number is. */
internal object NumberPatterns {
    /** 12,34,567: groups of two before the last three digits. Tried first; 1,000 is not Indian-only. */
    private const val INDIAN_GROUPED = """\d{1,2}(?:,\d{2})+,\d{3}"""
    private const val INTERNATIONAL_GROUPED = """\d{1,3}(?:,\d{3})+"""

    /** A whole or decimal number, grouped or not. A comma list like "1,2,3" is three numbers, not one. */
    const val NUMBER = """(?:$INDIAN_GROUPED|$INTERNATIONAL_GROUPED|\d+)(?:\.\d+)?"""

    val INDIAN_GROUPING = Regex("""^$INDIAN_GROUPED$""")

    /**
     * The second year of a span written short with a leading zero, "2005-06", given its century ("2006"), so it is
     * not read digit by digit as a code; any other range end as written ("2019-20" stays "twenty").
     */
    fun rangeEnd(from: String, to: String): String {
        val shortYear = from.length == YEAR_DIGITS && to.length == SHORT_YEAR_DIGITS && to.startsWith('0')
        return if (shortYear) from.take(2) + to else to
    }

    private const val YEAR_DIGITS = 4
    private const val SHORT_YEAR_DIGITS = 2
}

/**
 * Turns a token matched by [NumberPatterns.NUMBER] into words. Grouping decides the number system (1,50,000 is
 * Indian); an ungrouped token uses the caller's [NumberSystem]. Leading zeros and runs longer than
 * [NormalizerConfig.maxCardinalDigits] are read digit by digit, as people read codes and phone numbers.
 */
internal class NumberReader(private val config: NormalizerConfig) {

    /**
     * @param allowYear read a bare four-digit integer in [NormalizerConfig.yearRange] as a year. Off for amounts and
     *   measurements: "1500 km" is "one thousand five hundred kilometres".
     */
    fun read(token: String, allowYear: Boolean = true, ungrouped: NumberSystem = NumberSystem.INTERNATIONAL): String {
        val whole = token.substringBefore('.')
        val fraction = token.substringAfter('.', missingDelimiterValue = "")
        val digits = whole.replace(",", "")
        val grouped = digits.length != whole.length
        val system = when {
            !grouped -> ungrouped
            NumberPatterns.INDIAN_GROUPING.matches(whole) -> NumberSystem.INDIAN
            else -> NumberSystem.INTERNATIONAL
        }
        val wholeWords = when {
            digits.length > config.maxCardinalDigits -> NumberWords.digits(digits)
            digits.length > 1 && digits.startsWith('0') && fraction.isEmpty() -> NumberWords.digits(digits)
            allowYear && !grouped && fraction.isEmpty() && isYear(digits) -> NumberWords.year(digits.toInt())
            else -> NumberWords.cardinal(digits.toLong(), system)
        }
        return if (fraction.isEmpty()) wholeWords else "$wholeWords point ${NumberWords.digits(fraction)}"
    }

    /** Integer value of an ungrouped or grouped token without a fraction, or null if it is too long to be one. */
    fun valueOf(token: String): Long? {
        val digits = token.replace(",", "")
        return if (digits.length in 1..config.maxCardinalDigits && digits.all(Char::isAsciiDigit)) {
            digits.toLong()
        } else {
            null
        }
    }

    private fun isYear(digits: String): Boolean = digits.length == YEAR_DIGITS && digits.toInt() in config.yearRange

    private companion object {
        const val YEAR_DIGITS = 4
    }
}
