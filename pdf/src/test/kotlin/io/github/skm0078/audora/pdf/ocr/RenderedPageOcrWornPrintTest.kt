// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/ocr/RenderedPageOcrWornPrintTest.kt
// Purpose: When a scanned black and white page is read again softened, and when the softened read is kept.

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.config.LightingConfig
import io.github.skm0078.audora.core.config.OcrConfig
import io.github.skm0078.audora.core.config.WornPrintConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.getOrNull
import io.github.skm0078.audora.pdf.document.PageImage
import io.github.skm0078.audora.pdf.quality.OcrError
import io.github.skm0078.audora.pdf.quality.PageRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderedPageOcrWornPrintTest {
    /** Ink and paper in alternate columns (a black and white scan), or a flat grey (a photo). */
    private var bilevel = true
    private var drawnGray = ByteArray(0)

    private val pages = PageRenderer { page, dpi, _ ->
        drawnGray = ByteArray(WIDTH * HEIGHT) { i ->
            (
                if (!bilevel) {
                    GREY
                } else if (i % 2 == 0) {
                    INK
                } else {
                    PAPER
                }
                ).toByte()
        }
        Outcome.Success(PageImage(page, WIDTH, HEIGHT, dpi, drawnGray))
    }

    /** Reads [drawn] from the page as rendered and [softened] from any other image of it. */
    private inner class FakeEngine : OcrEngine {
        var drawn = "as drawn here" to UNSURE
        var softened = "softened read here" to SURE
        var softenedFails = false
        val images = ArrayList<PageImage>()
        override val cacheTag = "fake"

        override fun installedLangs() = setOf(Lang.EN)

        override fun recognize(image: PageImage, langs: Set<Lang>): Outcome<RecognizedPage, OcrError> {
            images.add(image)
            val asDrawn = image.gray.contentEquals(drawnGray)
            if (!asDrawn && softenedFails) return Outcome.Failure(OcrError.Failed("engine stopped"))
            val (text, conf) = if (asDrawn) drawn else softened
            val words = text.split(" ").filter { it.isNotEmpty() }.map { RecognizedWord(it, 0, 0, 1, 1, conf) }
            return Outcome.Success(RecognizedPage(listOf(RecognizedLine(words, 0)), image.width, image.height))
        }
    }

    private val engine = FakeEngine()

    private fun read(worn: WornPrintConfig = WornPrintConfig()): String? = RenderedPageOcr(
        pages,
        engine,
        null,
        "book",
        "book=test",
        OcrConfig(lighting = LightingConfig(enabled = false), wornPrint = worn),
    ).recognize(0, setOf(Lang.EN)).getOrNull()?.text

    @Test
    fun `an unsure black and white page is read again softened and the clearer read kept`() {
        assertEquals("softened read here", read())
        assertEquals(2, engine.images.size)
        val softened = engine.images[1]
        assertTrue(WornPrint.bilevelShare(softened, WornPrintConfig()) < WornPrintConfig().minBilevelShare)
    }

    @Test
    fun `a sure page, a grey page or worn print off are read once`() {
        engine.drawn = "as drawn here" to SURE
        assertEquals("as drawn here", read())
        engine.drawn = "as drawn here" to UNSURE
        bilevel = false
        assertEquals("as drawn here", read())
        bilevel = true
        assertEquals("as drawn here", read(WornPrintConfig(enabled = false)))
        assertEquals(3, engine.images.size)
    }

    @Test
    fun `a softened read that is not clearly surer, loses words or is unsure itself is dropped`() {
        val worn = WornPrintConfig()
        val cases = listOf(
            ("as drawn here" to UNSURE) to ("softened read here" to UNSURE + worn.minGain - 1),
            ("as drawn here" to UNSURE) to ("softened" to SURE),
            ("" to UNSURE) to ("zz" to worn.minSoftenedConfidence - 1),
        )
        for ((drawn, softened) in cases) {
            engine.drawn = drawn
            engine.softened = softened
            assertEquals("$softened", drawn.first, read())
            assertFalse("$softened was read", engine.images.last().gray.contentEquals(drawnGray))
        }
    }

    @Test
    fun `a softened read that fails keeps the first read`() {
        engine.softenedFails = true
        assertEquals("as drawn here", read())
        assertEquals(2, engine.images.size)
    }

    private companion object {
        const val WIDTH = 300
        const val HEIGHT = 24
        const val INK = 10
        const val PAPER = 245
        const val GREY = 128

        // Above the orientation check and the line retry, below the worn print check.
        const val UNSURE = 75
        const val SURE = 95
    }
}
