// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/studio/BookVoice.kt
// Purpose: One book's voices and rendering for a whole-book run (studio, talking book, clip, export), built one way.

package io.github.skm0078.audora.playback.studio

import android.content.Context
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.VoiceShaping
import io.github.skm0078.audora.pdf.document.PageReading
import io.github.skm0078.audora.playback.book.BookText
import io.github.skm0078.audora.playback.voice.CharacterCast
import io.github.skm0078.audora.playback.voice.SpeechStyleStore
import io.github.skm0078.audora.playback.voice.SpeechVoices
import io.github.skm0078.audora.playback.voice.VoiceEngines
import io.github.skm0078.audora.playback.voice.forReading
import io.github.skm0078.audora.playback.voice.narratorBlend
import kotlinx.coroutines.runBlocking

/**
 * Studio mode, the talking book, a clip and the audiobook export each render a book with voices of their own, separate
 * from the playback service's so listening and rendering never share a model. They are built here as playback builds
 * its own (AudoraPlaybackService.onCreate): the narrator blend (plan item 75) and the book's character voices (plan
 * item 63), so the audio under a studio key, which names both, is the audio playback would render under it. The
 * listener's style is read once, for the voices and the rendering alike: the key carries it, so the audio must not
 * change under it mid-run. Feed every page to [page] before rendering it, as playback's PageLoader does, or quotes
 * keep the narrator's voice. Built on the worker thread (it reads the book's
 * settings); close it to free the voices.
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
    val engines: VoiceEngines
    val voices: SpeechVoices
    val rendering: BookRendering

    init {
        lateinit var made: SpeechVoices
        // BlendCache keeps playback and this run from building the blend at once.
        engines = VoiceEngines(context, blend = { made.narratorBlend(shaping) }) { shaping.expressiveness }
        made = SpeechVoices(engines)
        voices = made
        // The process-wide per-book cast (CharacterCasts): playback and this run share one map, so no voice is lost.
        val characters = CharacterCast.forReading(context, engines, { voices }) { shaping }.apply { open(id) }
        // Frees the voices if reading the book's settings fails or is interrupted: close() is never reached then.
        rendering = runCatching {
            runBlocking { factory.prepare(id, text, voices, RunSettings(normalRate, heard, characters)) }
        }.onFailure { voices.release() }.getOrThrow()
    }

    /**
     * One page, before any of its units renders: which start inside a quote and who speaks them. A quote left open at
     * the foot of the previous page carries on only when [reading] continues it, as in playback.
     */
    fun page(reading: PageReading) = rendering.page(reading.units, reading.continuesPreviousPage)

    override fun close() = voices.release()
}
