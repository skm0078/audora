// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/document/PdfBook.kt
// Purpose: Page-at-a-time access to one open PDF with bounded memory (reopen every N pages, small page caches).

package io.github.skm0078.audora.pdf.document

import io.github.skm0078.audora.core.config.CleanupConfig
import io.github.skm0078.audora.core.config.ExtractionConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.flatMap
import io.github.skm0078.audora.core.result.map
import io.github.skm0078.audora.pdf.cleanup.PageProfile
import io.github.skm0078.audora.pdf.extract.PageText
import io.github.skm0078.audora.pdf.extract.PageTextAssembler
import io.github.skm0078.audora.pdf.ocr.OcrLanguages
import io.github.skm0078.audora.pdf.ocr.OcrMargins
import io.github.skm0078.audora.pdf.quality.OcrError
import io.github.skm0078.audora.pdf.quality.PageOcr
import io.github.skm0078.audora.pdf.quality.PageOcrFactory
import io.github.skm0078.audora.pdf.quality.PageQuality
import io.github.skm0078.audora.pdf.quality.PageRenderer
import io.github.skm0078.audora.pdf.quality.PageRoute
import io.github.skm0078.audora.pdf.quality.PageRouting
import io.github.skm0078.audora.pdf.quality.RouteReason
import io.github.skm0078.audora.pdf.quality.TextLayerCheck
import io.github.skm0078.audora.pdf.quality.TextLayerVerdict
import io.github.skm0078.audora.pdf.render.ViewRect
import io.github.skm0078.audora.pdf.units.AnalyzedPage
import io.github.skm0078.audora.pdf.units.PageCarry
import io.github.skm0078.audora.pdf.units.PageTail
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
 * first page with enough text is also OCR'd and compared with its text layer ([TextLayerCheck]). Legacy-font text
 * is converted wherever it is read (P3-04, ADR-023). Pages routed to OCR are read by the book's [PageOcr] (P3-05,
 * ADR-025) in the languages [OcrLanguages] picks; when OCR is missing (no models yet) or fails, the page falls back
 * to its text layer and [PageReading.source] says why.
 *
 * PDFium's document cache grows with every page visited, so after [ExtractionConfig.reopenEveryPages] reads
 * the document is closed and opened again with the same password. Thread-safe: calls are serialized, because
 * a PDFium document must not be used from two threads at once.
 *
 * @param logContext identifies the book in logs (e.g. "book=3f2a"); never the title or the PDF's text.
 */
