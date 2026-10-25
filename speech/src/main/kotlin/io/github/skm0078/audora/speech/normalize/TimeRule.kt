// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/normalize/TimeRule.kt
// Purpose: Reads clock times (10:30, 9 a.m., 18:00 hrs) as people say them.

package io.github.skm0078.audora.speech.normalize

/**
 * a.m./p.m. become "ay em"/"pee em": spelled phonetically because engines disagree on "am", "a.m." and "AM", and
 * one of them (ADR-012) has no normalizer at all. Times with seconds are left to the number rule.
 */
internal class TimeRule : NormalizationRule {
    override fun apply(text: String, role: TextRole): String {
        val clocks = CLOCK.replace(text) { match ->
            val (hourText, minuteText, meridiem) = match.destructured
            val hour = hourText.toInt()
            val minute = minuteText.toInt()
            val lastHour = if (meridiem.isEmpty()) LAST_HOUR else HALF_DAY
            if (hour > lastHour || minute > LAST_MINUTE) return@replace match.value
            keepSentenceStop(text, match, clock(hour, minute, meridiem))
        }
        return HOUR_ONLY.replace(clocks) { match ->
            val (hourText, meridiem) = match.destructured
            val hour = hourText.toInt()
            if (hour !in 1..HALF_DAY) return@replace match.value
            keepSentenceStop(clocks, match, "${NumberWords.cardinal(hour.toLong())} ${spokenMeridiem(meridiem)}")
        }
    }

    private fun clock(hour: Int, minute: Int, meridiem: String): String {
        val hourWords = NumberWords.cardinal(hour.toLong())
        val minuteWords = when {
            minute == 0 && meridiem.isNotEmpty() -> ""
            minute == 0 && hour in 1..HALF_DAY -> "o'clock"
            minute == 0 -> "hundred"
            minute < MINUTES_NEEDING_OH -> "oh ${NumberWords.cardinal(minute.toLong())}"
            else -> NumberWords.cardinal(minute.toLong())
        }
        return listOf(hourWords, minuteWords, spokenMeridiem(meridiem)).filter(String::isNotEmpty).joinToString(" ")
    }

    private fun spokenMeridiem(written: String): String = when {
        written.isEmpty() -> ""
        written.first().lowercaseChar() == 'a' -> "ay em"
        else -> "pee em"
    }

    private companion object {
        const val LAST_HOUR = 24
        const val LAST_MINUTE = 59
        const val HALF_DAY = 12
        const val MINUTES_NEEDING_OH = 10

        const val MERIDIEM = """(?:[aApP]\.?[mM]\.?)(?![\w])"""

        // Not a map scale or ratio with grouped digits: "1:50,000" is one to fifty thousand, never a clock time.
        val CLOCK = Regex("""(?<![\w:.])(\d{1,2}):(\d{2})(?![:\d]|,\d)(?:\s?($MERIDIEM))?""")
        val HOUR_ONLY = Regex("""(?<![\w:.])(\d{1,2})\s?($MERIDIEM)""")
    }
}
