// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/video/QuoteVoice.kt
// Purpose: A share card's quote read aloud in the book's own voice, with when each of its words is said.

package io.github.skm0078.audora.playback.export.video

import android.content.Context
import io.github.skm0078.audora.core.config.QuoteVideoConfig
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.share.CardQuote
import io.github.skm0078.audora.core.share.QuoteReading
import io.github.skm0078.audora.core.share.SpokenSentence
import io.github.skm0078.audora.pdf.document.PdfPageError
import io.github.skm0078.audora.playback.book.BookText
import io.github.skm0078.audora.playback.export.AudioChapter
import io.github.skm0078.audora.playback.export.ChapterAudioWriter
import io.github.skm0078.audora.playback.export.ExportError
import io.github.skm0078.audora.playback.export.ExportedChapter
import io.github.skm0078.audora.playback.export.atPitch
import io.github.skm0078.audora.playback.studio.BookRendering
import io.github.skm0078.audora.playback.studio.BookVoice
import io.github.skm0078.audora.playback.studio.RunSettings
import io.github.skm0078.audora.speech.audio.PcmAudio
import io.github.skm0078.audora.speech.audio.WordTimes

/** The quote as said: its audio and each word's start in it, ascending, one per word of the quote. */
class SpokenQuote(val audio: PcmAudio, val wordStartsMs: List<Long>)

/**
 * Reads the quote's own words, not the page's sentences: the card may have cut them to 60 words, and the video must
 * say what it shows. Each sentence goes through the renderer playback builds for the book (its voices, speed, fixes
 * and the listener's style, as an audio clip), then the listener's pitch as a clip has it. Blocking, on one worker
 * thread; throws [InterruptedException] on cancel.
 */
internal class QuoteVoice(
    private val context: Context,
    private val factory: BookRendering.Factory,
    private val config: QuoteVideoConfig,
    private val wordTimes: WordTimes = WordTimes(),
) {
    /** [quote] from zero-based [page] of [book]; a failure says why, for the log. */
    fun speak(book: Book, quote: String, page: Int): Outcome<SpokenQuote, String> {
        val reading = QuoteReading.of(quote)
        if (reading.sentences.isEmpty()) return Outcome.Failure("the quote has no words")
        val text = QuoteOnlyText(page)
        return BookVoice(context, factory, book.id, text, RunSettings(language = book.language)).use { voice ->
            val units = unitsOf(reading.sentences, page)
            voice.rendering.page(units, continuesPreviousPage = false)
            val parts = ArrayList<PcmAudio>(units.size)
            // The first sentence the voice cannot read ends it: a video with a silent gap would be worse than none.
            val failed = units.firstNotNullOfOrNull { unit ->
                when (val rendered = voice.rendering.live.render(unit)) {
                    is Outcome.Success -> null.also { parts += rendered.value }
                    is Outcome.Failure -> rendered.error.message
                }
            }
            if (failed != null) {
                Outcome.Failure(failed)
            } else {
                Outcome.Success(joined(reading, parts, voice.rendering.style.pitch))
            }
        }
    }

    /** One unit per sentence, on [page] so the book's settings for it apply; its script picks its voice. */
    private fun unitsOf(sentences: List<String>, page: Int): List<ReadingUnit> {
        var offset = 0
        return sentences.mapIndexed { index, sentence ->
            val start = offset
            offset += sentence.length + 1
            ReadingUnit(
                page = page,
                index = index,
                charStart = start,
                charEnd = start + sentence.length,
                text = sentence,
                lang = CardQuote.scriptOf(sentence),
                pauseAfterMs = config.sentenceGapMs,
                bounds = null,
            )
        }
    }

    /** The sentences at the first one's rate with a breath between them, timed, then at the listener's pitch. */
    private fun joined(reading: QuoteReading, parts: List<PcmAudio>, pitch: Float): SpokenQuote {
        val rate = parts.first().sampleRate
        val spoken = ArrayList<SpokenSentence>(parts.size)
        val audio = ArrayList<PcmAudio>(parts.size)
        var startMs = 0L
        parts.forEachIndexed { index, part ->
            val sentence = part.resampled(rate)
            spoken += SpokenSentence(startMs, sentence.durationMs, wordTimes.of(reading.sentences[index], sentence))
            val padded = if (index < parts.lastIndex) sentence.withSilenceAfter(config.sentenceGapMs) else sentence
            audio += padded
            startMs += padded.durationMs
        }
        return SpokenQuote(pitched(PcmAudio.concat(audio), pitch), reading.wordStarts(spoken))
    }

    /** Sonic keeps the length when it moves the pitch, so the word starts still hold. */
    private fun pitched(audio: PcmAudio, pitch: Float): PcmAudio {
        val collected = CollectedAudio(audio.sampleRate)
        val writer = collected.atPitch(pitch)
        writer.write(audio)
        writer.finish()
        return collected.audio() ?: audio
    }

    /** A writer that keeps what it is given, so the export's pitch shifter can be used on audio in memory. */
    private class CollectedAudio(override val sampleRate: Int) : ChapterAudioWriter {
        private val parts = mutableListOf<PcmAudio>()

        override fun write(audio: PcmAudio): Outcome<Unit, ExportError.Storage> {
            parts += audio
            return Outcome.Success(Unit)
        }

        override fun finish(): Outcome<ExportedChapter, ExportError.Storage> =
            Outcome.Success(ExportedChapter(AudioChapter(number = 1, firstPage = 0, lastPage = 0), NAME, 0L))

        override fun abort() = parts.clear()

        /** All it was given, joined; null if nothing came. */
        fun audio(): PcmAudio? = if (parts.isEmpty()) null else PcmAudio.concat(parts)

        private companion object {
            const val NAME = "quote"
        }
    }

    /**
     * The book's settings and voices apply to a quote without opening the book (a locked PDF needs no password here):
     * its text is the quote, and it has no chapters or scanned pages to mark.
     */
    private class QuoteOnlyText(quotePage: Int) : BookText {
        override val pageCount: Int = quotePage + 1

        override fun units(page: Int): Outcome<List<ReadingUnit>, PdfPageError> = Outcome.Success(emptyList())

        override fun close() = Unit
    }
}
