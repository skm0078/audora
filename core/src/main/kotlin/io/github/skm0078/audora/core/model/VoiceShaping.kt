// File: core/src/main/kotlin/io/github/skm0078/audora/core/model/VoiceShaping.kt
// Purpose: What is done to the voice's audio after synthesis: even loudness, voice boost, pause trim (items 9, 10).

package io.github.skm0078.audora.core.model

/**
 * @property evenLoudness every sentence is brought to one loudness, so voices (a Hindi Piper voice beside English
 *   Kokoro), mixed-language sentences and quiet or loud models sound the same level.
 * @property boostDb extra loudness in decibels on top, for a noisy street or a weak phone speaker; peaks are softly
 *   limited instead of clipped. 0 = none.
 * @property trimPauses the voice's own long silences (between words, and its lead-in and tail) are shortened, like
 *   a podcast app's "smart speed". The pauses Audora adds (pace, punctuation, paragraphs) are kept.
 */
data class VoiceShaping(val evenLoudness: Boolean = true, val boostDb: Int = 0, val trimPauses: Boolean = false) {
    init {
        require(boostDb >= 0) { "boostDb must be >= 0, was $boostDb" }
    }

    /** One line, `key=value` pairs separated by `;`, like [ReadingModes.encode]; [decode] reads it back. */
    fun encode(): String = "$KEY_EVEN=$evenLoudness;$KEY_BOOST=$boostDb;$KEY_TRIM=$trimPauses"

    companion object {
        private const val KEY_EVEN = "even"
        private const val KEY_BOOST = "boost"
        private const val KEY_TRIM = "trim"

        /** Reads [encode]'s line; a missing or bad value takes its default, so this never fails. */
        fun decode(line: String?): VoiceShaping {
            val values = line.orEmpty().split(';').mapNotNull { pair ->
                pair.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() }
            }.toMap()
            val defaults = VoiceShaping()
            return VoiceShaping(
                evenLoudness = values[KEY_EVEN]?.toBooleanStrictOrNull() ?: defaults.evenLoudness,
                boostDb = values[KEY_BOOST]?.toIntOrNull()?.takeIf { it >= 0 } ?: defaults.boostDb,
                trimPauses = values[KEY_TRIM]?.toBooleanStrictOrNull() ?: defaults.trimPauses,
            )
        }
    }
}
