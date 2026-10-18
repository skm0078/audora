// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/normalize/IndicTextPack.kt
// Purpose: Hindi and Bengali dates, ordinals, units and abbreviations read the way people say them (plan item 3).

package io.github.skm0078.audora.speech.normalize

import io.github.skm0078.audora.core.config.NormalizerConfig
import java.text.Normalizer

/**
 * Run by [IndicNormalizer] before its number rules. [abbreviations] works on the written text; the rest on text whose
 * digits are already ASCII. Each rule leaves anything it does not recognise as it was (a "date" with month 13, an
 * ordinal past 999), so the number rules still read it.
 * - **Dates** day first, as India writes them: "15/08/1947", "15-8-47", "15.08.1947" → "पंद्रह अगस्त उन्नीस सौ
 *   सैंतालीस" / "পনেরোই আগস্ট ...". Bengali says the day with its date ending (পয়লা, দোসরা, পাঁচই, উনিশে).
 * - **Ordinals** "1ला, 2री, 6ठे, 21वीं" → पहला, दूसरी, छठे, इक्कीसवीं;
 *   "১ম, ২য়, ৪র্থ, ১১শ, ২১তম" → প্রথম, দ্বিতীয়, চতুর্থ, একাদশ, একুশতম; Bengali day endings "১লা, ১৯শে" too.
 * - **Units** after a number, as Latin symbols: "5 km", "10kg", "37°C" → किलोमीटर, किलोग्राम, डिग्री सेल्सियस.
 * - **Abbreviations** the language's own: "डॉ.", "कि.मी.", "1947 ई.", "ডঃ", "পৃঃ", "খ্রিঃ" and the like; some only
 *   before a number of up to three digits ("सं. 5"), which elsewhere just lose the stop. A stop at the very end of
 *   the text stays ("500 ई.पू." ends the sentence).
 * - **Initials** "ए. पी. जे.", "এ.পি.জে.", "A. P. J." lose their full stops ("ए पी जे"): the voices paused and fell
 *   at each one like a sentence end (item 57). A stop at the very end of the text stays a stop, and short words
 *   that end sentences ("है. हाँ.") are not initials.
 * - **Roman numerals** after a numbering word: "अध्याय IV" → "अध्याय चार", "ভাগ ii" → "ভাগ দুই".
 * - **Clock times** ([IndicClock]), run first, before the initials step can take the dots of "a.m.".
 */
internal class IndicTextPack(
    private val words: IndicNumberWords,
    private val config: NormalizerConfig,
    private val table: IndicTextTable,
) {
    fun abbreviations(text: String): String {
        val eras = table.eraPattern.replace(text) { match ->
            keepSentenceStop(text, match, " " + table.eras.getValue(match.groupValues[1]))
        }
        // Whole abbreviations before "सं." alone, so "वि. सं." is read whole, not as "वि." and "सं".
        val expanded = table.abbreviationPattern.replace(eras) { match ->
            keepSentenceStop(eras, match, table.abbreviations.getValue(match.value))
        }
        val numbered = table.numberedAbbreviationPattern?.replace(expanded) { match ->
            val beforeNumber = SHORT_NUMBER_AHEAD.containsMatchIn(expanded.substring(match.range.last + 1))
            if (beforeNumber) {
                table.numberedAbbreviations.getValue(match.value)
            } else {
                keepSentenceStop(expanded, match, match.value.removeSuffix("."))
            }
        } ?: expanded
        return table.initialsPattern.replace(numbered) { match ->
            val letters = match.value.split('.').map(String::trim).filter(String::isNotEmpty)
            keepSentenceStop(numbered, match, letters.joinToString(" "))
        }
    }

    fun romanNumerals(text: String): String = table.romanPattern.replace(text) { match ->
        val (keyword, numeral) = match.destructured
        RomanNumerals.valueOf(numeral)?.let { "$keyword ${words.cardinal(it.toLong())}" } ?: match.value
    }

    fun times(ascii: String): String = table.clock.times(ascii, words)

    fun dates(ascii: String): String = DATE.replace(ascii) { match ->
        val day = match.groupValues[DAY].toInt()
        val month = match.groupValues[MONTH].toInt()
        val yearText = match.groupValues[YEAR]
        if (day !in 1..MAX_DAY || month !in 1..table.months.size) {
            match.value
        } else {
            "${table.dayOf(day, words)} ${table.months[month - 1]} ${year(yearText)}"
        }
    }

    fun ordinals(ascii: String): String = table.ordinalPattern.replace(ascii) { match ->
        val n = match.groupValues[1].toInt()
        table.ordinal(n, Normalizer.normalize(match.groupValues[2], Normalizer.Form.NFC), words) ?: match.value
    }

    fun units(ascii: String): String = UNIT.replace(ascii) { match ->
        " " + table.units.getValue(match.groupValues[1])
    }

    private fun year(digits: String): String {
        val value = digits.toInt()
        return if (digits.length == YEAR_DIGITS &&
            value in config.yearRange
        ) {
            words.year(value)
        } else {
            words.cardinal(value.toLong())
        }
    }

    private companion object {
        const val MAX_DAY = 31
        const val DAY = 1
        const val MONTH = 3
        const val YEAR = 4
        const val YEAR_DIGITS = 4

        /** Day, separator, month, the same separator, a four- or two-digit year. */
        val DATE = Regex("""(?<![\w./-])(\d{1,2})([/.-])(\d{1,2})\2(\d{4}|\d{2})(?![\w/-]|\.\d)""")

        /** A number of one to three digits next: "सं. 5" is a number, "सं. 2050" a year of an era. */
        val SHORT_NUMBER_AHEAD = Regex("""^\s?\p{Nd}{1,3}(?!\p{Nd})""")

        val UNIT = Regex("""(?<=\d)\s?(${IndicTextTable.UNIT_KEYS})(?![\p{L}\p{M}\d])""")
    }
}
