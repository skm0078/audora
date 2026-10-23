// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/browse/OutlineChapters.kt
// Purpose: Reads a book's chapters from its PDF outline when a media browser opens the book (plan item 34).

package io.github.skm0078.audora.playback.browse

import android.content.Context
import io.github.skm0078.audora.core.config.BrowseConfig
import io.github.skm0078.audora.core.config.ExtractionConfig
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.document.PdfBook
import io.github.skm0078.audora.pdf.pdfium.PdfiumEngine
import io.github.skm0078.audora.playback.documents.SharedPdfCopies
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Opens the PDF just for its outline (no page is read), then closes it; PDFium's own lock keeps this safe beside the
 * book playing. The last [BrowseConfig.chapterCacheSize] books are remembered by id and fingerprint, so a car going
 * back and forth through the library opens each file once. A book that cannot be opened (moved, password) has no
 * chapters, only "Resume", and is tried again next time.
 */
class OutlineChapters(
    context: Context,
    private val config: BrowseConfig = BrowseConfig(),
    private val extraction: ExtractionConfig = ExtractionConfig(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val appContext = context.applicationContext
    private val cache = object : LinkedHashMap<String, List<BookChapter>>(config.chapterCacheSize, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<BookChapter>>): Boolean =
            size > config.chapterCacheSize
    }

    suspend fun of(book: Book): List<BookChapter> {
        val key = "${book.id.value}:${book.fingerprint}"
        val known = synchronized(cache) { cache[key] }
        return known ?: withContext(io) { read(book) }?.also { read -> synchronized(cache) { cache[key] = read } }
            .orEmpty()
    }

    /** Null when the book could not be read; logged with its context only. */
    private fun read(book: Book): List<BookChapter>? {
        val logContext = "book=${book.id.logLabel}"
        val engine = PdfiumEngine.forUri(
            appContext,
            SharedPdfCopies.readableUri(appContext, book.documentUri),
            extraction,
        )
        return when (val opened = PdfBook.open(engine, config = extraction, logContext = logContext)) {
            is Outcome.Failure -> {
                AudoraLog.w(TAG, "No chapters to browse, $logContext: ${opened.error}")
                null
            }

            is Outcome.Success -> opened.value.use { pdf ->
                when (val outline = pdf.outline()) {
                    is Outcome.Success ->
                        BookChapters.from(outline.value, pdf.pageCount, extraction.outlineChapterDepth)

                    is Outcome.Failure -> {
                        AudoraLog.w(TAG, "Outline unreadable, $logContext: ${outline.error}")
                        null
                    }
                }
            }
        }
    }

    private companion object {
        const val TAG = "Playback.Browse"
        const val LOAD_FACTOR = 0.75f
    }
}
