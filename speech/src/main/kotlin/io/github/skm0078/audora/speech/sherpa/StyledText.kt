// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/sherpa/StyledText.kt
// Purpose: The text a styled VITS model (AI4Bharat Rasa, ADR-057) is given, so a sentence never ends in a noise.

package io.github.skm0078.audora.speech.sherpa

import io.github.skm0078.audora.core.model.Lang

/**
 * sherpa-onnx cuts its input after `.`, `?` and `!`, whatever the model's own punctuation; for the Rasa export a mark
 * at the very end leaves an empty last piece that the model voices as a short noise (the export's model card). The
 * end mark is dropped, and Hindi and Bengali get their danda (।) instead, which the model was trained on and which
 * sherpa does not cut at, so the sentence still falls at its end. Marks inside the sentence are kept.
 */
object StyledText {
    private const val DANDA = "।"
    private val END_MARKS = Regex("[.?!]+[\\s\"”’'»)\\]]*$")

    fun ending(text: String, lang: Lang): String {
        val match = END_MARKS.find(text) ?: return text
        val body = text.substring(0, match.range.first).trimEnd()
        val closers = match.value.trimStart('.', '?', '!').trim()
        val end = if (lang == Lang.EN || body.endsWith(DANDA)) "" else DANDA
        return body + end + closers
    }
}
