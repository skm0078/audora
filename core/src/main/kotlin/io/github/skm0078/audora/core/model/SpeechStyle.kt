// File: core/src/main/kotlin/io/github/skm0078/audora/core/model/SpeechStyle.kt
// Purpose: How the voice speaks: its rate, the pace of its pauses and the pauses at strong punctuation (ADR-033).

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
 */
data class SpeechStyle(val rate: Float = NORMAL_RATE, val pace: Pace = Pace.NORMAL, val clausePauses: Boolean = true) {
    companion object {
        const val NORMAL_RATE = 1f
    }
}
