// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/sherpa/ClosingMarks.kt
// Purpose: Closing quotes and brackets after a sentence's last mark move inside it, so no voice says them as a noise.

package io.github.skm0078.audora.speech.sherpa

/**
 * sherpa-onnx cuts its input after `.`, `?` and `!` and voices every piece, so `He said, “I am going home.”` ends
 * in a piece holding only `”`. KittenTTS says that piece as a short babble ("bububbaa" on a phone; measured on the
 * desktop with the app's Kitten voices: about 0.2 s of sound after the sentence, gone once the mark moves; Kokoro
 * stays silent). The closing marks after the last end mark move before it (`home”.`): every character is kept and
 * the sentence still falls at its end. Marks inside the sentence stay, since the piece after them holds words.
 * Styled voices get [StyledText.ending] instead, which already leaves no such piece.
 */
object ClosingMarks {
    private val AFTER_LAST_END = Regex("""([.?!…]+)((?:\s*["”’'»)\]])+)\s*$""")

    fun inside(text: String): String {
        val match = AFTER_LAST_END.find(text) ?: return text
        val (ends, closers) = match.destructured
        return text.substring(0, match.range.first) + closers.filterNot(Char::isWhitespace) + ends
    }
}
