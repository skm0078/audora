// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/AudiobookExporter.kt
// Purpose: Renders a book sentence by sentence into one audio file per chapter, offline (ADR-034).

package io.github.skm0078.audora.playback.export

import io.github.skm0078.audora.core.config.AudiobookConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.ReadingModes
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.errorOrNull
import io.github.skm0078.audora.core.result.getOrNull
import io.github.skm0078.audora.pdf.document.PdfPageError
import io.github.skm0078.audora.playback.book.BookText
import io.github.skm0078.audora.playback.queue.SentenceRenderer
import io.github.skm0078.audora.playback.queue.UnitKey
import io.github.skm0078.audora.playback.reading.UnitReading
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.audio.PcmAudio

/**
 * Blocking, and thread-confined like [BookText]: run it on one worker thread. Cancel by interrupting that
 * thread: the chapter in progress is aborted (its partial file deleted) and [InterruptedException] is rethrown;
 * chapters already finished stay, so a cancelled export of a long book still leaves its first chapters.
 *
 * Sentences go through the same [SentenceRenderer] as playback (`EngineRenderer`), so the files sound exactly
 * like listening in the app: same voices per language, normalizer and pauses; [reading] (the listener's reading modes)
 * picks the units the same way the playlist does.
 * Failures follow playback's rules: a sentence that fails is skipped, [AudiobookConfig.renderRetryLimit] in a row
 * stop the export; a page that cannot be extracted is skipped, a book that can no longer be opened stops it.
 */
class AudiobookExporter(
    private val renderer: SentenceRenderer,
    private val config: AudiobookConfig = AudiobookConfig(),
    private val reading: UnitReading = UnitReading(ReadingModes()),
    private val onPage: (units: List<ReadingUnit>, continuesPreviousPage: Boolean) -> Unit = { _, _ -> },
) {
    fun export(
        text: BookText,
        chapters: List<AudioChapter>,
        sink: ChapterAudioSink,
        listener: ExportListener = ExportListener.NONE,
        logContext: String = "",
    ): Outcome<ExportSummary, ExportError> {
        val run = Run(text, sink, listener, logContext)
        for (chapter in chapters) {
            val stop = run.chapter(chapter)
            if (stop != null) {
                AudoraLog.w(TAG, "Export stopped in chapter ${chapter.number}, $logContext: ${stop.message}")
                return Outcome.Failure(stop)
            }
        }
        val summary = run.summary()
        AudoraLog.i(
            TAG,
            "Exported ${summary.chapters.size} files, ${summary.durationMs / MS_PER_SECOND} s, " +
                "skipped ${summary.skippedSentences} sentences and ${summary.skippedPages} pages, $logContext",
        )
        return Outcome.Success(summary)
    }

    /** One export's mutable state, so [AudiobookExporter] itself can be reused. */
    private inner class Run(
        private val text: BookText,
        private val sink: ChapterAudioSink,
        private val listener: ExportListener,
        private val logContext: String,
    ) {
        private val done = ArrayList<ExportedChapter>()
        private var skippedSentences = 0
        private var skippedPages = 0
        private var silentChapters = 0
        private var failuresInARow = 0
        private var writer: ChapterAudioWriter? = null

        fun summary() = ExportSummary(done.toList(), skippedSentences, skippedPages, silentChapters)

        /** Null when the chapter is done; the reason to stop otherwise. */
        fun chapter(chapter: AudioChapter): ExportError? {
            val stop = try {
                chapter.pages.firstNotNullOfOrNull { page -> page(chapter, page) }
            } catch (interrupted: InterruptedException) {
                discard()
                throw interrupted
            }
            if (stop != null) {
                discard()
                return stop
            }
            return finish(chapter)
        }

        private fun page(chapter: AudioChapter, page: Int): ExportError? {
            fun sentenceOf(unit: ReadingUnit) = sentence(chapter, unit)
            // The units playback reads with the listener's reading modes, so the file sounds like the player.
            // As playback's page loader: the page's units with whether it opens inside the page before's sentence,
            // so a quote left open across the break keeps its dialogue voice.
            val stop = when (val loaded = text.reading(page)) {
                is Outcome.Success -> {
                    onPage(loaded.value.units, loaded.value.continuesPreviousPage)
                    reading.plays(loaded.value.units).firstNotNullOfOrNull(::sentenceOf)
                }

                is Outcome.Failure -> pageFailed(page, loaded.error)
            }
            if (stop == null) listener.onPageDone(chapter, page)
            return stop
        }

        private fun pageFailed(page: Int, error: PdfPageError): ExportError? = when (error) {
            is PdfPageError.ExtractionFailed, is PdfPageError.PageOutOfRange -> {
                AudoraLog.w(TAG, "Page skipped, $logContext page=$page: $error")
                skippedPages++
                null
            }

            is PdfPageError.ReopenFailed, PdfPageError.BookClosed -> ExportError.BookUnreadable(page, error)
        }

        private fun sentence(chapter: AudioChapter, unit: ReadingUnit): ExportError? {
            if (Thread.interrupted()) throw InterruptedException("Export cancelled")
            return when (val rendered = renderer.render(unit)) {
                is Outcome.Success -> {
                    failuresInARow = 0
                    write(chapter, rendered.value)
                }

                is Outcome.Failure -> renderFailed(unit, rendered.error)
            }
        }

        private fun renderFailed(unit: ReadingUnit, error: SpeechError): ExportError? {
            skippedSentences++
            failuresInARow++
            AudoraLog.w(TAG, "${UnitKey.of(unit)} skipped ($failuresInARow in a row), $logContext: ${error.message}")
            return if (failuresInARow >= config.renderRetryLimit) ExportError.VoiceFailed(error) else null
        }

        private fun write(chapter: AudioChapter, audio: PcmAudio): ExportError? {
            if (audio.samples.isEmpty()) return null
            // Voices differ in rate (Piper 22.05 kHz, Kokoro 24 kHz); a file has one, set by its first sentence.
            return when (val open = writerFor(chapter, audio.sampleRate)) {
                is Outcome.Success -> open.value.write(audio.resampled(open.value.sampleRate)).errorOrNull()
                is Outcome.Failure -> open.error
            }
        }

        private fun writerFor(chapter: AudioChapter, sampleRate: Int): Outcome<ChapterAudioWriter, ExportError> =
            writer?.let { Outcome.Success(it) }
                ?: sink.open(chapter, sampleRate).also { opened -> writer = opened.getOrNull() }

        private fun finish(chapter: AudioChapter): ExportError? {
            val open = writer ?: run {
                silentChapters++
                AudoraLog.i(TAG, "Chapter ${chapter.number} has nothing to read, no file, $logContext")
                return null
            }
            writer = null
            return when (val finished = open.finish()) {
                is Outcome.Success -> {
                    done.add(finished.value)
                    listener.onChapterDone(finished.value)
                    null
                }

                is Outcome.Failure -> {
                    open.abort()
                    finished.error
                }
            }
        }

        private fun discard() {
            writer?.abort()
            writer = null
        }
    }

    private companion object {
        const val TAG = "Export.Audiobook"
        const val MS_PER_SECOND = 1_000L
    }
}
