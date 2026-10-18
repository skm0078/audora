// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/normalize/IndicClock.kt
// Purpose: Clock times in Hindi and Bengali parts ("10:30 बजे", "১০:৩০টায়") read the way people say them (item 57).

package io.github.skm0078.audora.speech.normalize

import java.text.Normalizer

/**
 * Reads "H:MM" (hours 0–23, two-digit minutes) on text whose digits are already ASCII. Without it the number rule
 * read the two halves as separate numbers with the colon between ("दस:तीस").
 *
 * "H:MM" is a time only when something says so, since "2:30" is also a verse or a score: a "बजे"/"টা" after it, an
 * AM or PM after it, a time word before it ("सुबह", "समय", "সকাল", "সময়"), or whole hours (":00", never a verse).
 * - Hours 0–12 are said on the clock (0 is twelve), with the quarters people use: "10:15" → "सवा दस बजे", "10:30" →
 *   "साढ़े दस बजे", "10:45" → "पौने ग्यारह बजे", "1:30" → "डेढ़ बजे", "2:30" → "ढाई बजे"; Bengali "সোয়া দশটা",
 *   "সাড়ে দশটা", "পৌনে এগারোটা", "দেড়টা", "আড়াইটা". Other minutes: "दस बजकर बीस मिनट", "দশটা বেজে কুড়ি মিনিট".
 * - Hours 13–23 keep the minutes: "चौदह बजकर पाँच मिनट".
 * - AM and PM become the part of the day before the hour: "10:30 PM" → "रात साढ़े दस बजे", "সকাল সাড়ে দশটা"; a part of
 *   the day written before the time says it already ("शाम 6:30 PM"), "समय" does not ("समय रात साढ़े दस बजे").
 * - A "बजे" written after the time is part of what is said, so it is not read twice; when the minutes end the phrase,
 *   it becomes "पर" ("दस बजकर बीस मिनट पर"). A Bengali "টায়"/"টা" becomes the locative ("দশটায়", "… মিনিটে").
 * Anything that is not a clock time ("25:10", "10:75", "1:2", a verse "3:16:2", "1:30,5") is left for the number
 * rules. [times] runs before the initials step, so "a.m." is still whole.
 */
internal class IndicClock private constructor(private val language: ClockLanguage) {
    private val marker = alternatives(language.dayParts + language.timeWords)
    private val pattern = Regex(
        """(?:(?<![\p{L}\p{M}])((?:$marker)${language.timeWordTail}:?)\s+)?""" +
            """(?<![\w.:])(\d{1,2}):(\d{2})""" +
            """(?:((?:${language.suffix.pattern})(?![\p{L}\p{M}]))|\s?([AaPp])\.?\s?[Mm]\.?(?![\p{L}\p{M}]))?""" +
            """(?![\p{L}\p{M}\p{N}:]|[.,]\p{N})""",
    )

    private val dayPart = Regex("""^(?:${alternatives(language.dayParts)})""")

    fun times(ascii: String, words: IndicNumberWords): String = pattern.replace(ascii) { match ->
        val time = ClockTime.of(match, ascii) ?: return@replace match.value
        val prefix = match.groupValues[TIME_WORD]
        // A part of the day before the time already says what AM or PM would; "समय" does not.
        val period = time.meridiemHour?.takeUnless { dayPart.containsMatchIn(prefix) }?.let(language::periodOf)
        val said = listOfNotNull(prefix.ifEmpty { null }, period, language.say(time, words)).joinToString(" ")
        keepSentenceStop(ascii, match, nfc(said))
    }

    /** One time as written: [hour] is 0–23; [meridiemHour] is it on the 24-hour clock when AM or PM was given. */
    internal class ClockTime(
        val hour: Int,
        val minute: Int,
        val suffix: String,
        val meridiemHour: Int?,
        val rest: String,
    ) {
        /** The hour on the clock face, 1–12, or null for 13–23 written without AM or PM. */
        val clockHour: Int? = when {
            hour == 0 -> HOURS_ON_FACE
            hour <= HOURS_ON_FACE -> hour
            else -> null
        }

        companion object {
            fun of(match: MatchResult, text: String): ClockTime? {
                val hour = match.groupValues[HOUR].toInt()
                val minute = match.groupValues[MINUTE].toInt()
                val meridiem = match.groupValues[MERIDIEM]
                val marked =
                    minute == 0 || listOf(TIME_WORD, SUFFIX, MERIDIEM).any { match.groupValues[it].isNotEmpty() }
                val onFace = meridiem.isEmpty() || hour in 1..HOURS_ON_FACE
                val onClock = hour <= LAST_HOUR && minute <= LAST_MINUTE
                if (!onClock || !marked || !onFace) return null
                val meridiemHour = meridiem.takeIf { it.isNotEmpty() }?.let { onDayClock(hour, it) }
                val rest = text.substring(match.range.last + 1)
                return ClockTime(hour, minute, match.groupValues[SUFFIX], meridiemHour, rest)
            }

            private fun onDayClock(hour: Int, meridiem: String): Int {
                val morning = meridiem.equals("a", ignoreCase = true)
                return when {
                    morning && hour == HOURS_ON_FACE -> 0
                    morning || hour == HOURS_ON_FACE -> hour
                    else -> hour + HOURS_ON_FACE
                }
            }
        }
    }

    /** What one language says. */
    internal interface ClockLanguage {
        /** Parts of the day ("सुबह", "রাত"): before a time they replace its AM or PM. */
        val dayParts: List<String>

        /** Other words that mark a time ("समय", "সময়"); an AM or PM after the time is still said. */
        val timeWords: List<String>

