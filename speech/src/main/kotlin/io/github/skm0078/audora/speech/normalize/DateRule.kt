// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/normalize/DateRule.kt
// Purpose: Reads dates (15 August 1947, Aug. 15, 1947, 15/08/1947, 1947-08-15) with ordinal days and spoken years.

package io.github.skm0078.audora.speech.normalize

import io.github.skm0078.audora.core.config.DateOrder
import io.github.skm0078.audora.core.config.NormalizerConfig

/**
 * Day-first dates read "fifteenth August nineteen forty-seven" (Indian English), month-first ones "August
 * fifteenth, nineteen forty-seven". Numeric dates are read day-first unless [NormalizerConfig.numericDateOrder]
 * says otherwise or a field above 12 settles it. Anything that is not a real day and month is left untouched for
 * the number rules (a "31/14/2020" is not a date).
 */
internal class DateRule(private val config: NormalizerConfig) : NormalizationRule {

    override fun apply(text: String, role: TextRole): String {
        var result = ISO.replace(text) { match ->
            val (year, month, day) = match.destructured
            spokenDate(day.toInt(), month.toInt(), year) ?: match.value
        }
        for (pattern in listOf(NUMERIC, NUMERIC_DOTTED)) {
            result = pattern.replace(result) { match ->
                val (first, second, year) = match.destructured
                numericDate(first.toInt(), second.toInt(), year) ?: match.value
            }
        }
        result = DAY_MONTH.replace(result) { match ->
            val (day, of, monthWritten) = match.destructured
            val year = match.groupValues[YEAR_GROUP]
            val month = monthOf(monthWritten)
            if (day.toInt() !in 1..LAST_DAY) return@replace match.value
            val yearWords = if (year.isEmpty()) "" else " ${yearWords(year)}"
            "${NumberWords.ordinal(day.toLong())}${if (of.isEmpty()) "" else " of"} ${MONTHS[month]}$yearWords"
        }
        result = MONTH_DAY.replace(result) { match ->
            val (monthWritten, day, year) = match.destructured
            if (day.toInt() !in 1..LAST_DAY) return@replace match.value
            val yearWords = if (year.isEmpty()) "" else ", ${yearWords(year)}"
            "${MONTHS[monthOf(monthWritten)]} ${NumberWords.ordinal(day.toLong())}$yearWords"
        }
        return MONTH_YEAR.replace(result) { match ->
            val (monthWritten, year) = match.destructured
            "${MONTHS[monthOf(monthWritten)]} ${yearWords(year)}"
        }
    }

    private fun numericDate(first: Int, second: Int, year: String): String? {
        val dayFirst = when {
            first > LAST_MONTH -> true
            second > LAST_MONTH -> false
            else -> config.numericDateOrder == DateOrder.DAY_FIRST
        }
        return if (dayFirst) spokenDate(first, second, year) else spokenDate(second, first, year)
    }

    private fun spokenDate(day: Int, month: Int, year: String): String? {
        if (day !in 1..LAST_DAY || month !in 1..LAST_MONTH) return null
        val monthName = MONTHS[month - 1]
        return if (config.numericDateOrder == DateOrder.DAY_FIRST) {
            "${NumberWords.ordinal(day.toLong())} $monthName ${yearWords(year)}"
        } else {
            "$monthName ${NumberWords.ordinal(day.toLong())}, ${yearWords(year)}"
        }
    }

    /** "1947" → "nineteen forty-seven"; a two-digit year "07" → "oh seven", "47" → "forty-seven". */
    private fun yearWords(year: String): String = when {
        year.length == TWO_DIGIT_YEAR && year.startsWith('0') -> "oh ${NumberWords.cardinal(year.toLong())}"
        year.length == TWO_DIGIT_YEAR -> NumberWords.cardinal(year.toLong())
        else -> NumberWords.year(year.toInt())
    }

    private fun monthOf(written: String): Int {
        val key = written.trimEnd('.').take(MONTH_PREFIX).lowercase()
        return MONTHS.indexOfFirst { it.take(MONTH_PREFIX).lowercase() == key }
    }

    private companion object {
        const val LAST_DAY = 31
        const val LAST_MONTH = 12
        const val TWO_DIGIT_YEAR = 2
        const val MONTH_PREFIX = 3
        const val YEAR_GROUP = 4

        val MONTHS = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December",
        )

        // Full names, or three-letter abbreviations (and Sept) with an optional full stop. Capitalised only: "may"
        // and "march" are verbs.
        private const val MONTH =
            """(?:January|February|March|April|May|June|July|August|September|October|November|December|""" +
                """(?:Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sept|Sep|Oct|Nov|Dec)\.?)(?![\w])"""
        private const val ORDINAL_SUFFIX = """(?:st|nd|rd|th)?"""
        private const val YEAR = """(\d{4})(?![\d,]\d|\w)"""

        val ISO = Regex("""(?<![\w./-])(\d{4})-(\d{2})-(\d{2})(?![\w/-])""")

        // A full-stop date needs a four-digit year, so section numbers like 3.2.14 are never mistaken for dates.
        val NUMERIC = Regex("""(?<![\w./-])(\d{1,2})[/-](\d{1,2})[/-](\d{4}|\d{2})(?![\w/-]|\.\d)""")
        val NUMERIC_DOTTED = Regex("""(?<![\w./-])(\d{1,2})\.(\d{1,2})\.(\d{4})(?![\w.]?\d)""")
        val DAY_MONTH = Regex("""(?<![\w.,])(\d{1,2})$ORDINAL_SUFFIX(\s+of)?\s+($MONTH)(?:,?\s+$YEAR)?""")
        val MONTH_DAY = Regex("""($MONTH)\s+(\d{1,2})$ORDINAL_SUFFIX(?![\w,]?\d)(?![\w])(?:,?\s+$YEAR)?""")
        val MONTH_YEAR = Regex("""($MONTH)\s+$YEAR""")
    }
}
