// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/ExportVoice.kt
// Purpose: The audiobook export's voice (BookVoice) and what it can say about character voices for the book.

package io.github.skm0078.audora.playback.export

import android.content.Context
import io.github.skm0078.audora.core.config.AudiobookConfig
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.getOrNull
import io.github.skm0078.audora.playback.book.BookText
import io.github.skm0078.audora.playback.studio.BookRendering
import io.github.skm0078.audora.playback.studio.BookVoice
import io.github.skm0078.audora.playback.studio.RunSettings
import io.github.skm0078.audora.playback.voice.SpeechStyleStore
import io.github.skm0078.audora.playback.voice.SpeechVoices
import io.github.skm0078.audora.speech.sherpa.castSpeakers
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.runBlocking

/**
 * The book's voices built as for every whole-book run ([BookVoice]: book settings, narrator blend, character voices,
 * shaping read once), at the voice's own rate (the files go to apps with their own speed control). [cast] says, from
 * the languages read so far, whether characters (plan item 63) get voices at all, so the export never promises a cast
 * it cannot apply. Built on the worker thread (it reads the book's settings). Close it to free the voices.
 */
internal class ExportVoice(
    context: Context,
    style: SpeechStyleStore,
    factory: BookRendering.Factory,
    id: BookId,
    text: BookText,
    language: String?,
) : AutoCloseable {
    private val book =
        BookVoice(context, factory, id, text, RunSettings(normalRate = true, language = language), style = style)
    private val languages: MutableSet<Lang> = ConcurrentHashMap.newKeySet()
    private val castSizes = ConcurrentHashMap<Lang, Int>()
    val voices: SpeechVoices get() = book.voices
    val rendering: BookRendering get() = book.rendering

    /** What the export can say about character voices for the languages read so far. */
    fun cast(): CastInExport = CastInExport.of(book.shaping.characterVoices, languages, ::castSize)

    /**
     * The exporter reading through this voice: each page goes to the quote and speaker tracking, and its languages
     * to [cast]; [onCast] hears when that changes (the first page read, a first Hindi page in an English book).
     */
    fun exporter(config: AudiobookConfig, onCast: (CastInExport) -> Unit): AudiobookExporter =
        AudiobookExporter(rendering.live, config, rendering.reading) { units, continuesPreviousPage ->
            read(units, continuesPreviousPage, onCast)
        }

    override fun close() = book.close()

    private fun read(units: List<ReadingUnit>, continuesPreviousPage: Boolean, onCast: (CastInExport) -> Unit) {
        // As playback's PageLoader: a quote left open at the foot of the page before goes on when this page continues
        // that sentence.
        rendering.page(units, continuesPreviousPage)
        val before = cast()
        // Only the units the export speaks: a skipped heading in another script says nothing about the book.
        val spoken = rendering.reading.plays(units).map { it.lang }
        if (languages.addAll(spoken)) cast().takeIf { it != before }?.let(onCast)
    }

    /**
     * How many other speakers [lang]'s narrator has, read once per language (the run's voice choices are fixed). Only
     * a neural (sherpa) narrator can have any; the phone's TTS voices never do, and asking would build the neural
     * model. A language on its default resolves it here, as its first sentence is about to.
     */
    private fun castSize(lang: Lang): Int = castSizes.getOrPut(lang) {
        runBlocking { voices.voiceFor(lang) }.getOrNull()?.takeIf { it.engine == EngineId.SHERPA }
            ?.let { book.engines.bundled().getOrNull()?.engine?.castSpeakers(it)?.size }
            ?: 0
    }
}