@Suppress("TooManyFunctions") // Reading, rendering, the neighbour caches and the reopen rule share one lock.
class PdfBook private constructor(
    private val engine: PdfEngine,
    private val password: String?,
    private val config: ExtractionConfig,
    private val logContext: String,
    ocrFactory: PageOcrFactory?,
    private var handle: PdfDocumentHandle?,
) : Closeable {
    private val assembler = PageTextAssembler(config)
    private var caches: PageCaches? = null
    private var textLayerCheck: TextLayerCheck? = null
    private var ocrLanguages: OcrLanguages? = null
    private val ocr: PageOcr? = ocrFactory?.create(PageRenderer(::render))

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

    /** [readingUnits] with the page's route (P3-03) and where its words came from (P3-05). */
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
            val languages = ocrLanguages ?: OcrLanguages(builder.qualityConfig).also { ocrLanguages = it }
            val read = if (routing.route == PageRoute.OCR) {
                readByOcr(page, routing, languages, builder) { builder.build(page, neighbours) }
            } else {
                languages.learn(page.quality.ocrLangs)
                PageReading(builder.build(page, neighbours), routing, PageSource.TextLayer)
            }
            joined(pageIndex, read, cache)
        }
    }

    /**
     * [read] joined to the page before it when that page was read since the book opened ([PageCarry]): a table or
     * sentence carried over. What this page hands on is kept for the next one.
     */
    private fun joined(pageIndex: Int, read: PageReading, cache: PageCaches): PageReading {
        val joined = PageCarry.join(read.units, cache.tails[pageIndex - 1])
        cache.tails[pageIndex] = PageCarry.tailOf(joined.units)
        return PageReading(joined.units, read.routing, read.source, read.ocrConfidence, joined.continuesPreviousPage)
    }

    /**
     * The page's own picture, drawn for OCR. Counts towards the periodic reopen like a page read: drawing fills
     * PDFium's caches too.
     */
    @Synchronized
    fun render(pageIndex: Int, dpi: Int, maxPixels: Long): Outcome<PageImage, PdfPageError> {
        if (pageIndex !in 0 until pageCount) return Outcome.Failure(PdfPageError.PageOutOfRange(pageIndex, pageCount))
        return currentHandle().flatMap { open ->
            readsSinceOpen++
            open.render(pageIndex, dpi, maxPixels)
        }
    }

    /** OCR when the book has it and the languages are known; the text layer, with the reason, when not. */
    private fun readByOcr(
        page: AnalyzedPage,
        routing: PageRouting,
        languages: OcrLanguages,
        builder: ReadingUnitBuilder,
        textLayer: () -> List<ReadingUnit>,
    ): PageReading {
        val pageIndex = page.pageIndex
        val ocr = ocr ?: return PageReading(textLayer(), routing, PageSource.OcrUnavailable(OcrError.NoModels))
        val recognized = languages.forPage(pageIndex, routing.reason, page.quality.ocrLangs, ocr).flatMap { langs ->
            ocr.recognize(pageIndex, langs).map { text -> langs to text }
        }
        return when (recognized) {
            is Outcome.Success -> {
                val (langs, text) = recognized.value
                languages.learnFrom(text.text)
                val dropped = builder.ocrClutter.droppedLines(text) {
                    ocrMargins(pageIndex, langs, text.quarterTurns, ocr, builder.cleanupConfig)
                }
                val transform = handle?.pageTransform(pageIndex)
                val toPage = transform?.let { t -> { rect: ViewRect -> t.toPage(rect) } }
                val reading = builder.ocrReadingText(pageIndex, text, dropped, toPage)
                PageReading(builder.build(reading), routing, PageSource.Ocr(langs), text.meanConfidence)
            }

            is Outcome.Failure -> {
                AudoraLog.w(
                    TAG,
                    "OCR unavailable, reading the text layer, $logContext page=$pageIndex: ${recognized.error}",
                )
                PageReading(textLayer(), routing, PageSource.OcrUnavailable(recognized.error))
            }
        }
    }

    /**
     * The top and bottom strips of the scanned pages around [pageIndex], by page offset, for running-header removal
     * (ADR-032). A neighbour that cannot be read is left out (logged): the page keeps a header it would have confirmed.
     */
    private fun ocrMargins(
        pageIndex: Int,
        langs: Set<Lang>,
        quarterTurns: Int,
        ocr: PageOcr,
        config: CleanupConfig,
    ): Map<Int, OcrMargins> = window(pageIndex, config.ocrNeighbourPages)
        .filter { it != pageIndex }
        .mapNotNull { other ->
            when (val strips = ocr.margins(other, langs, quarterTurns, config.ocrMarginShare)) {
                is Outcome.Success -> other - pageIndex to strips.value

                is Outcome.Failure -> {
                    AudoraLog.w(TAG, "Margin strips skipped, $logContext page=$other error=${strips.error}")
                    null
                }
            }
        }
        .toMap()

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

        /** What each page read hands on to the next ([PageCarry]); a few bytes each. */
        val tails: MutableMap<Int, PageTail> = lru(owner.cleanupConfig.profileCachePages)
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
         * [ocr] builds the book's OCR from its page renderer (P3-05, `RenderedPageOcr.factory`): it reads pages
         * routed to OCR and samples a Hindi/Bengali book's text layer. Null reads every page from its text layer and
         * leaves Indic text layers unverified.
         */
        fun open(
            engine: PdfEngine,
            password: String? = null,
            config: ExtractionConfig = ExtractionConfig(),
            logContext: String = "",
            ocr: PageOcrFactory? = null,
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

/**
 * A page's units, its route and where its words actually came from. [ocrConfidence] is `OcrText.meanConfidence` for a
 * page read by OCR, else null: the reader's quality badge (RI-9). [continuesPreviousPage]: the page opens inside
 * the sentence the page before broke off (plan item 7: a quote opened there is still open); false when it does not,
 * or when the page before was not read since the book opened.
 */
class PageReading(
    val units: List<ReadingUnit>,
    val routing: PageRouting,
    val source: PageSource,
    val ocrConfidence: Int? = null,
    val continuesPreviousPage: Boolean = false,
)

/** Where a page's words came from: what the reader's "OCR" badge shows (P3-05). */
sealed interface PageSource {
    /** The PDF's own text (possibly repaired, P3-03). */
    data object TextLayer : PageSource

    /** Tesseract read the rendered page in [langs]. */
    data class Ocr(val langs: Set<Lang>) : PageSource

    /** The page needs OCR but got its text layer instead (often nothing, for a scan) because of [error]. */
    data class OcrUnavailable(val error: OcrError) : PageSource
}
