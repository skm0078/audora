// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/normalize/CurrencyRule.kt
// Purpose: Reads amounts like ₹1,50,000, Rs. 500, $2.5 million and £0.99 with the currency word after the number.

package io.github.skm0078.audora.speech.normalize

import io.github.skm0078.audora.core.config.NormalizerConfig

/**
 * A two-digit fraction without a magnitude word is read as the minor unit ("$1.50" is "one dollar and fifty
 * cents"); any other fraction is read as a decimal ("$2.5 million" is "two point five million dollars").
 */
internal class CurrencyRule(private val config: NormalizerConfig, private val numbers: NumberReader) :
    NormalizationRule {

    private data class Currency(val one: String, val many: String, val minorOne: String, val minorMany: String)

    override fun apply(text: String, role: TextRole): String = AMOUNT.replace(text) { match ->
        val (symbol, amount, magnitudeWritten) = match.destructured
        val currency = currencyFor(symbol)
        val magnitude = MAGNITUDES[magnitudeWritten.lowercase()] ?: magnitudeWritten.lowercase()
        val ungrouped = if (currency === RUPEE && config.rupeesInIndianSystem) {
            NumberSystem.INDIAN
        } else {
            NumberSystem.INTERNATIONAL
        }
        val fraction = amount.substringAfter('.', missingDelimiterValue = "")
        val spoken = if (magnitude.isEmpty() && fraction.length == MINOR_DIGITS) {
            withMinorUnit(amount.substringBefore('.'), fraction.toInt(), currency, ungrouped)
        } else {
            val count = numbers.read(amount, allowYear = false, ungrouped = ungrouped)
            val isOne = magnitude.isEmpty() && numbers.valueOf(amount) == 1L
            listOf(count, magnitude, if (isOne) currency.one else currency.many)
                .filter(String::isNotEmpty)
                .joinToString(" ")
        }
        spoken
    }

    private fun withMinorUnit(whole: String, minor: Int, currency: Currency, ungrouped: NumberSystem): String {
        val major = numbers.valueOf(whole)
        val majorWords = if (major == 0L) {
            ""
        } else {
            val count = numbers.read(whole, allowYear = false, ungrouped = ungrouped)
            "$count ${if (major == 1L) currency.one else currency.many}"
        }
        val minorWords = if (minor == 0) {
            ""
        } else {
            "${NumberWords.cardinal(minor.toLong())} ${if (minor == 1) currency.minorOne else currency.minorMany}"
        }
        return when {
            majorWords.isEmpty() && minorWords.isEmpty() -> "zero ${currency.many}"
            majorWords.isEmpty() -> minorWords
            minorWords.isEmpty() -> majorWords
            else -> "$majorWords and $minorWords"
        }
    }

    private fun currencyFor(symbol: String): Currency = when (symbol) {
        "$", "US$" -> DOLLAR
        "£" -> POUND
        "€" -> EURO
        else -> RUPEE
    }

    private companion object {
        const val MINOR_DIGITS = 2

        val RUPEE = Currency("rupee", "rupees", "paisa", "paise")
        val DOLLAR = Currency("dollar", "dollars", "cent", "cents")
        val POUND = Currency("pound", "pounds", "penny", "pence")
        val EURO = Currency("euro", "euros", "cent", "cents")

        /** Written magnitude abbreviations and plurals mapped to the spoken word; full words map to themselves. */
        val MAGNITUDES = mapOf(
            "k" to "thousand",
            "lakhs" to "lakh",
            "lac" to "lakh",
            "lacs" to "lakh",
            "cr" to "crore",
            "crores" to "crore",
            "mn" to "million",
            "bn" to "billion",
        )

        // "Rs" and "INR" must start a word; "Rs." may hug the number ("Rs.500").
        val AMOUNT = Regex(
            """(?<![\w$])(₹|Rs\.?|INR|US\$|\$|£|€)\s?(${NumberPatterns.NUMBER})""" +
                """(?:\s?(thousand|lakhs?|lacs?|crores?|cr|million|mn|billion|bn|trillion|k)\b)?""",
        )
    }
}
