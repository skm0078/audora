// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/export/AudiobookExporterTest.kt
// Purpose: The exporter writes every sentence to its chapter's file and follows playback's failure rules.

package io.github.skm0078.audora.playback.export

import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.document.PdfPageError
import io.github.skm0078.audora.playback.TEST_SAMPLE_RATE
import io.github.skm0078.audora.playback.book.BookText
import io.github.skm0078.audora.playback.page
import io.github.skm0078.audora.playback.pcm
import io.github.skm0078.audora.playback.queue.SentenceRenderer
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.audio.PcmAudio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudiobookExporterTest {
    /** Pages of [sentences] sentences each; [failing] pages fail with the given error. */
    private class FakeBook(
        override val pageCount: Int,
        private val sentences: Int = 2,
        private val failing: Map<Int, PdfPageError> = emptyMap(),
    ) : BookText {
        override fun units(page: Int): Outcome<List<ReadingUnit>, PdfPageError> =
            failing[page]?.let { Outcome.Failure(it) } ?: Outcome.Success(page(page, sentences))

        override fun close() = Unit
    }

    /** Collects what each chapter's file would hold, as sample counts per write. */
    private class RecordingSink : ChapterAudioSink {
        val written = LinkedHashMap<Int, MutableList<Int>>()
        val rates = HashMap<Int, Int>()
        val aborted = ArrayList<Int>()

        override fun open(chapter: AudioChapter, sampleRate: Int): Outcome<ChapterAudioWriter, ExportError.Storage> {
            val writes = written.getOrPut(chapter.number) { ArrayList() }
            rates[chapter.number] = sampleRate
            return Outcome.Success(
                object : ChapterAudioWriter {
                    override val sampleRate = sampleRate

                    override fun write(audio: PcmAudio): Outcome<Unit, ExportError.Storage> {
                        writes.add(audio.samples.size)
                        return Outcome.Success(Unit)
                    }

                    override fun finish() = Outcome.Success(
                        ExportedChapter(chapter, "${chapter.number}.m4a", writes.sum() * 1_000L / sampleRate),
                    )

                    override fun abort() {
                        aborted.add(chapter.number)
                    }
                },
            )
        }
    }

    private val brokenVoice = SpeechError.VoiceUnavailable(
        EngineId.SHERPA,
        SpeechError.VoiceUnavailable.Reason.MODEL_FILES_MISSING,
    )

    private fun renderer(fail: (ReadingUnit) -> Boolean = { false }) = SentenceRenderer { unit ->
        if (fail(unit)) Outcome.Failure(brokenVoice) else Outcome.Success(pcm(100))
    }

    private val chapters = listOf(AudioChapter(1, 0, 1), AudioChapter(2, 2, 3))

    @Test
    fun `every sentence lands in its chapter's file`() {
        val sink = RecordingSink()
        val result = AudiobookExporter(renderer()).export(FakeBook(pageCount = 4), chapters, sink)

        val summary = (result as Outcome.Success).value
        assertEquals(mapOf(1 to List(4) { 100 }, 2 to List(4) { 100 }), sink.written)
        assertEquals(listOf("1.m4a", "2.m4a"), summary.chapters.map { it.name })
        assertEquals(800L, summary.durationMs)
    }

    @Test
    fun `a bad sentence is skipped and the export goes on`() {
        val sink = RecordingSink()
        val result = AudiobookExporter(renderer { it.page == 1 && it.index == 0 })
            .export(FakeBook(pageCount = 4), chapters, sink)

        val summary = (result as Outcome.Success).value
        assertEquals(1, summary.skippedSentences)
        assertEquals(3, sink.written.getValue(1).size)
    }

    @Test
    fun `a broken voice stops the export and deletes the chapter in progress`() {
        val sink = RecordingSink()
        val result = AudiobookExporter(renderer { it.page >= 2 }).export(FakeBook(pageCount = 4), chapters, sink)

        assertEquals(ExportError.VoiceFailed(brokenVoice), (result as Outcome.Failure).error)
        assertEquals(emptyList<Int>(), sink.aborted)
        assertTrue("chapter 2 never opened: nothing rendered", 2 !in sink.written)
    }

    @Test
    fun `a voice that breaks mid-chapter aborts that chapter's file`() {
        val sink = RecordingSink()
        val result = AudiobookExporter(renderer { it.page == 3 })
            .export(FakeBook(pageCount = 4, sentences = 3), chapters, sink)

        assertTrue(result is Outcome.Failure)
        assertEquals(listOf(2), sink.aborted)
    }

    @Test
    fun `an unreadable page is skipped, a closed book stops the export`() {
        val skipped = FakeBook(pageCount = 4, failing = mapOf(1 to PdfPageError.ExtractionFailed(1, "bad")))
        val ok = AudiobookExporter(renderer()).export(skipped, chapters, RecordingSink())
        assertEquals(1, (ok as Outcome.Success).value.skippedPages)

        val closed = FakeBook(pageCount = 4, failing = mapOf(2 to PdfPageError.BookClosed))
        val stopped = AudiobookExporter(renderer()).export(closed, chapters, RecordingSink())
        assertEquals(ExportError.BookUnreadable(2, PdfPageError.BookClosed), (stopped as Outcome.Failure).error)
    }

    @Test
    fun `a chapter with nothing to read leaves no file`() {
        val sink = RecordingSink()
        val result = AudiobookExporter(renderer()).export(FakeBook(pageCount = 4, sentences = 0), chapters, sink)

        assertEquals(2, (result as Outcome.Success).value.silentChapters)
        assertTrue(sink.written.isEmpty())
    }

    @Test
    fun `sentences are resampled to the rate the chapter's file opened with`() {
        var first = true
        val mixed = SentenceRenderer {
            val rate = if (first) TEST_SAMPLE_RATE else TEST_SAMPLE_RATE / 2
            first = false
            Outcome.Success(PcmAudio(ShortArray(rate / 10), rate))
        }
        val sink = RecordingSink()
        AudiobookExporter(mixed).export(FakeBook(pageCount = 1), listOf(AudioChapter(1, 0, 0)), sink)

        assertEquals(TEST_SAMPLE_RATE, sink.rates.getValue(1))
        assertEquals(listOf(100, 100), sink.written.getValue(1))
    }

    @Test
    fun `cancelling aborts the chapter in progress and keeps finished ones`() {
        val sink = RecordingSink()
        val cancelling = SentenceRenderer { unit ->
            if (unit.page == 2 && unit.index == 1) throw InterruptedException()
            Outcome.Success(pcm(100))
        }
        val done = ArrayList<Int>()
        val listener = object : ExportListener {
            override fun onChapterDone(chapter: ExportedChapter) {
                done.add(chapter.chapter.number)
            }
        }

        val thrown = runCatching {
            AudiobookExporter(cancelling).export(FakeBook(pageCount = 4), chapters, sink, listener)
        }.exceptionOrNull()

        assertTrue(thrown is InterruptedException)
        assertEquals(listOf(1), done)
        assertEquals(listOf(2), sink.aborted)
    }
}
