// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/sherpa/LetterVoices.kt
// Purpose: What a Bengali voice that reads letters is given: its words spelled as said, at a storyteller's pace.

package io.github.skm0078.audora.speech.sherpa

import io.github.skm0078.audora.core.config.SpeechConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.speech.normalize.BengaliSpelling

/**
 * SYSPIN and Rasa read Bengali letters ([SherpaVoiceSpec.readsLetters]), so they say only what is written, and
 * Bengali spelling leaves out how a word ends and whether its "এ" is wide. Their words are respelled the way they
 * are said ([BengaliSpelling]), and they speak at [bengaliPace] of the asked rate, as both were recorded faster than
 * a story is told (ADR 2026-10-30-bengali-spoken-spelling). Every other voice, a Bengali one with espeak-ng
 * included, gets its text and rate as they are.
 *
 * Only marks are added to a word, so the voice says as many words as the page holds, in order, and the word
 * highlight needs nothing of this. The measured voice speed sees the slower pace as the voice's own.
 */
internal class LetterVoices(private val bengaliPace: Float = SpeechConfig().bengaliPace) {
    init {
        require(bengaliPace > 0f && bengaliPace.isFinite()) { "bengaliPace must be > 0, was $bengaliPace" }
    }

    /** The words [spec] is handed for [text]. */
    fun text(spec: SherpaVoiceSpec, text: String): String =
        if (spec.readsBengaliLetters) BengaliSpelling.respell(text) else text

    /** The rate [spec] speaks at when [rate] is asked. */
    fun rate(spec: SherpaVoiceSpec, rate: Float): Float = if (spec.readsBengaliLetters) rate * bengaliPace else rate

    private val SherpaVoiceSpec.readsBengaliLetters: Boolean get() = lang == Lang.BN && readsLetters
}
