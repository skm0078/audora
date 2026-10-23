// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/delivery/SceneMoods.kt
// Purpose: A sad or frightened line colours the plain sentence after it in its paragraph, as a reader keeps a mood.

package io.github.skm0078.audora.speech.delivery

import io.github.skm0078.audora.core.model.Feeling

/**
 * A reader does not drop a sad voice the moment the cue word is past: "মিনু কেঁদে ফেলল। এত বছর পরে দেখা।" keeps
 * the sorrow into the second sentence. Only the lasting moods carry ([LASTING]: sad, scared), only to the very next
 * sentence of the same paragraph, and only when that sentence asks for no feeling of its own. A shout, a laugh or a
 * surprise is a moment and never carries. Pure.
 */
object SceneMoods {
    private val LASTING = setOf(Feeling.SAD, Feeling.SCARED)

    /** For one paragraph's sentences in order: the mood each one carries from the sentence before, null for none. */
    fun of(paragraph: List<String>): List<Feeling?> {
        val own = paragraph.map(SentenceFeelings::of)
        return own.mapIndexed { i, feeling ->
            own.getOrNull(i - 1)?.takeIf { feeling == Feeling.NEUTRAL && it in LASTING }
        }
    }
}
