// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/ocr/RenderedPageOcr.kt
// Purpose: A book's OCR: draws the page, runs the OCR engine on it, caches the text and logs the time per page.

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.config.OcrConfig
import io.github.skm0078.audora.core.config.QualityConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.document.PageImage
import io.github.skm0078.audora.pdf.quality.OcrError
import io.github.skm0078.audora.pdf.quality.PageOcr
import io.github.skm0078.audora.pdf.quality.PageOcrFactory
import io.github.skm0078.audora.pdf.quality.PageRenderer
import io.github.skm0078.audora.pdf.quality.ocrLangs

/** Recognizes text in a page image. Tesseract on Android (`TesseractEngine`); fakes in tests. */
interface OcrEngine {
    /** Changes whenever the engine or its models change, so cached text from older ones is not reused. */
    val cacheTag: String

    /** The languages whose models are on the phone now. */
    fun installedLangs(): Set<Lang>

    /** Lines separated by line breaks, paragraphs by blank lines. Blocking; serializes callers if it must. */
    fun recognize(image: PageImage, langs: Set<Lang>): Outcome<String, OcrError>
}

/**
 * [PageOcr] over the book's own renderer (Build Spec §5 step 3c, ADR-025). Each page is drawn at
 * `OcrConfig.renderDpi`, read by [engine] and kept in [cache]; a page already in the cache costs a file read. Work on
 * the same page is serialized across every book object in the process (the reader and the player each open the
 * book), so the second one waits and reads the first one's result instead of recognising the page again.
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
    override fun recognize(pageIndex: Int, langs: Set<Lang>): Outcome<String, OcrError> {
        if (langs.isEmpty()) return Outcome.Failure(OcrError.Failed("no language to recognise page $pageIndex in"))
        return cached(OcrKey(bookKey, pageIndex, langs, config.renderDpi, engine.cacheTag), "page")
    }

    /** Reads the page at `OcrConfig.probeDpi` with every installed model and keeps the scripts it finds. */
    override fun detectLangs(pageIndex: Int): Outcome<Set<Lang>, OcrError> {
        val installed = engine.installedLangs()
        if (installed.isEmpty()) return Outcome.Failure(OcrError.NoModels)
        val key = OcrKey(bookKey, pageIndex, installed, config.probeDpi, engine.cacheTag)
        return when (val probe = cached(key, "probe")) {
            is Outcome.Success -> Outcome.Success(ocrLangs(probe.value, qualityConfig)).also { found ->
                AudoraLog.i(TAG, "OCR languages detected ${found.value}, $logContext page=$pageIndex from $installed")
            }

            is Outcome.Failure -> probe
        }
    }

    private fun cached(key: OcrKey, what: String): Outcome<String, OcrError> = synchronized(lockFor(key)) {
        cache?.get(key)?.let { return Outcome.Success(it) }
        val result = recognizeNow(key, what)
        if (result is Outcome.Success) cache?.put(key, result.value)
        result
    }

    private fun recognizeNow(key: OcrKey, what: String): Outcome<String, OcrError> {
        val started = System.nanoTime()
        val image = when (val drawn = pages.render(key.page, key.dpi, config.maxRenderPixels)) {
            is Outcome.Success -> drawn.value
            is Outcome.Failure -> return Outcome.Failure(OcrError.Failed("could not draw the page: ${drawn.error}"))
        }
        val drawnAt = System.nanoTime()
        val result = engine.recognize(image, key.langs)
        val done = System.nanoTime()
        val chars = (result as? Outcome.Success)?.value?.length
        AudoraLog.i(
            TAG,
            "OCR $what $logContext page=${key.page} langs=${key.langs} dpi=${image.dpi} " +
                "px=${image.width}x${image.height} renderMs=${(drawnAt - started) / NANOS_PER_MS} " +
                "recognizeMs=${(done - drawnAt) / NANOS_PER_MS} chars=${chars ?: "failed: ${result.errorText()}"}",
        )
        return result
    }

    private fun Outcome<String, OcrError>.errorText(): String = (this as? Outcome.Failure)?.error.toString()

    companion object {
        private const val TAG = "Audora.Ocr"
        private const val NANOS_PER_MS = 1_000_000L
        private const val LOCK_STRIPES = 64

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
