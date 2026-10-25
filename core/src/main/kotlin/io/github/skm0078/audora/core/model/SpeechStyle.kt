// File: core/src/main/kotlin/io/github/skm0078/audora/core/model/SpeechStyle.kt
// Purpose: How the voice speaks (rate, pitch, pace, pauses at strong punctuation; ADR-033, ADR-067) and what it
//          reads (ADR-035).

package io.github.skm0078.audora.core.model

/** How long the silences between sentences and after paragraphs are; the values live in SpeechStyleConfig. */
enum class Pace { TIGHT, NORMAL, RELAXED }

/**
 * Punctuation inside a sentence that gets its own pause when [SpeechStyle.clausePauses] is on (RI-6, ADR-035).
 * Commas are not among them: the sentence is cut only where a reader would stop, so the voice keeps its intonation.
 */
enum class ClauseMark { DASH, COLON, SEMICOLON, BRACKET, QUOTE, ELLIPSIS, DANDA }

/**
 * @property rate how fast the voice speaks, 1.0 = the voice's own speed. Applied when a sentence is synthesized
 *   (the model speaks faster or slower), never by stretching finished audio, so words stay clear (ADR-033).
 * @property pace the silences between sentences.
 * @property clausePauses pause at dashes, colons, semicolons, brackets, quotes and ellipses, and read brackets a
 *   little quieter (RI-6). A switch so the listener can compare both by ear.
 * @property modes what is read, announced or skipped (tables, footnotes, equations, captions, unreadable text).
 *   Owned by the playback service with the rest of the style, so a change applies from the sentence playing.
 * @property shaping what is done to the voice's audio: even loudness, boost, pause trim (plan items 9, 10).
 * @property pitch how high the voice sounds, 1.0 = the voice's own pitch. Unlike [rate] it is applied to finished
 *   audio (the player's Sonic, and the same processor before an exported file's encoder), so changing it renders
 *   nothing again and studio audio stays valid (ADR-067).
 */
data class SpeechStyle(
    val rate: Float = NORMAL_RATE,
    val pace: Pace = Pace.NORMAL,
    val clausePauses: Boolean = true,
    val modes: ReadingModes = ReadingModes(),
    val shaping: VoiceShaping = VoiceShaping(),
    val pitch: Float = NORMAL_PITCH,
) {
    companion object {
        const val NORMAL_RATE = 1f
        const val NORMAL_PITCH = 1f
    }
}