        /** What may follow a time word before the time ("शाम को", "সকালে"). */
        val timeWordTail: String
        val suffix: Regex

        /** The part of the day for an hour on the 24-hour clock. */
        fun periodOf(hour: Int): String
        fun say(time: ClockTime, words: IndicNumberWords): String
    }

    companion object {
        private const val TIME_WORD = 1
        private const val HOUR = 2
        private const val MINUTE = 3
        private const val SUFFIX = 4
        private const val MERIDIEM = 5
        private const val LAST_HOUR = 23
        private const val LAST_MINUTE = 59
        private const val HOURS_ON_FACE = 12
        private const val QUARTER_PAST = 15
        private const val HALF_PAST = 30
        private const val QUARTER_TO = 45
        private const val ONE = 1
        private const val TWO = 2

        private fun nfc(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)

        /** Both spellings of each word: Bengali য় is one code point in some PDFs and য plus nukta in NFC. */
        private fun alternatives(words: List<String>): String = words
            .flatMap { listOf(it, nfc(it), it.replace("য়", "য়")) }
            .distinct().sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }

        /** The hour after [clockHour] on the clock face: "पौने ग्यारह" is a quarter to eleven. */
        private fun nextOnFace(clockHour: Int): Int = clockHour % HOURS_ON_FACE + 1

        private fun periodFrom(hour: Int, periods: List<Pair<IntRange, String>>, night: String): String =
            periods.firstOrNull { hour in it.first }?.second ?: night

        val HINDI = IndicClock(
            object : ClockLanguage {
                override val dayParts = listOf("सुबह", "दोपहर", "शाम", "रात")
                override val timeWords = listOf("समय")
                override val timeWordTail = """(?:\s+(?:के|को|में))?"""
                override val suffix = Regex("""\s?बजे""")

                @Suppress("MagicNumber") // The hours each part of the day covers.
                private val periods = listOf(4..11 to "सुबह", 12..15 to "दोपहर", 16..18 to "शाम")
                private val postpositions = Regex("""^\s*(?:का|की|के|से|तक|पर|को|में)(?![\p{L}\p{M}])""")

                override fun periodOf(hour: Int) = periodFrom(hour, periods, "रात")

                override fun say(time: ClockTime, words: IndicNumberWords): String {
                    val face = time.clockHour
                    val hours = words.cardinal((face ?: time.hour).toLong())
                    return when {
                        time.minute == 0 -> "$hours बजे"
                        face == null -> minutes(time, hours, words)
                        time.minute == QUARTER_PAST -> "सवा $hours बजे"
                        time.minute == HALF_PAST && face == ONE -> "डेढ़ बजे"
                        time.minute == HALF_PAST && face == TWO -> "ढाई बजे"
                        time.minute == HALF_PAST -> "साढ़े $hours बजे"
                        time.minute == QUARTER_TO -> "पौने ${words.cardinal(nextOnFace(face).toLong())} बजे"
                        else -> minutes(time, hours, words)
                    }
                }

                /** "दस बजकर बीस मिनट", with "पर" for a written "बजे" unless a postposition follows. */
                private fun minutes(time: ClockTime, hours: String, words: IndicNumberWords): String {
                    val at = time.suffix.isNotEmpty() && !postpositions.containsMatchIn(time.rest)
                    return "$hours बजकर ${words.cardinal(time.minute.toLong())} मिनट" + if (at) " पर" else ""
                }
            },
        )

        /** 1 to 12 o'clock in Bengali's clock form; hours 13–23 add "টা" to the number. */
        private val BENGALI_HOURS = listOf(
            "একটা", "দুটো", "তিনটে", "চারটে", "পাঁচটা", "ছটা", "সাতটা", "আটটা", "নটা", "দশটা", "এগারোটা", "বারোটা",
        )

        val BENGALI = IndicClock(
            object : ClockLanguage {
                override val dayParts = listOf("সকাল", "দুপুর", "বিকেল", "সন্ধ্যা", "রাত")
                override val timeWords = listOf("সময়")
                override val timeWordTail = """(?:ে|য়|য়)?"""
                override val suffix = Regex("""\s?টা(?:য়|য়)?""")

                @Suppress("MagicNumber") // The hours each part of the day covers.
                private val periods = listOf(4..11 to "সকাল", 12..14 to "দুপুর", 15..17 to "বিকেল", 18..18 to "সন্ধ্যা")

                override fun periodOf(hour: Int) = periodFrom(hour, periods, "রাত")

                override fun say(time: ClockTime, words: IndicNumberWords): String {
                    val locative = time.suffix.endsWith('য়') || time.suffix.endsWith('়')
                    val face = time.clockHour
                    val hours = face?.let { BENGALI_HOURS[it - 1] } ?: (words.cardinal(time.hour.toLong()) + "টা")
                    val said = when {
                        time.minute == 0 -> hours
                        face == null -> null
                        time.minute == QUARTER_PAST -> "সোয়া $hours"
                        time.minute == HALF_PAST && face == ONE -> "দেড়টা"
                        time.minute == HALF_PAST && face == TWO -> "আড়াইটা"
                        time.minute == HALF_PAST -> "সাড়ে $hours"
                        time.minute == QUARTER_TO -> "পৌনে ${BENGALI_HOURS[nextOnFace(face) - 1]}"
                        else -> null
                    }
                    return said?.let { if (locative) it + "য়" else it } ?: minutes(time, hours, locative, words)
                }

                private fun minutes(time: ClockTime, hours: String, locative: Boolean, words: IndicNumberWords) =
                    "$hours বেজে ${words.cardinal(time.minute.toLong())} মিনিট" + if (locative) "ে" else ""
            },
        )
    }
}
