// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/epub/CopyJob.kt
// Purpose: Makes one book's accessible copy or talking book and saves it to Documents/Audora (items 69, 76).

package io.github.skm0078.audora.playback.epub

import android.content.Context
import io.github.skm0078.audora.core.config.AudiobookConfig
import io.github.skm0078.audora.core.config.EpubConfig
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.flatMap
import io.github.skm0078.audora.core.result.map
import io.github.skm0078.audora.pdf.document.PdfPageError
import io.github.skm0078.audora.playback.R
import io.github.skm0078.audora.playback.book.BookText
import io.github.skm0078.audora.playback.book.OpenedBook
import io.github.skm0078.audora.playback.export.AacAudioSink
import io.github.skm0078.audora.playback.export.AudioChapters
import io.github.skm0078.audora.playback.export.AudiobookExporter
import io.github.skm0078.audora.playback.export.AudiobookFolder
import io.github.skm0078.audora.playback.export.ExportError
import io.github.skm0078.audora.playback.export.ExportProgress
import io.github.skm0078.audora.playback.export.LocalChapterFiles
import io.github.skm0078.audora.playback.studio.BookRendering
import io.github.skm0078.audora.playback.studio.StudioSentenceRenderer
import io.github.skm0078.audora.playback.studio.StudioStore
import java.io.File
import java.time.Instant
import java.util.UUID

/** What was saved: the file names (safe to show) and the pages that had no text. */
internal data class SavedCopy(val names: List<String>, val skippedPages: Int)

/**
 * Blocking, on the service's worker thread, which owns [book] (thread-confined like its `BookText`). Cancel by
 * interrupting that thread: [InterruptedException] propagates and nothing partial is left in Documents.
 *
 * @param title the book's title as shown (never empty).
 * @param onProgress the step's label and its percentage, in steps of [EpubConfig.progressStepPercent].
 */
internal class CopyJob(
    private val context: Context,
    private val book: OpenedBook,
    private val title: String,
    private val config: EpubConfig,
    private val onProgress: (step: String, percent: Int) -> Unit,
) {
    private val logContext = "book=${book.book.id.logLabel}"
    private val chapters = AudioChapters.plan(book.text.chapterStarts(), book.text.pageCount, AudiobookConfig())
    private val builder = DocumentBuilder(config) { chapter ->
        context.getString(R.string.copy_pages, chapter.firstPage + 1, chapter.lastPage + 1)
    }
    private val files = SharedDocuments(context.contentResolver, config.folder)
    private val writer = EpubWriter(
        CopyLabels(
            contents = context.getString(R.string.copy_contents),
            pages = context.getString(R.string.copy_page_list),
            notes = context.getString(R.string.copy_notes),
        ),
    )
    private val fileStem = AudiobookFolder
        .folderName(title, AudiobookConfig(folderNameMaxChars = config.fileNameMaxChars))
        .ifEmpty { context.getString(R.string.export_untitled) }

    /** Item 69: reads every page (OCR on scanned ones), then saves `<title>.epub` and `<title>.txt`. */
    fun accessibleCopy(): Outcome<SavedCopy, CopyError> {
        val pageDone = progress(R.string.copy_step_reading)
        val pages = BookPages { _, _ -> pageDone() }.read(book.text.pageCount, book.text::units, logContext)
        return pages.flatMap { loaded ->
            val doc = builder.build(title, chapters, loaded.units)
                ?: return@flatMap Outcome.Failure(CopyError.NothingToCopy)
            val epub = "$fileStem.$EPUB_EXTENSION"
            val text = "$fileStem.$TEXT_EXTENSION"
            save(epub, doc, CopyKind.ACCESSIBLE, emptyMap())
                .flatMap { files.save(text, TEXT_MIME) { out -> out.write(PlainTextCopy().write(doc).toByteArray()) } }
                .map { SavedCopy(listOf(epub, text), loaded.skippedPages) }
        }
    }

    /**
     * Item 76: renders the book as the audiobook export does (studio audio first when it is ready), records where
     * each sentence lands in its chapter's audio, then packs text, audio and timings into `<title> - talking
     * book.epub`. Chapter audio is staged in the cache folder and deleted afterwards.
     */
    fun talkingBook(rendering: BookRendering, store: StudioStore): Outcome<SavedCopy, CopyError> {
        val staging = File(context.cacheDir, STAGING_FOLDER).apply {
            deleteRecursively()
            mkdirs()
        }
        return try {
            voice(rendering, store, staging)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun voice(rendering: BookRendering, store: StudioStore, staging: File): Outcome<SavedCopy, CopyError> {
        val text = RecordingText(book.text)
        val pageDone = progress(R.string.copy_step_voicing)
        val recorder = OverlayRecorder(staging) { _, _ -> pageDone() }
        val renderer = StudioSentenceRenderer(rendering.live, store) { rendering.key }
        val sink = AacAudioSink(LocalChapterFiles(staging), chapters.size)
        val exported = AudiobookExporter(renderer, AudiobookConfig(), rendering.reading)
            .export(text, chapters, sink, recorder, logContext)
        return when (exported) {
            is Outcome.Failure -> Outcome.Failure(exported.error.toCopyError())
            is Outcome.Success -> pack(text.read, recorder)
        }
    }

    private fun pack(units: Map<Int, List<ReadingUnit>>, recorder: OverlayRecorder): Outcome<SavedCopy, CopyError> {
        val doc = builder.build(title, chapters, units, recorder.voiced)
            ?: return Outcome.Failure(CopyError.NothingToCopy)
        val name = context.getString(R.string.copy_talking_book_file, fileStem) + ".$EPUB_EXTENSION"
        return save(name, doc, CopyKind.TALKING_BOOK, recorder.chapters).map { SavedCopy(listOf(name), 0) }
    }

    private fun save(name: String, doc: BookDocument, kind: CopyKind, audio: Map<Int, ChapterAudio>) =
        files.save(name, EPUB_MIME) { out ->
            val summary = if (audio.isEmpty()) R.string.copy_summary_text else R.string.copy_summary_audio
            val identifier = UUID.nameUUIDFromBytes("audora:$kind:${book.book.id.value}".toByteArray())
            writer.write(out, doc, EpubMeta("urn:uuid:$identifier", Instant.now(), context.getString(summary)), audio)
        }

    /** A step's page counter: call it once per page done. */
    private fun progress(step: Int): () -> Unit {
        val label = context.getString(step)
        val counter = ExportProgress(chapters, config.progressStepPercent)
        return { counter.onPageDone()?.let { onProgress(label, it) } }
    }

    private fun ExportError.toCopyError(): CopyError = when (this) {
        is ExportError.Storage -> CopyError.Storage(detail)
        is ExportError.VoiceFailed -> CopyError.VoiceFailed(message)
        is ExportError.BookUnreadable -> CopyError.BookUnreadable(page)
    }

    /** The book's text as the exporter reads it, keeping every page's units for the copy's text (one pass). */
    private class RecordingText(private val text: BookText) : BookText by text {
        val read = HashMap<Int, List<ReadingUnit>>()

        override fun units(page: Int): Outcome<List<ReadingUnit>, PdfPageError> =
            text.units(page).also { if (it is Outcome.Success) read[page] = it.value }
    }

    private companion object {
        const val EPUB_EXTENSION = "epub"
        const val TEXT_EXTENSION = "txt"
        const val EPUB_MIME = "application/epub+zip"
        const val TEXT_MIME = "text/plain"
        const val STAGING_FOLDER = "talking-book"
    }
}
