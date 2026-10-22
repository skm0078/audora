// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/ocr/RenderedPageOcr.kt
// Purpose: A book's OCR: draws the page, turns a sideways scan upright, reads it, caches the words and logs the time.

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.config.OcrConfig
import io.github.skm0078.audora.core.config.QualityConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.flatMap
import io.github.skm0078.audora.core.result.map
import io.github.skm0078.audora.pdf.document.PageImage
import io.github.skm0078.audora.pdf.quality.OcrError
import io.github.skm0078.audora.pdf.quality.PageOcr
import io.github.skm0078.audora.pdf.quality.PageOcrFactory
import io.github.skm0078.audora.pdf.quality.PageRenderer
import io.github.skm0078.audora.pdf.quality.ocrLangs
import java.util.Locale

/** Recognizes text in a page image. Tesseract on Android (`TesseractEngine`); fakes in tests. */
interface OcrEngine {
    /** Changes whenever the engine or its models change, so cached text from older ones is not reused. */
    val cacheTag: String

    /** The languages whose models are on the phone now. */
    fun installedLangs(): Set<Lang>

    /** Words with pixel boxes and confidences, in reading order. Blocking; serializes callers if it must. */
    fun recognize(image: PageImage, langs: Set<Lang>): Outcome<RecognizedPage, OcrError>
}

/**
 * [PageOcr] over the book's own renderer (Build Spec §5 step 3c, ADR-025, ADR-032). Each page is drawn at
 * `OcrConfig.renderDpi`, read by [engine] and kept in [cache]; a page already in the cache costs a file read. Work on
 * the same page is serialized across every book object in the process (the reader and the player each open the
 * book), so the second one waits and reads the first one's result instead of recognising the page again.
 *
 * A page that reads with a low confidence may be a sideways or upside-down scan: a shrunk copy is read turned each
 * way and, when one turn reads clearly better, the page is read again turned that way ([OcrText.quarterTurns]).
 *
 * Logs one line per recognised page with the time spent drawing and recognising it (done-when: per-page time on
 * the test phones), never the text.
 */
