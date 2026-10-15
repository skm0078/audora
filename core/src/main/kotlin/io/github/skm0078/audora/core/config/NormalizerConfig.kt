// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/NormalizerConfig.kt
// Purpose: Tunables for turning written English into speakable words before synthesis (P2-04, ADR-019).

package io.github.skm0078.audora.core.config

/** How an all-numeric date such as 05/08/2024 is read when both fields could be the month. */
enum class DateOrder {
    /** 05/08/2024 is 5 August: India and most of the world. */
    DAY_FIRST,

    /** 05/08/2024 is May 8: the US convention. */
    MONTH_FIRST,
}

/**
 * @property numericDateOrder reading of an ambiguous numeric date; a field above 12 always decides by itself.
 * @property linkWord what a URL or e-mail address is read as (Build Spec §5 step 8: "link"), so a voice never
 *   spells out a web address letter by letter.
 * @property maxCardinalDigits longer digit runs (account, phone and ISBN-like numbers) are read digit by digit.
 *   At most 18, so every cardinal fits a Long.
 * @property rupeesInIndianSystem read ungrouped rupee amounts in lakh and crore (₹150000 is "one lakh fifty
 *   thousand rupees"). A grouped amount always follows its own grouping: 1,50,000 is Indian, 150,000 international.
 * @property yearRange a bare four-digit number in this range is read as a year ("nineteen forty-seven").
 */
data class NormalizerConfig(
    val numericDateOrder: DateOrder = DateOrder.DAY_FIRST,
    val linkWord: String = "link",
    val maxCardinalDigits: Int = 15,
    val rupeesInIndianSystem: Boolean = true,
    val yearRange: IntRange = FIRST_SPOKEN_YEAR..LAST_SPOKEN_YEAR,
) {
    init {
        require(linkWord.isNotBlank()) { "linkWord must not be blank" }
        require(maxCardinalDigits in 1..MAX_LONG_DIGITS) {
            "maxCardinalDigits must be in 1..$MAX_LONG_DIGITS, was $maxCardinalDigits"
        }
        require(yearRange.first in YEAR_DIGITS_MIN..yearRange.last && yearRange.last <= YEAR_DIGITS_MAX) {
            "yearRange must hold four-digit years, was $yearRange"
        }
    }

    private companion object {
        const val FIRST_SPOKEN_YEAR = 1100
        const val LAST_SPOKEN_YEAR = 2099
        const val MAX_LONG_DIGITS = 18
        const val YEAR_DIGITS_MIN = 1000
        const val YEAR_DIGITS_MAX = 9999
    }
}
