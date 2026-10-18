// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/studio/BookVoice.kt
// Purpose: One book's voices and rendering for a whole-book run (studio, talking book, clip, export), built one way.

package io.github.skm0078.audora.playback.studio

import android.content.Context
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.VoiceShaping
import io.github.skm0078.audora.playback.book.BookText
import io.github.skm0078.audora.playback.voice.SpeechStyleStore
import io.github.skm0078.audora.playback.voice.SpeechVoices
import io.github.skm0078.audora.playback.voice.VoiceEngines
import kotlinx.coroutines.runBlocking

/**
 * Studio mode, the talking book, a clip and the audiobook export each render a book with voices of their own, separate
 * from the playback service's so listening and rendering never share a model. They are built here one way, so the
 * audio under a studio key is the audio playback would render under it. The listener's style is read once, for the
 * voices and the rendering alike: the key carries it, so the audio must not change under it mid-run. Built on the
 * worker thread (it reads the book's settings); close it to free the voices.
 *
 * @param normalRate the voice's own rate instead of any speed, for files played in apps with their own speed control.
 */
internal class BookVoice(
    context: Context,
    factory: BookRendering.Factory,
    id: BookId,
    text: BookText,
    normalRate: Boolean,
    style: SpeechStyleStore = SpeechStyleStore.forContext(context),
) : AutoCloseable {
    private val heard = style.current
    val shaping: VoiceShaping = heard.shaping
    val engines = VoiceEngines(context) { shaping.expressiveness }
    val voices = SpeechVoices(engines)

    // Frees the voices if reading the book's settings fails or is interrupted: close() is never reached then.
    val rendering: BookRendering = runCatching {
        runBlocking { factory.prepare(id, text, voices, normalRate, global = heard) }
    }.onFailure { voices.release() }
        .getOrThrow()

    override fun close() = voices.release()
}