@Suppress("LongParameterList") // The book, its renderer and logs, the engine, the cache and two configs.
class RenderedPageOcr(
    private val pages: PageRenderer,
    private val engine: OcrEngine,
    private val cache: OcrCache?,
    private val bookKey: String,
    private val logContext: String,
    private val config: OcrConfig = OcrConfig(),
    private val qualityConfig: QualityConfig = QualityConfig(),
) : PageOcr {
    private val lineRetry = OcrLineRetry(engine, config, logContext)
    private val ruleFinder = RuleFinder(config.rules)

    override fun recognize(pageIndex: Int, langs: Set<Lang>): Outcome<OcrText, OcrError> {
        if (langs.isEmpty()) return Outcome.Failure(OcrError.Failed("no language to recognise page $pageIndex in"))
        val key = OcrKey(bookKey, pageIndex, langs, config.renderDpi, engine.cacheTag, PAGE_VARIANT)
        return cached(key) { image -> readUpright(image, key.langs) }
    }

    /** Reads the page at `OcrConfig.probeDpi` with every installed model and keeps the scripts it finds. */
    override fun detectLangs(pageIndex: Int): Outcome<Set<Lang>, OcrError> {
        val installed = engine.installedLangs()
        if (installed.isEmpty()) return Outcome.Failure(OcrError.NoModels)
        val key = OcrKey(bookKey, pageIndex, installed, config.probeDpi, engine.cacheTag, variant = "probe")
        return when (val probe = cached(key) { image -> engine.recognize(image, installed).map { it.toOcrText(0) } }) {
            is Outcome.Success -> Outcome.Success(ocrLangs(probe.value.text, qualityConfig)).also { found ->
                AudoraLog.i(TAG, "OCR languages detected ${found.value}, $logContext page=$pageIndex from $installed")
            }

            is Outcome.Failure -> probe
        }
    }

    /**
     * The page drawn at `OcrConfig.marginDpi`, turned like the page it is compared with, and only its top and bottom
     * [share] read. Cached as two paragraphs: 0 the top strip, 1 the bottom.
     */
    override fun margins(
        pageIndex: Int,
        langs: Set<Lang>,
        quarterTurns: Int,
        share: Float,
    ): Outcome<OcrMargins, OcrError> {
        if (langs.isEmpty()) return Outcome.Failure(OcrError.Failed("no language to read page $pageIndex's margins in"))
        val variant = String.format(Locale.ROOT, "margins%d-%.3f", quarterTurns, share)
        val key = OcrKey(bookKey, pageIndex, langs, config.marginDpi, engine.cacheTag, variant)
        val strips = cached(key) { image -> readStrips(image.turned(quarterTurns), langs, share) }
        return strips.map { text ->
            OcrMargins(
                top = text.lines.filter { it.paragraph == TOP_STRIP }.map { it.text },
                bottom = text.lines.filter { it.paragraph != TOP_STRIP }.map { it.text },
            )
        }
    }

    private fun readStrips(image: PageImage, langs: Set<Lang>, share: Float): Outcome<OcrText, OcrError> {
        val lines = ArrayList<OcrLine>()
        for ((paragraph, strip) in listOf(
            TOP_STRIP to image.strip(0f, share),
            BOTTOM_STRIP to image.strip(1 - share, 1f),
        )) {
            if (strip == null) continue
            when (val read = engine.recognize(strip, langs)) {
                is Outcome.Success -> read.value.lines.mapTo(lines) { line ->
                    OcrLine(line.words.map { OcrWord(it.text, null) }, paragraph)
                }

                is Outcome.Failure -> return read
            }
        }
        return Outcome.Success(OcrText(lines))
    }

    /**
     * The page as drawn, unless it reads poorly and a shrunk copy turned a quarter, half or three quarters reads at
     * least `OcrConfig.orientationMinGain` more confidently: then the page turned that way. Its unsure lines are then
     * read again with the other models ([OcrLineRetry]), and its rules found ([RuleFinder]).
     */
    private fun readUpright(image: PageImage, langs: Set<Lang>): Outcome<OcrText, OcrError> =
        engine.recognize(image, langs).flatMap { asDrawn ->
            val turns = if (asDrawn.meanConfidence >= config.orientationCheckBelow) 0 else bestTurn(image, langs)
            if (turns == 0) {
                Outcome.Success(finished(asDrawn, image, langs, 0))
            } else {
                val upright = image.turned(turns)
                engine.recognize(upright, langs).map { finished(it, upright, langs, turns) }
            }
        }

    /** [page], read from [upright], with its unsure lines read again and the rules found in [upright]. */
    private fun finished(page: RecognizedPage, upright: PageImage, langs: Set<Lang>, turns: Int): OcrText {
        val improved = lineRetry.improve(page, upright, langs)
        val started = System.nanoTime()
        val rules = ruleFinder.find(upright, improved.lineHeight)
        AudoraLog.d(
            TAG,
            "OCR rules $logContext page=${upright.pageIndex} rules=${rules.size} " +
                "ms=${(System.nanoTime() - started) / NANOS_PER_MS}",
        )
        return improved.toOcrText(turns, rules)
    }

    /** The quarter turns that make a shrunk copy of [image] read best, or 0 when none reads clearly better. */
    private fun bestTurn(image: PageImage, langs: Set<Lang>): Int {
        val small = image.shrunkTo(config.orientationDpi)
        val scores = IntArray(OcrText.FULL_TURN) { turns ->
            (engine.recognize(small.turned(turns), langs) as? Outcome.Success)?.value?.meanConfidence ?: 0
        }
        val best = scores.indices.maxBy { scores[it] }
        AudoraLog.i(TAG, "OCR orientation check $logContext page=${image.pageIndex} turns=${scores.joinToString(",")}")
        return if (scores[best] >= scores[0] + config.orientationMinGain) best else 0
    }

    private fun cached(key: OcrKey, read: (PageImage) -> Outcome<OcrText, OcrError>): Outcome<OcrText, OcrError> =
        synchronized(lockFor(key)) {
            cache?.get(key)?.let(OcrTextCodec::decode)?.let { return Outcome.Success(it) }
            val result = recognizeNow(key, read)
            if (result is Outcome.Success) cache?.put(key, OcrTextCodec.encode(result.value))
            result
        }

    private fun recognizeNow(key: OcrKey, read: (PageImage) -> Outcome<OcrText, OcrError>): Outcome<OcrText, OcrError> {
        val started = System.nanoTime()
        val image = when (val drawn = pages.render(key.page, key.dpi, config.maxRenderPixels)) {
            is Outcome.Success -> drawn.value
            is Outcome.Failure -> return Outcome.Failure(OcrError.Failed("could not draw the page: ${drawn.error}"))
        }
        val drawnAt = System.nanoTime()
        val result = read(image)
        val done = System.nanoTime()
        val outcome = when (result) {
            is Outcome.Success -> "chars=${result.value.text.length} turns=${result.value.quarterTurns}"
            is Outcome.Failure -> "failed: ${result.error}"
        }
        AudoraLog.i(
            TAG,
            "OCR ${key.variant} $logContext page=${key.page} langs=${key.langs} dpi=${image.dpi} " +
                "px=${image.width}x${image.height} renderMs=${(drawnAt - started) / NANOS_PER_MS} " +
                "recognizeMs=${(done - drawnAt) / NANOS_PER_MS} $outcome",
        )
        return result
    }

    companion object {
        private const val TAG = "Audora.Ocr"
        private const val NANOS_PER_MS = 1_000_000L
        private const val LOCK_STRIPES = 64
        private const val TOP_STRIP = 0
        private const val BOTTOM_STRIP = 1

        /** Pages read with the line retry (RI-3) and their rules found (RI-4a part 2); older text is not reused. */
        private const val PAGE_VARIANT = "page-r2"

        /** Striped, so the locks stay few however many pages a session reads. */
        private val locks = Array(LOCK_STRIPES) { Any() }

        private fun lockFor(key: OcrKey): Any = locks[Math.floorMod(key.hashCode(), LOCK_STRIPES)]

        /** What `PdfBook.open(ocr = …)` takes: this OCR bound to the book being opened. */
        @Suppress("LongParameterList") // Everything the constructor takes except the renderer.
        fun factory(
            engine: OcrEngine,
            cache: OcrCache?,
            bookKey: String,
            logContext: String,
            config: OcrConfig = OcrConfig(),
            qualityConfig: QualityConfig = QualityConfig(),
        ): PageOcrFactory = PageOcrFactory { pages ->
            RenderedPageOcr(pages, engine, cache, bookKey, logContext, config, qualityConfig)
        }
    }
}
