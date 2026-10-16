// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/normalize/NumberWords.kt
// Purpose: Spells numbers as English words: cardinals (international or lakh/crore), ordinals, years, digit runs.

package io.github.skm0078.audora.speech.normalize

/** Which large-number words a cardinal uses: 150000 is "one hundred fifty thousand" or "one lakh fifty thousand". */
internal enum class NumberSystem { INTERNATIONAL, INDIAN }

/**
 * Pure number-to-words functions. Hyphenated tens ("forty-two") and no "and" after hundreds, which every engine
 * reads the same way; the only allowed input is a non-negative value that fits a Long (callers check digit counts).
 */
internal object NumberWords {
    private val UNITS = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
    )
    private val TENS = listOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

    private const val TEN = 10
    private const val TWENTY = 20
    private const val HUNDRED = 100
    private const val THOUSAND = 1_000L
    private const val LAKH = 100_000L
    private const val CRORE = 10_000_000L
    private const val MILLION = 1_000_000L
    private const val BILLION = 1_000_000_000L
    private const val TRILLION = 1_000_000_000_000L
    private const val QUADRILLION = 1_000_000_000_000_000L
    private const val FIRST_FOUR_DIGIT = 1_000
    private const val LAST_FOUR_DIGIT = 9_999

    private val INTERNATIONAL_SCALES = listOf(
        QUADRILLION to "quadrillion",
        TRILLION to "trillion",
        BILLION to "billion",
        MILLION to "million",
        THOUSAND to "thousand",
    )

    // Above 99 crore the crore count is itself spelled out: "one thousand crore", the common Indian usage.
    private val INDIAN_SCALES = listOf(CRORE to "crore", LAKH to "lakh", THOUSAND to "thousand")

    private val IRREGULAR_ORDINALS = mapOf(
        "one" to "first",
        "two" to "second",
        "three" to "third",
        "five" to "fifth",
        "eight" to "eighth",
        "nine" to "ninth",
        "twelve" to "twelfth",
    )

    fun cardinal(value: Long, system: NumberSystem = NumberSystem.INTERNATIONAL): String {
        require(value >= 0) { "value must be >= 0, was $value" }
        if (value < THOUSAND) return belowThousand(value.toInt())
        val scales = if (system == NumberSystem.INDIAN) INDIAN_SCALES else INTERNATIONAL_SCALES
        val parts = mutableListOf<String>()
        var rest = value
        for ((scale, name) in scales) {
            if (rest >= scale) {
                parts += "${cardinal(rest / scale, system)} $name"
                rest %= scale
            }
        }
        if (rest > 0) parts += belowThousand(rest.toInt())
        return parts.joinToString(" ")
    }

    fun ordinal(value: Long, system: NumberSystem = NumberSystem.INTERNATIONAL): String =
        transformLastWord(cardinal(value, system)) { word ->
            IRREGULAR_ORDINALS[word] ?: if (word.endsWith('y')) word.dropLast(1) + "ieth" else word + "th"
        }

    /**
     * Reads a year the way people say it: 1947 "nineteen forty-seven", 1905 "nineteen oh five", 1900 "nineteen
     * hundred", 2007 "two thousand seven", 2024 "twenty twenty-four". Outside 1000..9999 it is a plain cardinal.
     */
    fun year(value: Int): String {
        val century = value / HUNDRED
        val rest = value % HUNDRED
        return when {
            value !in FIRST_FOUR_DIGIT..LAST_FOUR_DIGIT -> cardinal(value.toLong())
            century % TEN == 0 && rest < TEN -> cardinal(value.toLong())
            rest == 0 -> "${belowThousand(century)} hundred"
            rest < TEN -> "${belowThousand(century)} oh ${UNITS[rest]}"
            else -> "${belowThousand(century)} ${belowThousand(rest)}"
        }
    }

    /** "007" is "zero zero seven". */
    fun digits(run: String): String {
        require(run.all(Char::isAsciiDigit)) { "digits expects ASCII digits only" }
        return run.map { UNITS[it - '0'] }.joinToString(" ")
    }

    /** "ninety" is "nineties", "nineteen hundred" "nineteen hundreds", "six" "sixes": decades and plurals. */
    fun plural(words: String): String = transformLastWord(words) { word ->
        when {
            word.endsWith('y') -> word.dropLast(1) + "ies"
            word.endsWith('x') -> word + "es"
            else -> word + "s"
        }
    }

    private fun belowThousand(value: Int): String {
        val hundreds = value / HUNDRED
        val rest = value % HUNDRED
        return when {
            hundreds == 0 -> belowHundred(rest)
            rest == 0 -> "${UNITS[hundreds]} hundred"
            else -> "${UNITS[hundreds]} hundred ${belowHundred(rest)}"
        }
    }

    private fun belowHundred(value: Int): String = when {
        value < TWENTY -> UNITS[value]
        value % TEN == 0 -> TENS[value / TEN]
        else -> "${TENS[value / TEN]}-${UNITS[value % TEN]}"
    }

    private fun transformLastWord(words: String, transform: (String) -> String): String {
        val cut = words.indexOfLast { it == ' ' || it == '-' } + 1
        return words.substring(0, cut) + transform(words.substring(cut))
    }
}

internal fun Char.isAsciiDigit(): Boolean = this in '0'..'9'
