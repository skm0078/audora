// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/cleanup/Folios.kt
// Purpose: Recognises page numbers (folios) in a margin line: "12", "xii", "Page 3 of 50", "– 7 –".

package io.github.skm0078.audora.pdf.cleanup

/**
 * @property values every page number the line carries: a span that is only a page number, or a plain number at
 *   either end of the line ("CHAPTER 5. CONNECTIVES 33"). Roman numerals count only as whole spans.
 * @property standalone the whole line is one numeric page number. Roman numerals alone never are: "I" or "IV" at
 *   the top of a page is as likely a part heading.
 */
internal data class FolioInfo(val values: List<Int>, val standalone: Boolean)

internal object Folios {
    /** [spans] are the line's runs of text, left to right. Digits of any script count (Devanagari, Bengali). */
    fun of(spans: List<String>, maxDigits: Int): FolioInfo {
        val values = ArrayList<Int>()
        val spanFolios = spans.map { span -> span.trim().let { numeric(it, maxDigits) ?: roman(it) } }
        spanFolios.filterNotNullTo(values)
        // A number at either end of a span that is not a page number by itself: "Chapter 5 Connectives 33".
        val first = spans.firstOrNull()?.takeIf { spanFolios.first() == null }?.let(::tokens)?.firstOrNull()
        val last = spans.lastOrNull()?.takeIf { spanFolios.last() == null }?.let(::tokens)?.lastOrNull()
        listOfNotNull(first, last?.takeIf { spans.size > 1 || tokens(spans.last()).size > 1 })
            .mapNotNull { number(it, maxDigits) }
            .forEach(values::add)
        val whole = spans.joinToString(" ").trim()
        return FolioInfo(values.distinct(), standalone = numeric(whole, maxDigits) != null)
    }

    private fun tokens(text: String): List<String> = text.split(WHITESPACE).filter { it.isNotEmpty() }

    /** "12", "Page 12", "p. 12", "Page 3 of 50", "3 / 50", "– 7 –": the page's own number. */
    private fun numeric(text: String, maxDigits: Int): Int? {
        for (pattern in NUMERIC) {
            val match = pattern.matchEntire(text) ?: continue
            return number(match.groupValues[1], maxDigits)
        }
        return null
    }

    /** A run of decimal digits of one script, at most [maxDigits] long. */
    private fun number(text: String, maxDigits: Int): Int? {
        val digits = text.map { Character.digit(it, DECIMAL) }
        if (digits.isEmpty() || digits.size > maxDigits || digits.any { it < 0 }) return null
        return digits.fold(0) { value, digit -> value * DECIMAL + digit }
    }

    /** "xii" or "XII"; one case only, at most [MAX_ROMAN] letters, and a well-formed numeral. */
    private fun roman(text: String): Int? {
        val oneCase = text.all { it in ROMAN_LOWER } || text.all { it in ROMAN_UPPER }
        if (text.isEmpty() || text.length > MAX_ROMAN || !oneCase) return null
        val upper = text.uppercase()
        var total = 0
        for (i in upper.indices) {
            val value = ROMAN_VALUES.getValue(upper[i])
            val next = if (i + 1 < upper.length) ROMAN_VALUES.getValue(upper[i + 1]) else 0
            total += if (value < next) -value else value
        }
        return total.takeIf { it > 0 && toRoman(it) == upper }
    }

    private fun toRoman(value: Int): String {
        var rest = value
        val out = StringBuilder()
        for ((symbol, amount) in ROMAN_STEPS) {
            while (rest >= amount) {
                out.append(symbol)
                rest -= amount
            }
        }
        return out.toString()
    }

    private const val DECIMAL = 10
    private const val MAX_ROMAN = 8
    private const val ROMAN_LOWER = "ivxlcdm"
    private const val ROMAN_UPPER = "IVXLCDM"

    @Suppress("MagicNumber") // The numeral system itself.
    private val ROMAN_VALUES = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000)

    @Suppress("MagicNumber")
    private val ROMAN_STEPS = listOf(
        "M" to 1000, "CM" to 900, "D" to 500, "CD" to 400, "C" to 100, "XC" to 90,
        "L" to 50, "XL" to 40, "X" to 10, "IX" to 9, "V" to 5, "IV" to 4, "I" to 1,
    )

    private val WHITESPACE = Regex("\\s+")

    /** Group 1 is the page's own number. */
    private val NUMERIC = listOf(
        Regex("(\\p{Nd}+)"),
        Regex("(?i)(?:page|pg\\.?|p\\.)\\s*(\\p{Nd}+)(?:\\s*(?:of|/)\\s*\\p{Nd}+)?"),
        Regex("(?i)(\\p{Nd}+)\\s*(?:of|/)\\s*\\p{Nd}+"),
        Regex("[-\u2013\u2014]\\s*(\\p{Nd}+)\\s*[-\u2013\u2014]"),
    )
}
