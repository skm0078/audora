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
import java.util.concurrent.locks.ReentrantLock

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
 * Never blocks a caller for long on someone else's page: a scanned page can take a minute of OCR under the book's
 * lock. [close] returns at once (the page being read finishes, then the document closes, and every later call fails
 * with [PdfPageError.BookClosed]); OCR stops between its steps once the book is closing. A caller whose thread is
 * interrupted while it waits for the lock or between OCR steps gets an [InterruptedException]: the reader's page
 * loads run through `runInterruptible`, so a page the listener has turned away from stops being read.
 *
 * @param logContext identifies the book in logs (e.g. "book=3f2a"); never the title or the PDF's text.
 */
@Suppress("TooManyFunctions") // Reading, rendering, the neighbour caches, the reopen rule and closing share one lock.
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
    private var pageLabels: List<String?>? = null
    private var ocrLanguages: OcrLanguages? = null

    /** Every call but [close] holds it; interruptible, so a reader that gave up on a page stops waiting. */
    private val lock = ReentrantLock()

    /** Set by [close] (or a failed reopen) from any thread: the document closes as soon as nobody is reading it. */
    @Volatile
    private var closing = false

    private val ocr: PageOcr? = ocrFactory?.create(
        object : PageRenderer {
            override fun render(pageIndex: Int, dpi: Int, maxPixels: Long) =
                this@PdfBook.render(pageIndex, dpi, maxPixels)

            override fun abandoned(): Boolean = closing
        },
    )

    /** What the book's OCR sample said about its Hindi/Bengali text layer. Diagnostics. */
    val textLayerVerdict: TextLayerVerdict
        get() = locked { verdictNow() }

    /** The character error rate of the book's last OCR sample; null before one ran. Diagnostics. */
    val textLayerSampleCer: Float?
        get() = locked { textLayerCheck?.lastSampleCer }

    /** Fixed for the book's lifetime: the source does not change between reopens. */
    val pageCount: Int = requireNotNull(handle).pageCount

    /** How many times the document has been opened, the first open included. Diagnostics and tests. */
    var opens: Int = 1
        get() = locked { field }
        private set

    /**
     * Pages extracted since the book was opened, neighbours read for cleanup included (ADR-020). Diagnostics and
     * tests: the reopen rule counts these, so [opens] follows from it.
     */
    var pagesRead: Int = 0
        get() = locked { field }
        private set

    private var readsSinceOpen = 0

    fun pageText(pageIndex: Int): Outcome<PageText, PdfPageError> = locked {
        if (pageIndex !in 0 until pageCount) return@locked outOfRange(pageIndex)
        contained(pageIndex) {
            currentHandle().flatMap { open ->
                readsSinceOpen++
                pagesRead++
                open.readPage(pageIndex).map(assembler::assemble)
            }
        }
    }

    /** The bookmarks. Does not count as a page read: PDFium's outline walk loads no pages. */
    fun outline(): Outcome<List<OutlineEntry>, PdfPageError> = locked { openHandle().map { it.outline() } }

    /**
     * Each page's printed label ("iv", "A-3"), so a copy or a "Go to page" can use the numbers printed on the
     * pages; null for a page without one (most PDFs have no labels at all). Trimmed, control characters dropped.
     * Does not count as a page read: labels live in the document catalog. Read once per book (an older PDFium walks
     * the label tree for every page).
     */
    fun pageLabels(): Outcome<List<String?>, PdfPageError> = locked {
        openHandle().map { open ->
            val labels = pageLabels ?: List(pageCount) { page -> open.pageLabel(page)?.let(::cleanLabel) }
            pageLabels = labels
            labels
        }
    }

    /**
     * The page's units after layout and cleanup. A neighbour that fails to read is left out (logged): the page
     * is still read, at worst with a header the missing neighbour would have confirmed.
     */
    fun readingUnits(pageIndex: Int, builder: ReadingUnitBuilder): Outcome<List<ReadingUnit>, PdfPageError> =
        readPage(pageIndex, builder).map { it.units }

    /** [readingUnits] with the page's route (P3-03) and where its words came from (P3-05). */
    fun readPage(pageIndex: Int, builder: ReadingUnitBuilder): Outcome<PageReading, PdfPageError> = locked {
        contained(pageIndex) {
            val cache = cachesFor(builder)
            analyzedPage(pageIndex, cache).map { page ->
                val read = checkNotNull(read(page, builder, cache, quiet = false)) {
                    "only a quiet read leaves a page out"
                }
                joined(pageIndex, read, builder, cache)
            }
        }
    }

    /**
     * [readPage] without running OCR: a scanned page OCR has not read yet is null, one it has read comes from its
     * cache (unjoined to the page before, its running headers removed only as far as the neighbours' cached margins
     * allow). A text-layer page is read by the book's verdict so far, joined to the page before like [readPage], but
     * never sampled against OCR and never teaching the book its languages. For whole-book work beside listening
     * (search), which must never queue a book's worth of OCR in front of the page being read.
     */
    fun readPageIfReady(pageIndex: Int, builder: ReadingUnitBuilder): Outcome<PageReading?, PdfPageError> = locked {
        contained(pageIndex) {
            val cache = cachesFor(builder)
            analyzedPage(pageIndex, cache).flatMap { page ->
                val quiet = read(page, builder, cache, quiet = true)
                when (quiet?.source) {
                    null -> Outcome.Success(null)
                    PageSource.TextLayer -> Outcome.Success(joined(pageIndex, quiet, builder, cache))
                    else -> Outcome.Success(quiet)
                }
            }
        }
    }

    /**
     * Runs one page's pipeline with its defects contained: an invariant that a strange page breaks (a unit range, a
     * table cell, a score) or a layout walk that recurses too deep becomes [PdfPageError.ExtractionFailed], which
     * listening skips and logs, instead of an exception that ends playback. Nothing here suspends, so no coroutine
     * cancellation can be caught by mistake.
     */
    @Suppress("TooGenericExceptionCaught") // Any defect in the page pipeline, by design (see above).
    private inline fun <T> contained(pageIndex: Int, block: () -> Outcome<T, PdfPageError>): Outcome<T, PdfPageError> =
        try {
            block()
        } catch (e: RuntimeException) {
            pageFailed(pageIndex, e)
        } catch (e: StackOverflowError) {
            pageFailed(pageIndex, e)
        }

    private fun pageFailed(pageIndex: Int, cause: Throwable): Outcome.Failure<PdfPageError> {
        AudoraLog.e(TAG, "Page pipeline failed, skipping the page, $logContext page=$pageIndex", cause)
        return Outcome.Failure(PdfPageError.ExtractionFailed(pageIndex, cause.toString(), cause))
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
     * aside). What this page hands on is kept for the next one, marked provisional when its chain was not complete
     * ([Chain]).
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
        keep(pageIndex, carry.tailOf(joined.units), if (needsTable) before.chain else Chain.COMPLETE, cache)
        if (joined.table != null || joined.continuesPreviousPage) {
            AudoraLog.d(
                TAG,
                "Page joined to the one before, $logContext page=$pageIndex table=${joined.table} " +
                    "sentence=${joined.continuesPreviousPage}",
            )
        }
        return PageReading(joined.units, read.routing, read.source, read.ocrConfidence, joined.continuesPreviousPage)
    }

    /**
     * What the page before [pageIndex] hands on: kept from reading it (under the text-layer verdict still in force),
     * else worked out by reading it quietly and, when this page needs its table ([needsTable]) and it opens with one,
     * joining it to its own page before, at most `PageCarryConfig.maxCarryPages` back ([depth] pages so far). So a
     * page reads the same whichever page was read before it. Best effort only past that many pages (a table's rows
     * numbered from there, its header unknown) and through a scanned page read from the OCR cache: those hand-ons
     * are kept as provisional, so the pages after a jump take them up without walking back again and their rows
     * number on; one is worked out again once a complete hand-on has been kept since ([PageCaches.completeKept]).
     * After a scanned page not yet OCR'd nothing is handed on and nothing is kept, so the page reads right once OCR
     * has read it.
     */
    private fun tailBefore(
        pageIndex: Int,
        builder: ReadingUnitBuilder,
        cache: PageCaches,
        depth: Int,
        needsTable: Boolean,
    ): HandOn {
        val before = pageIndex - 1
        val known = cache.tails[before]?.takeIf { it.verdict == verdictNow() }
        return when {
            before < 0 -> HandOn.NONE
            known != null && !known.provisional -> HandOn(known.tail, Chain.COMPLETE)
            known != null && known.completeKept == cache.completeKept -> HandOn(known.tail, Chain.PROVISIONAL)
            depth >= builder.cleanupConfig.carry.maxCarryPages -> HandOn.CUT
            else -> quietTail(before, builder, cache, depth, needsTable)
        }
    }

    /** What [pageIndex] hands on, read quietly ([tailBefore]); kept unless its chain is [Chain.MISSING]. */
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
            return HandOn.MISSING
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
        val own = if (read.source == PageSource.TextLayer) Chain.COMPLETE else Chain.PROVISIONAL
        val chain = when {
            !opens -> own
            needsTable -> maxOf(own, previous.chain)
            else -> Chain.MISSING // Its opening table was not looked for in the page before.
        }
        keep(pageIndex, tail, chain, cache)
        return HandOn(tail, chain)
    }

    /** Keeps [tail] unless its [chain] is missing a page; a provisional one never replaces a complete one. */
    private fun keep(pageIndex: Int, tail: PageTail, chain: Chain, cache: PageCaches) {
        val verdict = verdictNow()
        val known = cache.tails[pageIndex]?.takeIf { it.verdict == verdict && !it.provisional }
        when {
            chain == Chain.MISSING || (chain == Chain.PROVISIONAL && known != null) -> Unit

            chain == Chain.COMPLETE -> {
                cache.completeKept++
                cache.tails[pageIndex] = KnownTail(tail, verdict, provisional = false, cache.completeKept)
            }

            else -> cache.tails[pageIndex] = KnownTail(tail, verdict, provisional = true, cache.completeKept)
        }
    }

    /**
     * The page's own picture, drawn for OCR. Counts towards the periodic reopen like a page read: drawing fills
     * PDFium's caches too.
     */
    fun render(pageIndex: Int, dpi: Int, maxPixels: Long): Outcome<PageImage, PdfPageError> = locked {
        if (pageIndex !in 0 until pageCount) return@locked outOfRange(pageIndex)
        currentHandle().flatMap { open ->
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
                PageReading(builder.build(reading), routing, PageSource.Ocr(langs), text.meanConfidence)
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

    /**
     * Never waits: safe on the main thread. Closes the document now when nobody is reading it, else marks the book
     * closing so the page being read stops at its next step and the reader closes the document as it lets go.
     */
    override fun close() {
        closing = true
        if (lock.isHeldByCurrentThread) return // The outermost call closes it on its way out.
        if (!closeIfFree()) AudoraLog.d(TAG, "Close deferred until the page being read lets go, $logContext")
    }

    /**
     * Runs [block] holding the book's lock, then closes the document if [close] was called meanwhile. The flag is
     * read after unlocking: either [close] saw the lock free, or this sees the flag (it was set before [close] tried).
     */
    private inline fun <T> locked(block: () -> T): T {
        lock.lockInterruptibly()
        try {
            return block()
        } finally {
            lock.unlock()
            if (closing && !lock.isHeldByCurrentThread) closeIfFree()
        }
    }

    /** Closes the document if the lock is free now; false when someone else holds it (they close it on unlock). */
    private fun closeIfFree(): Boolean {
        if (!lock.tryLock()) return false
        try {
            handle?.close()
            handle = null
            caches = null
        } finally {
            lock.unlock()
        }
        return true
    }

    private fun outOfRange(pageIndex: Int) = Outcome.Failure(PdfPageError.PageOutOfRange(pageIndex, pageCount))

    private fun verdictNow(): TextLayerVerdict = textLayerCheck?.verdict ?: TextLayerVerdict.UNCHECKED

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

    /** The document for a page read: opened again after [ExtractionConfig.reopenEveryPages] reads. */
    private fun currentHandle(): Outcome<PdfDocumentHandle, PdfPageError> {
        val open = handle
        return if (open != null && readsSinceOpen >= config.reopenEveryPages) reopen(open) else openHandle()
    }

    /**
     * The open document. Null between closing the old document and opening the new one only when that open was
     * interrupted (a reader that gave up on its page): it is opened again here, so the book stays readable.
     */
    private fun openHandle(): Outcome<PdfDocumentHandle, PdfPageError> {
        if (closing) return Outcome.Failure(PdfPageError.BookClosed)
        return handle?.let { Outcome.Success(it) } ?: reopen(old = null)
    }

    private fun reopen(old: PdfDocumentHandle?): Outcome<PdfDocumentHandle, PdfPageError> {
        old?.close()
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
                closing = true // The source is gone: every later call says the book is closed.
                Outcome.Failure(PdfPageError.ReopenFailed(reopened.error))
            }
        }
    }

    private class PageCaches(val owner: ReadingUnitBuilder) {
        val analyzed: MutableMap<Int, AnalyzedPage> = lru(owner.cleanupConfig.analyzedCachePages)
        val profiles: MutableMap<Int, PageProfile> = lru(owner.cleanupConfig.profileCachePages)

        /** What each page read hands on to the next ([PageCarry]); a few bytes each. */
        val tails: MutableMap<Int, KnownTail> = lru(owner.cleanupConfig.profileCachePages)

        /** How many complete hand-ons have been kept: a provisional one kept before the last may now be completed. */
        var completeKept = 0
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
        ): Outcome<PdfBook, PdfOpenError> = when (val opened = engine.open(password).flatMap(::withPages)) {
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

        /** A damaged page tree PDFium still opens has nothing to read or resume into: refused (and closed) here. */
        private fun withPages(handle: PdfDocumentHandle): Outcome<PdfDocumentHandle, PdfOpenError> =
            if (handle.pageCount > 0) {
                Outcome.Success(handle)
            } else {
                handle.close()
                Outcome.Failure(PdfOpenError.Damaged("the document has no pages"))
            }
    }
}

