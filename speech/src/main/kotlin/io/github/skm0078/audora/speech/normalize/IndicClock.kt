// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/normalize/IndicClock.kt
// Purpose: Clock times in Hindi and Bengali parts ("10:30 बजे", "১০:৩০টায়") read the way people say them (item 57).

package io.github.skm0078.audora.speech.normalize

import java.text.Normalizer

/**
 * Reads "H:MM" (hours 0–23, two-digit minutes) on text whose digits are already ASCII. Without it the number rule
 * read the two halves as separate numbers with the colon between ("दस:तीस").
 * - Hindi: "10:00" → "दस बजे", "10:30" → "दस बजकर तीस मिनट"; a "बजे" written after the time is part of what is said,
 *   so it is not read twice.
 * - Bengali: the hour takes its clock form (একটা, দুটো, তিনটে … দশটা), "10:00" → "দশটা", "10:30" → "দশটা বেজে ত্রিশ
 *   মিনিট"; a "টায়" or "টা" written after the time becomes the locative ("দশটায়", "… মিনিটে").
 * Anything that is not a clock time ("25:10", "10:75", "1:2", a verse "3:16:2") is left for the number rules.
 */
internal class IndicClock private constructor(
    private val after: Regex,
    private val say: (hour: Int, minute: Int, suffix: String, words: IndicNumberWords) -> String,
) {
    private val pattern =
        Regex("""(?<![\w.:])(\d{1,2}):(\d{2})(?![\w:]|\.\d)((?:${after.pattern})(?![\p{L}\p{M}]))?""")

    fun times(ascii: String, words: IndicNumberWords): String = pattern.replace(ascii) { match ->
        val hour = match.groupValues[HOUR].toInt()
        val minute = match.groupValues[MINUTE].toInt()
        if (hour > LAST_HOUR || minute > LAST_MINUTE) {
            match.value
        } else {
            nfc(say(hour, minute, match.groupValues[SUFFIX], words))
        }
    }

    companion object {
        private const val HOUR = 1
        private const val MINUTE = 2
        private const val SUFFIX = 3
        private const val LAST_HOUR = 23
        private const val LAST_MINUTE = 59

        private fun nfc(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)

        val HINDI = IndicClock(Regex("""\s?बजे""")) { hour, minute, _, words ->
            val hours = words.cardinal(hour.toLong())
            if (minute == 0) "$hours बजे" else "$hours बजकर ${words.cardinal(minute.toLong())} मिनट"
        }

        /** 1 to 12 o'clock in Bengali's clock form; other hours (0, 13–23) add "টা" to the number. */
        private val BENGALI_HOURS = listOf(
            "একটা", "দুটো", "তিনটে", "চারটে", "পাঁচটা", "ছটা", "সাতটা", "আটটা", "নটা", "দশটা", "এগারোটা", "বারোটা",
        )

        val BENGALI = IndicClock(Regex("""\s?টা(?:\u09DF|\u09AF\u09BC)?""")) { hour, minute, suffix, words ->
            val locative = suffix.endsWith('\u09DF') || suffix.endsWith('\u09BC')
            val hours = BENGALI_HOURS.getOrNull(hour - 1) ?: (words.cardinal(hour.toLong()) + "টা")
            when {
                minute == 0 && locative -> hours + "য়"
                minute == 0 -> hours
                else -> "$hours বেজে ${words.cardinal(minute.toLong())} মিনিট" + if (locative) "ে" else ""
            }
        }
    }
}
