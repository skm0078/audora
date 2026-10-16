// File: core/src/main/kotlin/io/github/skm0078/audora/core/model/SpeechStyle.kt
// Purpose: How the voice speaks: its rate and the pace of the pauses between sentences (ADR-033).

package io.github.skm0078.audora.core.model

/** How long the silences between sentences and after paragraphs are; the values live in SpeechStyleConfig. */
enum class Pace { TIGHT, NORMAL, RELAXED }

/**
 * @property rate how fast the voice speaks, 1.0 = the voice's own speed. Applied when a sentence is synthesized
 *   (the model speaks faster or slower), never by stretching finished audio, so words stay clear (ADR-033).
 * @property pace the silences between sentences.
 */
data class SpeechStyle(val rate: Float = NORMAL_RATE, val pace: Pace = Pace.NORMAL) {
    companion object {
        const val NORMAL_RATE = 1f
    }
}