/**
 * A printed page label as shown: control and format characters (zero-width spaces, byte-order and bidi marks) dropped,
 * trimmed; null when nothing is left. Zero-width (non-)joiners stay: they change how Indic letters join.
 */
private fun cleanLabel(label: String): String? =
    label.filterNot { it.isISOControl() || (Character.getType(it) == Character.FORMAT.toInt() && it !in JOINERS) }
        .trim()
        .ifEmpty { null }

/** Zero-width non-joiner and joiner. */
private const val JOINERS = "\u200C\u200D"

/** How far a hand-on can be trusted, worst last: [maxOf] of two is the chain through both. */
private enum class Chain {
    /** Built from pages read as they read aloud, back to a page that needs nothing before it. */
    COMPLETE,

    /** Cut at `PageCarryConfig.maxCarryPages`, or through a scanned page read from the OCR cache: best effort. */
    PROVISIONAL,

    /** A page could not be read now (a scanned page not yet OCR'd, a page that failed): not kept at all. */
    MISSING,
}

/** What the page before hands on ([tail]) and how far it can be trusted ([chain]). */
private class HandOn(val tail: PageTail?, val chain: Chain) {
    companion object {
        /** Nothing to hand on, for certain: the first page, or a page that takes nothing from the one before. */
        val NONE = HandOn(null, Chain.COMPLETE)

        /** Nothing known past `PageCarryConfig.maxCarryPages`. */
        val CUT = HandOn(null, Chain.PROVISIONAL)

        /** A page before that could not be read quietly. */
        val MISSING = HandOn(null, Chain.MISSING)
    }
}

/**
 * A page's hand-on, kept with the text-layer verdict it was built under (a new verdict may change the page), whether
 * it is [provisional], and [completeKept] at the time (`PageCaches.completeKept`).
 */
private class KnownTail(
    val tail: PageTail,
    val verdict: TextLayerVerdict,
    val provisional: Boolean,
    val completeKept: Int,
)

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
 * or when the page before cannot be read without new OCR (a scanned page not yet recognised).
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
