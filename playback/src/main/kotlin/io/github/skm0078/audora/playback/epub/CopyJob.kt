// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/epub/CopyJob.kt
// Purpose: Makes one book's accessible copy or talking book and saves it to Documents/ListenLock (items 69, 76).

package io.github.skm0078.audora.playback.epub

import android.content.Context
import io.github.skm0078.audora.core.config.AudiobookConfig
import io.github.skm0078.audora.core.config.EpubConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.flatMap
import io.github.skm0078.audora.core.result.map
import io.github.skm0078.audora.pdf.document.PageReading
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
import io.github.skm0078.audora.playback.export.withPitch
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
 * interrupting that thread: [InterruptedException] propagates and nothing partial is left in Documents (staged
 * talking-book chapters stay; [dropStaging] deletes them).
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
            save(epub, doc, CopyKind.ACCESSIBLE, emptyMap()).flatMap { savedEpub ->
                files.save(text, TEXT_MIME) { out -> out.write(PlainTextCopy().write(doc).toByteArray()) }
                    .map { savedText -> SavedCopy(listOf(savedEpub, savedText), loaded.skippedPages) }
            }
        }
    }

    /**
     * Item 76: renders the book as the audiobook export does (studio audio first when it is ready), records where
     * each sentence lands in its chapter's audio, then packs text, audio and timings into `<title> - talking
     * book.epub`. Chapter audio is staged in the cache folder, per book and rendering signature ([StagedChapters]):
     * kept when a run stops early (Android's time limit, a full disk, a failed voice), so the next start renders only
     * the missing chapters; deleted once the book is saved. [onChapter] runs after each chapter is staged.
     */
    fun talkingBook(
        rendering: BookRendering,
        store: StudioStore,
        onChapter: () -> Unit = {},
    ): Outcome<SavedCopy, CopyError> {
        val root = File(context.cacheDir, STAGING_FOLDER)
        // The pitch is in the folder's key too: chapters staged at another pitch are not mixed in (ADR-067).
        val signature = withPitch(rendering.key.signature.hash, rendering.style.pitch)
        val staging = StagedChapters.folderFor(root, book.book.id.value, signature)
        val result = voice(rendering, store, staging, onChapter)
        if (result is Outcome.Success) staging.deleteRecursively()
        return result
    }

    private fun voice(
        rendering: BookRendering,
        store: StudioStore,
        staging: File,
        onChapter: () -> Unit,
    ): Outcome<SavedCopy, CopyError> {
        val text = RecordingText(book.text)
        val pageDone = progress(R.string.copy_step_voicing)
        val staged = StagedChapters(staging)
        val recorder = OverlayRecorder(staging, onPage = { _, _ -> pageDone() }) { chapter ->
            staged.save(chapter)
            onChapter()
        }
        val restored = staged.load().filterKeys { number -> chapters.any { it.number == number } }
        recorder.restore(restored.values)
        val remaining = chapters.filter { it.number !in restored }
        chapters.filter { it.number in restored }.forEach { chapter -> repeat(chapter.pages.count()) { pageDone() } }
        if (restored.isNotEmpty()) {
            AudoraLog.i(TAG, "Talking book goes on: ${restored.size} of ${chapters.size} chapters staged, $logContext")
        }
        val renderer = StudioSentenceRenderer(rendering.live, store) { rendering.key }
        val sink = AacAudioSink(LocalChapterFiles(staging), chapters.size, pitch = rendering.style.pitch)
        val exported = AudiobookExporter(renderer, AudiobookConfig(), rendering.reading, rendering::page)
            .export(text, remaining, sink, recorder, logContext)
        return when (exported) {
            is Outcome.Failure -> Outcome.Failure(exported.error.toCopyError())
            is Outcome.Success -> pack(text.allPages(), recorder)
        }
    }

    private fun pack(units: Map<Int, List<ReadingUnit>>, recorder: OverlayRecorder): Outcome<SavedCopy, CopyError> {
        val doc = builder.build(title, chapters, units, recorder.voiced)
            ?: return Outcome.Failure(CopyError.NothingToCopy)
        val name = context.getString(R.string.copy_talking_book_file, fileStem) + ".$EPUB_EXTENSION"
        return save(name, doc, CopyKind.TALKING_BOOK, recorder.chapters).map { saved -> SavedCopy(listOf(saved), 0) }
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
        private val read = HashMap<Int, List<ReadingUnit>>()

        override fun units(page: Int): Outcome<List<ReadingUnit>, PdfPageError> =
            text.units(page).also { if (it is Outcome.Success) read[page] = it.value }

        // The exporter reads pages through this, not units: without it nothing is recorded and pages are read twice.
        override fun reading(page: Int): Outcome<PageReading, PdfPageError> =
            text.reading(page).also { if (it is Outcome.Success) read[page] = it.value.units }

        /** Every page's units: those of staged chapters, not read this run, are read now (OCR comes from its cache). */
        fun allPages(): Map<Int, List<ReadingUnit>> {
            for (page in 0 until text.pageCount) if (page !in read) units(page)
            return read
        }
    }

    companion object {
        /** Deletes every staged talking-book chapter: the listener cancelled, so nothing is kept for later. */
        fun dropStaging(context: Context) {
            File(context.cacheDir, STAGING_FOLDER).deleteRecursively()
        }

        private const val EPUB_EXTENSION = "epub"
        private const val TEXT_EXTENSION = "txt"
        private const val EPUB_MIME = "application/epub+zip"
        private const val TEXT_MIME = "text/plain"
        private const val STAGING_FOLDER = "talking-book"
        private const val TAG = "Copy.Job"
    }
}
