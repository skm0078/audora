// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/book/BookText.kt
// Purpose: A book's sentences, one page at a time: the seam between playback and the :pdf pipeline.

package io.github.skm0078.audora.playback.book

import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.document.PdfBook
import io.github.skm0078.audora.pdf.document.PdfPageError
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder
import java.io.Closeable

/** Blocking and thread-confined to the caller's extraction thread. Fakes implement it in tests. */
interface BookText : Closeable {
    val pageCount: Int

    fun units(page: Int): Outcome<List<ReadingUnit>, PdfPageError>
}

/** :pdf's page-at-a-time extraction (P1-02): PDFium text, `/ActualText` rebuild, sentence split. */
class PdfBookText(private val book: PdfBook, private val builder: ReadingUnitBuilder = ReadingUnitBuilder()) :
    BookText {
    override val pageCount: Int get() = book.pageCount

    override fun units(page: Int): Outcome<List<ReadingUnit>, PdfPageError> = book.readingUnits(page, builder)

    override fun close() = book.close()
}
