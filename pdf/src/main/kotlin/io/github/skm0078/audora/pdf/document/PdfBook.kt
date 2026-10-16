// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/document/PdfBook.kt
// Purpose: Page-at-a-time access to one open PDF with bounded memory (reopen every N pages, small page caches).

package io.github.skm0078.audora.pdf.document

import io.github.skm0078.audora.core.config.ExtractionConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.flatMap
import io.github.skm0078.audora.core.result.map
import io.github.skm0078.audora.pdf.cleanup.PageProfile
import io.github.skm0078.audora.pdf.extract.PageText
import io.github.skm0078.audora.pdf.extract.PageTextAssembler
import io.github.skm0078.audora.pdf.quality.PageOcr
import io.github.skm0078.audora.pdf.quality.PageQuality
import io.github.skm0078.audora.pdf.quality.PageRouting
import io.github.skm0078.audora.pdf.quality.RouteReason
import io.github.skm0078.audora.pdf.quality.TextLayerCheck
import io.github.skm0078.audora.pdf.quality.TextLayerVerdict
import io.github.skm0078.audora.pdf.units.AnalyzedPage
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder
import java.io.Closeable

/**
 * One book, read a page at a time around the playhead, so books of any length fit in memory. Header and footer
 * removal compares a page with its neighbours (ADR-020), so [readingUnits] reads up to
 * `CleanupConfig.neighbourPages` pages on each side and keeps two small LRU caches: a few analysed pages (so the
 * next page, already read as a neighbour, is not extracted again) and a few dozen page profiles of a few hundred
 * bytes each. Reading on from page to page costs about one extraction per page; a jump costs one per window page.
 *
 * Each page is routed (P3-03, ADR-021): text layer, legacy-font converter or OCR. For a Hindi or Bengali book the
 * first page with enough text is also OCR'd through [ocr] and compared with its text layer ([TextLayerCheck]).
 * Legacy-font text is converted wherever it is read (P3-04, ADR-023); OCR (P3-05) is not built yet, so a page
 * routed to OCR is still read from its text layer, and the route is returned with the units for P3-05 to act on.
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
    private val ocr: PageOcr?,
    private var handle: PdfDocumentHandle?,
) : Closeable {
    private val assembler = PageTextAssembler(config)
    private var caches: PageCaches? = null
    private var textLayerCheck: TextLayerCheck? = null

    /** What the book's OCR sample said about its Hindi/Bengali text layer. Diagnostics. */
    @get:Synchronized
    val textLayerVerdict: TextLayerVerdict
        get() = textLayerCheck?.verdict ?: TextLayerVerdict.UNCHECKED

    /** The character error rate of the book's last OCR sample; null before one ran. Diagnostics. */
    @get:Synchronized
    val textLayerSampleCer: Float?
        get() = textLayerCheck?.lastSampleCer

    /** Fixed for the book's lifetime: the source does not change between reopens. */
    val pageCount: Int = requireNotNull(handle).pageCount

    /** How many times the document has been opened, the first open included. Diagnostics and tests. */
    @get:Synchronized
    var opens: Int = 1
        private set

    /**
     * Pages extracted since the book was opened, neighbours read for cleanup included (ADR-020). Diagnostics and
     * tests: the reopen rule counts these, so [opens] follows from it.
     */
    @get:Synchronized
    var pagesRead: Int = 0
        private set

    private var readsSinceOpen = 0

    @Synchronized
    fun pageText(pageIndex: Int): Outcome<PageText, PdfPageError> {
        if (pageIndex !in 0 until pageCount) return Outcome.Failure(PdfPageError.PageOutOfRange(pageIndex, pageCount))
        return currentHandle().flatMap { open ->
            readsSinceOpen++
            pagesRead++
            open.readPage(pageIndex).map(assembler::assemble)
        }
    }

    /** The bookmarks. Does not count as a page read: PDFium's outline walk loads no pages. */
    @Synchronized
    fun outline(): Outcome<List<OutlineEntry>, PdfPageError> =
        handle?.let { Outcome.Success(it.outline()) } ?: Outcome.Failure(PdfPageError.BookClosed)

    /**
     * The page's units after layout and cleanup. A neighbour that fails to read is left out (logged): the page
     * is still read, at worst with a header the missing neighbour would have confirmed.
     */
    @Synchronized
    fun readingUnits(pageIndex: Int, builder: ReadingUnitBuilder): Outcome<List<ReadingUnit>, PdfPageError> =
        readPage(pageIndex, builder).map { it.units }

    /** [readingUnits] with the page's route (P3-03). */
    @Synchronized
    fun readPage(pageIndex: Int, builder: ReadingUnitBuilder): Outcome<PageReading, PdfPageError> {
        val cache = cachesFor(builder)
        return analyzedPage(pageIndex, cache).map { page ->
            val neighbours = window(pageIndex, builder.cleanupConfig.neighbourPages)
                .filter { it != pageIndex }
                .mapNotNull { profileOf(it, cache) }
            val check = textLayerCheck ?: TextLayerCheck(builder.qualityConfig, ocr, logContext)
            textLayerCheck = check
            val routing = check.route(pageIndex, page.quality, page.quality.ocrLangs) {
                builder.sampleText(page, neighbours)
            }
            PageReading(builder.build(page, neighbours), routing)
        }
    }

    @Synchronized
    override fun close() {
        handle?.close()
        handle = null
        caches = null
    }

    /** Profiles depend on the builder's configuration, so a different builder starts empty caches. */
    private fun cachesFor(builder: ReadingUnitBuilder): PageCaches =
        caches?.takeIf { it.owner === builder } ?: PageCaches(builder).also { caches = it }

    private fun analyzedPage(pageIndex: Int, cache: PageCaches): Outcome<AnalyzedPage, PdfPageError> {
        cache.analyzed[pageIndex]?.let { return Outcome.Success(it) }
        return pageText(pageIndex).map(cache.owner::analyze).also { result ->
            if (result is Outcome.Success) {
                cache.analyzed[pageIndex] = result.value
                cache.profiles[pageIndex] = result.value.profile
                // Pages off the text layer are logged once per extraction, with the measurements behind it.
                if (result.value.quality.reason != RouteReason.TEXT_LAYER_OK) {
                    AudoraLog.i(TAG, "Page routed ${describe(result.value.quality)}, $logContext page=$pageIndex")
                }
            }
        }
    }

    private fun profileOf(pageIndex: Int, cache: PageCaches): PageProfile? = cache.profiles[pageIndex]
        ?: when (val page = analyzedPage(pageIndex, cache)) {
            is Outcome.Success -> page.value.profile

            is Outcome.Failure -> {
                AudoraLog.w(TAG, "Neighbour page skipped for cleanup, $logContext page=$pageIndex error=${page.error}")
                null
            }
        }

    /** [radius] pages on each side of [pageIndex], shifted inward at the book's ends so the window stays full. */
    private fun window(pageIndex: Int, radius: Int): IntRange {
        val size = minOf(2 * radius + 1, pageCount)
        val start = (pageIndex - radius).coerceIn(0, pageCount - size)
        return start until start + size
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

    private class PageCaches(val owner: ReadingUnitBuilder) {
        val analyzed: MutableMap<Int, AnalyzedPage> = lru(owner.cleanupConfig.analyzedCachePages)
        val profiles: MutableMap<Int, PageProfile> = lru(owner.cleanupConfig.profileCachePages)
    }

    companion object {
        private const val TAG = "Audora.PdfBook"
        private const val LOAD_FACTOR = 0.75f

        /** Least recently used entries go first once [capacity] is exceeded. */
        private fun <K, V> lru(capacity: Int): MutableMap<K, V> =
            object : LinkedHashMap<K, V>(capacity + 1, LOAD_FACTOR, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > capacity
            }

        /**
         * Opens the book. A [PdfOpenError.PasswordRequired] failure means: ask for the password, call again.
         * [ocr] samples a Hindi/Bengali book's text layer (P3-05 supplies it); null leaves it unverified.
         */
        fun open(
            engine: PdfEngine,
            password: String? = null,
            config: ExtractionConfig = ExtractionConfig(),
            logContext: String = "",
            ocr: PageOcr? = null,
        ): Outcome<PdfBook, PdfOpenError> = when (val opened = engine.open(password)) {
            is Outcome.Success -> {
                val book = PdfBook(engine, password, config, logContext, ocr, opened.value)
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

/** Route, reason and the scorer's measurements, for the log (no text). */
private fun describe(quality: PageQuality): String = with(quality.signals) {
    "${quality.route} (${quality.reason}) letters=$letters indic=$indicLetters mapErrors=$unicodeMapErrorRate " +
        "invisible=$invisibleShare garbage=$garbageRate invalidIndic=$invalidIndicRate legacy=$legacyShare " +
        "legacyUnknown=$legacyUnknownRate"
}

/** A page's units and where its words come from. */
class PageReading(val units: List<ReadingUnit>, val routing: PageRouting)
