// File: core/src/main/kotlin/io/github/skm0078/audora/core/model/SleepOptions.kt
// Purpose: The listener's sleep timer extras (plan item 37): shake to extend, fade-out, auto-on at bedtime.

package io.github.skm0078.audora.core.model

/**
 * @property shakeToExtend shaking the phone while a timer runs starts it again from now (AntennaPod's "shake to
 *   reset"), with a short buzz, so a half-asleep listener can keep going without looking.
 * @property fadeOut the last seconds of a minutes timer fade the voice out instead of stopping it mid-word.
 * @property bedtime starting to read between [bedtimeFromMinute] and [bedtimeToMinute] (minutes after midnight; the
 *   window may run past midnight) sets a [bedtimeMinutes] timer when none is set.
 */
data class SleepOptions(
    val shakeToExtend: Boolean = true,
    val fadeOut: Boolean = true,
    val bedtime: Boolean = false,
    val bedtimeFromMinute: Int = DEFAULT_BEDTIME_FROM,
    val bedtimeToMinute: Int = DEFAULT_BEDTIME_TO,
    val bedtimeMinutes: Int = DEFAULT_BEDTIME_MINUTES,
) {
    init {
        require(bedtimeFromMinute in 0 until MINUTES_PER_DAY) { "bedtimeFromMinute out of range: $bedtimeFromMinute" }
        require(bedtimeToMinute in 0 until MINUTES_PER_DAY) { "bedtimeToMinute out of range: $bedtimeToMinute" }
        require(bedtimeMinutes > 0) { "bedtimeMinutes must be > 0, was $bedtimeMinutes" }
    }

    /** Whether [minuteOfDay] (0 = midnight) is in the bedtime window; the end minute is outside it. */
    fun inBedtime(minuteOfDay: Int): Boolean = when {
        bedtimeFromMinute == bedtimeToMinute -> false
        bedtimeFromMinute < bedtimeToMinute -> minuteOfDay in bedtimeFromMinute until bedtimeToMinute
        else -> minuteOfDay >= bedtimeFromMinute || minuteOfDay < bedtimeToMinute
    }

    /** One line, `key=value` pairs separated by `;`, like [ReadingModes.encode]; [decode] reads it back. */
    fun encode(): String = listOf(
        KEY_SHAKE to shakeToExtend,
        KEY_FADE to fadeOut,
        KEY_BEDTIME to bedtime,
        KEY_FROM to bedtimeFromMinute,
        KEY_TO to bedtimeToMinute,
        KEY_MINUTES to bedtimeMinutes,
    ).joinToString(";") { (key, value) -> "$key=$value" }

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
        private const val DEFAULT_BEDTIME_FROM = 22 * 60
        private const val DEFAULT_BEDTIME_TO = 6 * 60
        private const val DEFAULT_BEDTIME_MINUTES = 30
        private const val KEY_SHAKE = "shake"
        private const val KEY_FADE = "fade"
        private const val KEY_BEDTIME = "bedtime"
        private const val KEY_FROM = "from"
        private const val KEY_TO = "to"
        private const val KEY_MINUTES = "minutes"

        /** Reads [encode]'s line; a missing or bad value takes its default, so this never fails. */
        fun decode(line: String?): SleepOptions {
            val values = line.orEmpty().split(';').mapNotNull { pair ->
                pair.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() }
            }.toMap()
            val defaults = SleepOptions()
            fun flag(key: String, default: Boolean) = values[key]?.toBooleanStrictOrNull() ?: default
            fun minute(key: String, default: Int) =
                values[key]?.toIntOrNull()?.takeIf { it in 0 until MINUTES_PER_DAY } ?: default
            return SleepOptions(
                shakeToExtend = flag(KEY_SHAKE, defaults.shakeToExtend),
                fadeOut = flag(KEY_FADE, defaults.fadeOut),
                bedtime = flag(KEY_BEDTIME, defaults.bedtime),
                bedtimeFromMinute = minute(KEY_FROM, defaults.bedtimeFromMinute),
                bedtimeToMinute = minute(KEY_TO, defaults.bedtimeToMinute),
                bedtimeMinutes = values[KEY_MINUTES]?.toIntOrNull()?.takeIf { it > 0 } ?: defaults.bedtimeMinutes,
            )
        }
    }
}
