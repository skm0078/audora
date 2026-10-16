// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/ocr/RenderedPageOcrTest.kt
// Purpose: Draw → recognise → cache, the language probe and failures, with a fake renderer and engine.

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.config.OcrConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.errorOrNull
import io.github.skm0078.audora.core.result.getOrNull
import io.github.skm0078.audora.pdf.document.PageImage
import io.github.skm0078.audora.pdf.document.PdfPageError
import io.github.skm0078.audora.pdf.quality.OcrError
import io.github.skm0078.audora.pdf.quality.PageRenderer
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderedPageOcrTest {
    private val dir: File = Files.createTempDirectory("ocr").toFile()
    private val renders = ArrayList<Pair<Int, Int>>()
    private var renderFails = false

    private val pages = PageRenderer { page, dpi, _ ->
        renders.add(page to dpi)
        if (renderFails) {
            Outcome.Failure(PdfPageError.ExtractionFailed(page, "broken"))
        } else {
            Outcome.Success(PageImage(page, 2, 1, dpi, ByteArray(2)))
        }
    }

    private class FakeEngine(var text: String = "Some text.", var installed: Set<Lang> = Lang.entries.toSet()) :
        OcrEngine {
        val calls = ArrayList<Set<Lang>>()
        var fail = false
        override var cacheTag = "fake-1"

        override fun installedLangs() = installed

        override fun recognize(image: PageImage, langs: Set<Lang>): Outcome<String, OcrError> {
            calls.add(langs)
            return if (fail) Outcome.Failure(OcrError.Failed("engine broke")) else Outcome.Success(text)
        }
    }

    private val engine = FakeEngine()

    private fun ocr(cache: OcrCache? = OcrCache(dir, 1_000_000)) =
        RenderedPageOcr(pages, engine, cache, "book", "book=test", OcrConfig())

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun `a page is drawn at the OCR resolution, recognised once and then read from the cache`() {
        assertEquals("Some text.", ocr().recognize(3, setOf(Lang.EN)).getOrNull())
        // A second book object (the player opens its own) reads the first one's result.
        assertEquals("Some text.", ocr().recognize(3, setOf(Lang.EN)).getOrNull())
        assertEquals(listOf(3 to OcrConfig().renderDpi), renders)
        assertEquals(listOf(setOf(Lang.EN)), engine.calls)
    }

    @Test
    fun `other languages or a new engine tag recognise the page again`() {
        ocr().recognize(3, setOf(Lang.EN))
        ocr().recognize(3, setOf(Lang.HI))
        engine.cacheTag = "fake-2"
        ocr().recognize(3, setOf(Lang.EN))
        assertEquals(3, engine.calls.size)
    }

    @Test
    fun `failures are typed and not cached`() {
        engine.fail = true
        assertEquals(OcrError.Failed("engine broke"), ocr().recognize(0, setOf(Lang.EN)).errorOrNull())
        engine.fail = false
        assertEquals("Some text.", ocr().recognize(0, setOf(Lang.EN)).getOrNull())
        renderFails = true
        val drawn = ocr().recognize(1, setOf(Lang.EN)).errorOrNull()
        assertTrue("$drawn", drawn is OcrError.Failed && drawn.detail.startsWith("could not draw the page"))
        assertTrue(ocr().recognize(1, emptySet()).errorOrNull() is OcrError.Failed)
    }

    @Test
    fun `works without a cache`() {
        ocr(cache = null).recognize(0, setOf(Lang.EN))
        ocr(cache = null).recognize(0, setOf(Lang.EN))
        assertEquals(2, engine.calls.size)
    }

    @Test
    fun `the probe reads the page small with every installed model and keeps the scripts it finds`() {
        engine.text = "সব মানুষ স্বাধীনভাবে জন্মগ্রহণ করে।"
        assertEquals(setOf(Lang.BN), ocr().detectLangs(0).getOrNull())
        assertEquals(listOf(0 to OcrConfig().probeDpi), renders)
        assertEquals(listOf(Lang.entries.toSet()), engine.calls)
    }

    @Test
    fun `no installed model means no probe`() {
        engine.installed = emptySet()
        assertEquals(OcrError.NoModels, ocr().detectLangs(0).errorOrNull())
        assertTrue(renders.isEmpty())
    }
}
