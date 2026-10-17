// File: core/src/main/kotlin/io/github/skm0078/audora/core/model/VoiceShaping.kt
// Purpose: How the voice sounds: loudness, boost, pause trim (items 9, 10), expressiveness (8), cast voices (7)
// character voices (63) and the narrator blend (75).

package io.github.skm0078.audora.core.model

import io.github.skm0078.audora.core.text.UrlText

/**
 * @property evenLoudness every sentence is brought to one loudness, so voices (a Hindi Piper voice beside English
 *   Kokoro), mixed-language sentences and quiet or loud models sound the same level.
 * @property boostDb extra loudness in decibels on top, for a noisy street or a weak phone speaker; peaks are softly
 *   limited instead of clipped. 0 = none.
 * @property trimPauses the voice's own long silences (between words, and its lead-in and tail) are shortened, like
 *   a podcast app's "smart speed". The pauses Audora adds (pace, punctuation, paragraphs) are kept.
 * @property expressiveness from 0, a newsreader's even delivery, up to `VoiceShapingConfig.maxExpression`, a
 *   storyteller's livelier one. Unlike the rest, set when the voice is synthesized (Piper and MMS voices' noise
 *   values), so a change reloads the voice, about a second.
 * @property dialogueVoices plan item 7: the voice that reads quoted speech, per language; a language missing here
 *   (or an empty map, the default) keeps the narrator's voice for quotes.
 * @property headingVoices the voice that reads headings, per language, the same way.
 * @property characterVoices plan item 63: each character the dialogue tags name ("said Mira") gets a voice of their
 *   own, kept per book, where a guess at the speaker is confident; the rest of the quotes keep the dialogue voice.
 *   Off by default: a wrong guess puts a line in the wrong voice.
 * @property blendWith plan item 75: another speaker of the narrator's Kokoro model (its speaker id) whose style is
 *   mixed into the narrator's own, for a voice of the listener's making; null (the default) keeps the voice as is.
 *   Like expressiveness, set when the voice loads, so a change reloads it.
 * @property blendPercent how much of [blendWith] is mixed in, from 1 to 99 percent.
 */
data class VoiceShaping(
    val evenLoudness: Boolean = true,
    val boostDb: Int = 0,
    val trimPauses: Boolean = false,
    val expressiveness: Int = DEFAULT_EXPRESSION,
    val dialogueVoices: Map<Lang, VoiceId> = emptyMap(),
    val headingVoices: Map<Lang, VoiceId> = emptyMap(),
    val characterVoices: Boolean = false,
    val blendWith: Int? = null,
    val blendPercent: Int = DEFAULT_BLEND_PERCENT,
) {
    init {
        require(blendWith == null || blendWith >= 0) { "blendWith must be >= 0, was $blendWith" }
        require(blendPercent in BLEND_PERCENTS) { "blendPercent must be in $BLEND_PERCENTS, was $blendPercent" }
        require(boostDb >= 0) { "boostDb must be >= 0, was $boostDb" }
        require(expressiveness >= 0) { "expressiveness must be >= 0, was $expressiveness" }
    }

    /**
     * One line, `key=value` pairs separated by `;`, like [ReadingModes.encode]; [decode] reads it back. The voice
     * maps are percent-encoded, since their own form ([VoiceMaps]) uses `=` and `;`.
     */
    fun encode(): String =
        "$KEY_EVEN=$evenLoudness;$KEY_BOOST=$boostDb;$KEY_TRIM=$trimPauses;$KEY_EXPRESSION=$expressiveness;" +
            "$KEY_DIALOGUE=${encoded(dialogueVoices)};$KEY_HEADING=${encoded(headingVoices)};" +
            "$KEY_CHARACTERS=$characterVoices;$KEY_BLEND_WITH=${blendWith ?: ""};$KEY_BLEND_PERCENT=$blendPercent"

    companion object {
        /** The middle step: the voices' own noise values, as before the setting existed. */
        const val DEFAULT_EXPRESSION = 2

        /** Enough of the other voice to hear it, while the narrator stays recognisably itself. */
        const val DEFAULT_BLEND_PERCENT = 30

        /** A blend of 0 or 100 percent is just one of the two voices. */
        val BLEND_PERCENTS = 1..99

        private const val KEY_EVEN = "even"
        private const val KEY_BOOST = "boost"
        private const val KEY_TRIM = "trim"
        private const val KEY_EXPRESSION = "expression"
        private const val KEY_DIALOGUE = "dialogue"
        private const val KEY_HEADING = "heading"
        private const val KEY_CHARACTERS = "characters"
        private const val KEY_BLEND_WITH = "blendWith"
        private const val KEY_BLEND_PERCENT = "blendPercent"

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
                expressiveness = values[KEY_EXPRESSION]?.toIntOrNull()?.takeIf { it >= 0 } ?: defaults.expressiveness,
                dialogueVoices = decoded(values[KEY_DIALOGUE]),
                headingVoices = decoded(values[KEY_HEADING]),
                characterVoices = values[KEY_CHARACTERS]?.toBooleanStrictOrNull() ?: defaults.characterVoices,
                blendWith = values[KEY_BLEND_WITH]?.toIntOrNull()?.takeIf { it >= 0 },
                blendPercent = values[KEY_BLEND_PERCENT]?.toIntOrNull()?.takeIf { it in BLEND_PERCENTS }
                    ?: defaults.blendPercent,
            )
        }

        private fun encoded(voices: Map<Lang, VoiceId>): String = UrlText.encode(VoiceMaps.encode(voices))

        private fun decoded(value: String?): Map<Lang, VoiceId> = VoiceMaps.decode(value?.let(UrlText::decode))
    }
}
