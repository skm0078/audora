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
import io.github.skm0078.audora.core.result.getOrNull
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
import io.github.skm0078.audora.pdf.units.opensWithProse
import io.github.skm0078.audora.pdf.units.opensWithTable
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
     * Each page's printed label ("iv", "A-3"), so a copy or a "Go to page" can use the numbers printed on the
     * pages; null for a page without one (most PDFs have no labels at all). Trimmed, control characters dropped.
     * Does not count as a page read: labels live in the document catalog.
     */
    @Synchronized
    fun pageLabels(): Outcome<List<String?>, PdfPageError> = handle?.let { open ->
        Outcome.Success(List(pageCount) { page -> open.pageLabel(page)?.let(::cleanLabel) })
    } ?: Outcome.Failure(PdfPageError.BookClosed)

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
            val read = checkNotNull(read(page, builder, cache, quiet = false)) { "only a quiet read leaves a page out" }
            joined(pageIndex, read, builder, cache)
        }
    }

    /**
     * The page's units unjoined to the page before. A [quiet] read (to see what the page hands on, [tailBefore])
     * neither samples the book's text layer nor runs OCR: an OCR page is read from the OCR cache or not at all
     * (null), and nothing the book learns from a page changes.
     */
    private fun read(page: AnalyzedPage, builder: ReadingUnitBuilder, cache: PageCaches, quiet: Boolean): PageReading? {
        val pageIndex = page.pageIndex
        val neighbours = window(pageIndex, builder.cleanupConfig.neighbourPages)
            .filter { it != pageIndex }
            .mapNotNull { profileOf(it, cache) }
        val check = textLayerCheck ?: TextLayerCheck(builder.qualityConfig, ocr, logContext)
        textLayerCheck = check
        val routing = if (quiet) {
            check.currentRoute(page.quality)
        } else {
            check.route(pageIndex, page.quality, page.quality.ocrLangs) { builder.sampleText(page, neighbours) }
        }
        val languages = languagesFor(builder)
        val bookOcr = ocr
        val build = { builder.build(page, neighbours) }
        return when {
            routing.route != PageRoute.OCR -> {
                if (!quiet) languages.learn(page.quality.ocrLangs)
                PageReading(build(), routing, PageSource.TextLayer)
            }

            bookOcr == null -> PageReading(build(), routing, PageSource.OcrUnavailable(OcrError.NoModels))

            else -> readByOcr(bookOcr, page, routing, builder, textLayer = build.takeUnless { quiet })
        }
    }

    private fun languagesFor(builder: ReadingUnitBuilder): OcrLanguages =
        ocrLanguages ?: OcrLanguages(builder.qualityConfig).also { ocrLanguages = it }

    /**
     * [read] joined to the page before it ([PageCarry]): a table or sentence carried over. The page before is looked
     * at only when this page can carry something on (it opens with a table, or with body text, a list item or an
     * aside). What this page hands on is kept for the next one when it was built on a complete chain ([HandOn]).
     */
    private fun joined(pageIndex: Int, read: PageReading, builder: ReadingUnitBuilder, cache: PageCaches): PageReading {
        val carry = cache.carry
        val needsTable = opensWithTable(read.units)
        val before = if (needsTable || opensWithProse(read.units)) {
            tailBefore(pageIndex, builder, cache, depth = 0, needsTable)
        } else {
            HandOn.NONE
        }
        val joined = carry.join(read.units, before.tail)
        if (!needsTable || before.complete) keep(pageIndex, carry.tailOf(joined.units), cache)
        if (joined.table != null || joined.continuesPreviousPage) {
            AudoraLog.d(
                TAG,
                "Page joined to the one before, $logContext page=$pageIndex table=${joined.table} " +
                    "sentence=${joined.continuesPreviousPage}",
            )
        }
        return PageReading(joined.units, read.routing, read.source, joined.continuesPreviousPage)
    }

    /**
     * What the page before [pageIndex] hands on: kept from reading it (under the text-layer verdict still in force),
     * else worked out by reading it quietly and, when this page needs its table ([needsTable]) and it opens with one,
     * joining it to its own page before, at most `PageCarryConfig.maxCarryPages` back ([depth] pages so far). So a
     * page reads the same whichever page was read before it. Best effort only past that many pages (a table's rows
     * numbered from there, its header unknown) and after a scanned page not yet OCR'd (nothing handed on); neither
     * is kept, so the page reads right once the chain is complete.
     */
    private fun tailBefore(
        pageIndex: Int,
        builder: ReadingUnitBuilder,
        cache: PageCaches,
        depth: Int,
        needsTable: Boolean,
    ): HandOn {
        val before = pageIndex - 1
        val known = cache.tails[before]?.takeIf { it.verdict == textLayerVerdict }
        return when {
            before < 0 -> HandOn.NONE
            known != null -> HandOn(known.tail, complete = true)
            depth >= builder.cleanupConfig.carry.maxCarryPages -> HandOn.CUT
            else -> quietTail(before, builder, cache, depth, needsTable)
        }
    }

    /** What [pageIndex] hands on, read quietly ([tailBefore]); kept only when complete. */
    private fun quietTail(
        pageIndex: Int,
        builder: ReadingUnitBuilder,
        cache: PageCaches,
        depth: Int,
        needsTable: Boolean,
    ): HandOn {
        val page = analyzedPage(pageIndex, cache)
        val read = page.getOrNull()?.let { read(it, builder, cache, quiet = true) }
        if (read == null) {
            val reason = (page as? Outcome.Failure)?.error ?: "a scanned page not in the OCR cache"
            AudoraLog.d(TAG, "Page before not read for what it hands on, $logContext page=$pageIndex: $reason")
            return HandOn.CUT
        }
        val carry = cache.carry
        val opens = opensWithTable(read.units)
        val previous = if (needsTable && opens) {
            tailBefore(pageIndex, builder, cache, depth + 1, needsTable = true)
        } else {
            HandOn.NONE
        }
        val tail = carry.tailOf(carry.join(read.units, previous.tail).units)
        // Only a text-layer page reads quietly as it reads aloud: an OCR page's quiet reading is whatever is cached.
        val complete = read.source == PageSource.TextLayer && (!opens || (needsTable && previous.complete))
        if (complete) keep(pageIndex, tail, cache)
        return HandOn(tail, complete)
    }

    private fun keep(pageIndex: Int, tail: PageTail, cache: PageCaches) {
        cache.tails[pageIndex] = KnownTail(tail, textLayerVerdict)
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

    /**
     * [bookOcr]'s reading of the page when the languages are known; [textLayer], with the reason, when not. A quiet
     * read ([textLayer] null) reads only what OCR has cached, leaves the page out (null) otherwise and learns nothing.
     */
    private fun readByOcr(
        bookOcr: PageOcr,
        page: AnalyzedPage,
        routing: PageRouting,
        builder: ReadingUnitBuilder,
        textLayer: (() -> List<ReadingUnit>)?,
    ): PageReading? {
        val pageIndex = page.pageIndex
        val ocr = (if (textLayer == null) bookOcr.cachedOnly() else bookOcr) ?: return null
        val languages = languagesFor(builder)
        val recognized = languages.forPage(
            pageIndex,
            routing.reason,
            page.quality.ocrLangs,
            ocr,
            remember = textLayer != null,
        ).flatMap { langs ->
            ocr.recognize(pageIndex, langs).map { text -> langs to text }
        }
        return when (recognized) {
            is Outcome.Success -> {
                val (langs, text) = recognized.value
                if (textLayer != null) languages.learnFrom(text.text)
                val dropped = builder.ocrClutter.droppedLines(text) {
                    ocrMargins(pageIndex, langs, text.quarterTurns, ocr, builder.cleanupConfig)
                }
                val transform = handle?.pageTransform(pageIndex)
                val toPage = transform?.let { t -> { rect: ViewRect -> t.toPage(rect) } }
                val reading = builder.ocrReadingText(pageIndex, text, dropped, toPage)
                PageReading(builder.build(reading), routing, PageSource.Ocr(langs))
            }

            is Outcome.Failure -> textLayer?.let { layer ->
                AudoraLog.w(
                    TAG,
                    "OCR unavailable, reading the text layer, $logContext page=$pageIndex: ${recognized.error}",
                )
                PageReading(layer(), routing, PageSource.OcrUnavailable(recognized.error))
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
        val tails: MutableMap<Int, KnownTail> = lru(owner.cleanupConfig.profileCachePages)
        val carry = PageCarry(owner.cleanupConfig.carry)
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

/**
 * A printed page label as shown: control and format characters (zero-width spaces, byte-order and bidi marks) dropped,
 * trimmed; null when nothing is left.
 */
private fun cleanLabel(label: String): String? =
    label.filterNot { it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() }.trim().ifEmpty { null }

/**
 * What the page before hands on ([tail]) and whether it may be kept: [complete] when built from pages read as they
 * read aloud, back to a page that needs nothing before it.
 */
private class HandOn(val tail: PageTail?, val complete: Boolean) {
    companion object {
        /** Nothing to hand on, for certain: the first page, or a page that takes nothing from the one before. */
        val NONE = HandOn(null, complete = true)

        /** Nothing known: the chain was cut (too long, or a page that could not be read quietly). */
        val CUT = HandOn(null, complete = false)
    }
}

/** A page's hand-on, kept with the text-layer verdict it was built under: a new verdict may change the page. */
private class KnownTail(val tail: PageTail, val verdict: TextLayerVerdict)

/** Route, reason and the scorer's measurements, for the log (no text). */
private fun describe(quality: PageQuality): String = with(quality.signals) {
    "${quality.route} (${quality.reason}) letters=$letters indic=$indicLetters mapErrors=$unicodeMapErrorRate " +
        "invisible=$invisibleShare garbage=$garbageRate invalidIndic=$invalidIndicRate legacy=$legacyShare " +
        "legacyUnknown=$legacyUnknownRate"
}

/**
 * A page's units, its route and where its words actually came from. [continuesPreviousPage]: the page opens inside
 * the sentence the page before broke off (plan item 7: a quote opened there is still open); false when it does not,
 * or when the page before cannot be read without new OCR (a scanned page not yet recognised).
 */
class PageReading(
    val units: List<ReadingUnit>,
    val routing: PageRouting,
    val source: PageSource,
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
