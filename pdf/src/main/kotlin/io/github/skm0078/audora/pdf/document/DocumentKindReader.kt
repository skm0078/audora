// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/document/DocumentKindReader.kt
// Purpose: Reads a sample of a book's pages and classifies the book (RI-10).

package io.github.skm0078.audora.pdf.document

import io.github.skm0078.audora.core.config.DocumentKindConfig
import io.github.skm0078.audora.core.config.SpeakabilityConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder

private const val TAG = "DocumentKind"

/**
 * What kind of book this is (RI-10), from `DocumentKindConfig.samplePages` pages spread over it, each read the way
 * playback reads it (OCR included, where the page needs it). That costs up to a dozen page reads, so call it once per
 * book, off the main thread, and keep the answer. A page that fails to read is left out (logged).
 *
 * @param readableFrom units scoring below this (`SpeakabilityConfig.skipBelow`) are junk and are not counted.
 */
fun PdfBook.documentKind(
    builder: ReadingUnitBuilder,
    config: DocumentKindConfig = DocumentKindConfig(),
    readableFrom: Float = SpeakabilityConfig().skipBelow,
    logContext: String = "",
): DocumentKind {
    val pages = samplePages(pageCount, config.samplePages).mapNotNull { page ->
        when (val reading = readPage(page, builder)) {
            is Outcome.Failure -> {
                AudoraLog.w(TAG, "sample page unreadable, $logContext page=$page error=${reading.error}")
                null
            }

            is Outcome.Success -> {
                val rawText = if (reading.value.source == PageSource.TextLayer) {
                    (pageText(page) as? Outcome.Success)?.value?.text
                } else {
                    null
                }
                PageSignals.of(reading.value.units, rawText, readableFrom)
            }
        }
    }
    val kind = DocumentKindClassifier(config).classify(pages, pageCount)
    AudoraLog.i(TAG, "classified $logContext pages=$pageCount sampled=${pages.size} kind=$kind")
    return kind
}

/** [count] page indexes spread evenly over a book of [pageCount] pages, first and last included. */
internal fun samplePages(pageCount: Int, count: Int): List<Int> = when {
    pageCount <= 0 -> emptyList()
    pageCount <= count -> (0 until pageCount).toList()
    count == 1 -> listOf(0)
    else -> (0 until count).map { i -> Math.round(i * (pageCount - 1) / (count - 1).toDouble()).toInt() }.distinct()
}
