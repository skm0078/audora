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
            Outcome.Success(PageImage(page, 4, 2, dpi, ByteArray(8) { it.toByte() }))
        }
    }

    private class FakeEngine(var text: String = "Some text.", var installed: Set<Lang> = Lang.entries.toSet()) :
        OcrEngine {
        val calls = ArrayList<Set<Lang>>()
        val images = ArrayList<PageImage>()
        var fail = false
        var confidence: (PageImage) -> Int = { CONFIDENT }
        override var cacheTag = "fake-1"

        override fun installedLangs() = installed

        override fun recognize(image: PageImage, langs: Set<Lang>): Outcome<RecognizedPage, OcrError> {
            calls.add(langs)
            images.add(image)
            if (fail) return Outcome.Failure(OcrError.Failed("engine broke"))
            val conf = confidence(image)
            val lines = text.lines().filter { it.isNotBlank() }.mapIndexed { i, line ->
                RecognizedLine(
                    line.split(" ").map { RecognizedWord(it, 0, i, image.width, i + 1, conf) },
                    paragraph = 0,
                )
            }
            return Outcome.Success(RecognizedPage(lines, image.width, image.height))
        }
    }

    private val engine = FakeEngine()

    private fun ocr(cache: OcrCache? = OcrCache(dir, 1_000_000), config: OcrConfig = OcrConfig()) =
        RenderedPageOcr(pages, engine, cache, "book", "book=test", config)

    private fun RenderedPageOcr.text(page: Int, langs: Set<Lang> = setOf(Lang.EN)) =
        recognize(page, langs).getOrNull()?.text

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun `a page is drawn at the OCR resolution, recognised once and then read from the cache`() {
        assertEquals("Some text.", ocr().text(3))
        // A second book object (the player opens its own) reads the first one's result.
        assertEquals("Some text.", ocr().text(3))
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
        assertEquals("Some text.", ocr().text(0))
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

    @Test
    fun `a sideways scan is turned upright before it is read, and its boxes stay on the page as displayed`() {
        // The 4 × 2 test image holds 0..7 row by row; turned a quarter clockwise its top-left pixel is 4.
        engine.confidence = { image -> if (image.gray[0] == 4.toByte()) CONFIDENT else SIDEWAYS }
        val config = OcrConfig(orientationDpi = OcrConfig().renderDpi)
        val text = requireNotNull(ocr(config = config).recognize(0, setOf(Lang.EN)).getOrNull())
        assertEquals(1, text.quarterTurns)
        assertEquals("Some text.", text.text)
        // As drawn, the four turns of the orientation check, then the page turned.
        assertEquals(6, engine.calls.size)
        assertEquals(2 to 4, engine.images.last().let { it.width to it.height })
        // Cached with its turn and boxes.
        val again = requireNotNull(ocr(config = config).recognize(0, setOf(Lang.EN)).getOrNull())
        assertEquals(1, again.quarterTurns)
        assertEquals(6, engine.calls.size)
        assertTrue(again.lines.all { line -> line.words.all { it.box != null } })
    }

    @Test
    fun `a page that reads well is never turned`() {
        ocr().recognize(0, setOf(Lang.EN))
        assertEquals(1, engine.calls.size)
    }

    @Test
    fun `a poor scan no turn helps stays as drawn`() {
        engine.confidence = { SIDEWAYS }
        val text = requireNotNull(ocr().recognize(0, setOf(Lang.EN)).getOrNull())
        assertEquals(0, text.quarterTurns)
        assertEquals(1 + OcrText.FULL_TURN, engine.calls.size)
    }

    @Test
    fun `margin strips are drawn small, read top and bottom, and cached`() {
        engine.text = "Header line"
        val margins = requireNotNull(ocr().margins(2, setOf(Lang.EN), 0, 0.5f).getOrNull())
        assertEquals(listOf("Header line"), margins.top)
        assertEquals(listOf("Header line"), margins.bottom)
        assertEquals(listOf(2 to OcrConfig().marginDpi), renders)
        assertEquals(listOf(4 to 1, 4 to 1), engine.images.map { it.width to it.height })
        ocr().margins(2, setOf(Lang.EN), 0, 0.5f)
        assertEquals(2, engine.calls.size)
    }

    private companion object {
        const val CONFIDENT = 90
        const val SIDEWAYS = 20
    }
}
