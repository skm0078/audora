// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/document/PdfBook.kt
// Purpose: Page-at-a-time access to one open PDF with bounded native memory (reopen every N pages, ADR-011 §5).

package io.github.skm0078.audora.pdf.document

import io.github.skm0078.audora.core.config.ExtractionConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.flatMap
import io.github.skm0078.audora.core.result.map
import io.github.skm0078.audora.pdf.extract.PageText
import io.github.skm0078.audora.pdf.extract.PageTextAssembler
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder
import java.io.Closeable

/**
 * One book, read a page at a time around the playhead. Nothing is cached here: callers keep only the pages
 * they need (the render queue holds about two pages ahead), so books of any length fit in memory.
 *
 * PDFium's document cache grows with every page visited, so after [ExtractionConfig.reopenEveryPages] reads
 * the document is closed and opened again with the same password. Thread-safe: calls are serialized, because
 * a PDFium document must not be used from two threads at once.
 *
 * @param logContext identifies the book in logs (e.g. "book=3f2a"); never the title or the PDF's text.
 */
class PdfBook private constructor(
    private val engine: PdfEngine,
    private val password: String?,
    private val config: ExtractionConfig,
    private val logContext: String,
    private var handle: PdfDocumentHandle?,
) : Closeable {
    private val assembler = PageTextAssembler(config)

    /** Fixed for the book's lifetime: the source does not change between reopens. */
    val pageCount: Int = requireNotNull(handle).pageCount

    /** How many times the document has been opened, the first open included. Diagnostics and tests. */
    @get:Synchronized
    var opens: Int = 1
        private set

    private var readsSinceOpen = 0

    @Synchronized
    fun pageText(pageIndex: Int): Outcome<PageText, PdfPageError> {
        if (pageIndex !in 0 until pageCount) return Outcome.Failure(PdfPageError.PageOutOfRange(pageIndex, pageCount))
        return currentHandle().flatMap { open ->
            readsSinceOpen++
            open.readPage(pageIndex).map(assembler::assemble)
        }
    }

    /** The bookmarks. Does not count as a page read: PDFium's outline walk loads no pages. */
    @Synchronized
    fun outline(): Outcome<List<OutlineEntry>, PdfPageError> =
        handle?.let { Outcome.Success(it.outline()) } ?: Outcome.Failure(PdfPageError.BookClosed)

    fun readingUnits(pageIndex: Int, builder: ReadingUnitBuilder): Outcome<List<ReadingUnit>, PdfPageError> =
        pageText(pageIndex).map(builder::build)

    @Synchronized
    override fun close() {
        handle?.close()
        handle = null
    }

    private fun currentHandle(): Outcome<PdfDocumentHandle, PdfPageError> {
        val open = handle ?: return Outcome.Failure(PdfPageError.BookClosed)
        return if (readsSinceOpen < config.reopenEveryPages) Outcome.Success(open) else reopen(open)
    }

    private fun reopen(old: PdfDocumentHandle): Outcome<PdfDocumentHandle, PdfPageError> {
        old.close()
        handle = null
        return when (val reopened = engine.open(password)) {
            is Outcome.Success -> {
                handle = reopened.value
                readsSinceOpen = 0
                opens++
                AudoraLog.d(TAG, "Reopened document to release PDFium's page cache, $logContext opens=$opens")
                Outcome.Success(reopened.value)
            }

            is Outcome.Failure -> {
                AudoraLog.e(TAG, "Reopen failed, $logContext error=${reopened.error}")
                Outcome.Failure(PdfPageError.ReopenFailed(reopened.error))
            }
        }
    }

    companion object {
        private const val TAG = "Audora.PdfBook"

        /** Opens the book. A [PdfOpenError.PasswordRequired] failure means: ask for the password, call again. */
        fun open(
            engine: PdfEngine,
            password: String? = null,
            config: ExtractionConfig = ExtractionConfig(),
            logContext: String = "",
        ): Outcome<PdfBook, PdfOpenError> = when (val opened = engine.open(password)) {
            is Outcome.Success -> {
                val book = PdfBook(engine, password, config, logContext, opened.value)
                AudoraLog.i(TAG, "Opened $logContext pages=${book.pageCount} encrypted=${password != null}")
                Outcome.Success(book)
            }

            is Outcome.Failure -> {
                AudoraLog.w(TAG, "Open failed, $logContext error=${opened.error}")
                Outcome.Failure(opened.error)
            }
        }
    }
}
