// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/book/BookText.kt
// Purpose: A book's sentences, one page at a time: the seam between playback and the :pdf pipeline.

package io.github.skm0078.audora.playback.book

import io.github.skm0078.audora.core.config.ExtractionConfig
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.document.PdfBook
import io.github.skm0078.audora.pdf.document.PdfPageError
import io.github.skm0078.audora.pdf.outline.ChapterStarts
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder
import java.io.Closeable

/** Blocking and thread-confined to the caller's extraction thread. Fakes implement it in tests. */
interface BookText : Closeable {
    val pageCount: Int

    fun units(page: Int): Outcome<List<ReadingUnit>, PdfPageError>

    /**
     * First pages of the book's chapters, ascending; empty when it has none (the end-of-chapter sleep timer
     * is then not offered). Today from the PDF outline; detected headings plug in here once the layout
     * pipeline finds them (P2-02).
     */
    fun chapterStarts(): List<Int> = emptyList()
}

/** :pdf's page-at-a-time extraction (P1-02): PDFium text, `/ActualText` rebuild, sentence split. */
class PdfBookText(
    private val book: PdfBook,
    private val builder: ReadingUnitBuilder = ReadingUnitBuilder(),
    private val config: ExtractionConfig = ExtractionConfig(),
) : BookText {
    override val pageCount: Int get() = book.pageCount

    override fun units(page: Int): Outcome<List<ReadingUnit>, PdfPageError> = book.readingUnits(page, builder)

    override fun chapterStarts(): List<Int> = when (val outline = book.outline()) {
        is Outcome.Success -> ChapterStarts.from(outline.value, book.pageCount, config.outlineChapterDepth)
        is Outcome.Failure -> emptyList()
    }

    override fun close() = book.close()
}
