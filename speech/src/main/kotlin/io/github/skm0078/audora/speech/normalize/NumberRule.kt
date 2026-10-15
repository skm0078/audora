// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/normalize/NumberRule.kt
// Purpose: Reads the numbers left after dates, times, money and units: sections, ranges, decades, ordinals, plain.

package io.github.skm0078.audora.speech.normalize

/**
 * The last rule, so every more specific reading has had its chance. A bare four-digit number in the configured
 * year range reads as a year: "in 1947" is right, "1500 soldiers" becomes "fifteen hundred soldiers", which is
 * still natural English; amounts and measurements were already read as quantities by earlier rules.
 */
internal class NumberRule(private val numbers: NumberReader) : NormalizationRule {

    override fun apply(text: String, role: TextRole): String {
        var result = SECTION.replace(text) { match ->
            match.value.split('.').joinToString(" point ") { numbers.read(it, allowYear = false) }
        }
        for (range in listOf(DASH_RANGE, HYPHEN_RANGE)) {
            result = range.replace(result) { match ->
                val (from, to) = match.destructured
                "${numbers.read(from)} to ${numbers.read(to)}"
            }
        }
        result = HYPHENATED_CODE.replace(result) { match ->
            match.value.split('-').joinToString(", ") { NumberWords.digits(it) }
        }
        result = DECADE.replace(result) { match -> decade(match.groupValues[1]) }
        result = ORDINAL.replace(result) { match ->
            val value = numbers.valueOf(match.groupValues[1])
            if (value == null) match.value else NumberWords.ordinal(value)
        }
        return PLAIN.replace(result) { match -> numbers.read(match.value) }
    }

    /** "1990" → "nineteen nineties", "80" → "eighties", "100" → "hundreds". */
    private fun decade(digits: String): String = when (digits.toLong()) {
        HUNDRED -> "hundreds"
        THOUSAND -> "thousands"
        else -> NumberWords.plural(numbers.read(digits))
    }

    private companion object {
        const val HUNDRED = 100L
        const val THOUSAND = 1_000L
        private const val NUMBER = NumberPatterns.NUMBER

        /** 3.2.1 or 192.168.0.1: at least three dot-separated parts. */
        val SECTION = Regex("""(?<![\w.])\d+(?:\.\d+){2,}(?![\w]|\.\d)""")

        /** En or em dash, spaces allowed: any two numbers. */
        val DASH_RANGE = Regex("""(?<![\w.,])($NUMBER)\s?[–—]\s?($NUMBER)(?![\w]|[.,]\d)""")

        /**
         * Hyphen, no spaces, up to four digits a side: "10-12", "2019-20". Longer runs are phone or account
         * numbers ("98765-43210"), read digit by digit.
         */
        val HYPHEN_RANGE = Regex("""(?<![\w.,-])(\d{1,4})-(\d{1,4})(?![\w-]|[.,]\d)""")

        /** Phone, PIN and account numbers in hyphenated groups, left over after ranges: read digit by digit. */
        val HYPHENATED_CODE = Regex("""(?<![\w.,-])\d+(?:-\d+)+(?![\w-])""")

        /** 1990s, '90s, 80s. */
        val DECADE = Regex("""(?<![\w.,])['’]?(\d{0,3}0)s(?![\w])""")

        val ORDINAL = Regex("""(?<![\w.,])(\d{1,15})(?:st|nd|rd|th)(?![\w])""")

        val PLAIN = Regex("""(?<![\w.])$NUMBER(?![\w])""")
    }
}
