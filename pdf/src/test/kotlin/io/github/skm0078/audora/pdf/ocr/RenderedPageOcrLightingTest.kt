// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/ocr/RenderedPageOcrLightingTest.kt
// Purpose: When a page is read again with its light evened out, and when the evened read is kept.

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.config.LightingConfig
import io.github.skm0078.audora.core.config.OcrConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.getOrNull
import io.github.skm0078.audora.pdf.document.PageImage
import io.github.skm0078.audora.pdf.quality.OcrError
import io.github.skm0078.audora.pdf.quality.PageRenderer
import org.junit.Assert.assertEquals
import org.junit.Test

class RenderedPageOcrLightingTest {
    /** A page half in shadow (right half dark), or lit evenly. */
    private var shadow = true

    private val pages = PageRenderer { page, dpi, _ ->
        val gray = ByteArray(WIDTH * HEIGHT) { i -> (if (shadow && i % WIDTH >= WIDTH / 2) DARK else LIT).toByte() }
        Outcome.Success(PageImage(page, WIDTH, HEIGHT, dpi, gray))
    }

    /** Reads [drawn] from the page as drawn and [evened] from a page whose right half is now white. */
    private class FakeEngine : OcrEngine {
        var drawn = "as drawn here" to UNSURE
        var evened = "evened out here" to SURE
        val images = ArrayList<PageImage>()
        override val cacheTag = "fake"

        override fun installedLangs() = setOf(Lang.EN)

        override fun recognize(image: PageImage, langs: Set<Lang>): Outcome<RecognizedPage, OcrError> {
            images.add(image)
            val right = image.gray[image.width - 1].toInt() and 0xFF
            val (text, conf) = if (right >= WHITE) evened else drawn
            val words = text.split(" ").filter { it.isNotEmpty() }.map { RecognizedWord(it, 0, 0, 1, 1, conf) }
            return Outcome.Success(RecognizedPage(listOf(RecognizedLine(words, 0)), image.width, image.height))
        }
    }

    private val engine = FakeEngine()

    private fun read(lighting: LightingConfig = LightingConfig()): String? =
        RenderedPageOcr(pages, engine, null, "book", "book=test", OcrConfig(lighting = lighting))
            .recognize(0, setOf(Lang.EN)).getOrNull()?.text

    @Test
    fun `an unsure page half in shadow is read again evened and the clearer read kept`() {
        assertEquals("evened out here", read())
        assertEquals(2, engine.images.size)
    }

    @Test
    fun `a sure page, an evenly lit page or lighting off are read once`() {
        engine.drawn = "as drawn here" to SURE
        assertEquals("as drawn here", read())
        engine.drawn = "as drawn here" to UNSURE
        shadow = false
        assertEquals("as drawn here", read())
        shadow = true
        assertEquals("as drawn here", read(LightingConfig(enabled = false)))
        assertEquals(3, engine.images.size)
    }

    @Test
    fun `an evened read that is not clearly surer, or loses words, is dropped`() {
        engine.evened = "evened out here" to UNSURE + LightingConfig().minGain - 1
        assertEquals("as drawn here", read())
        // A cover's white title on a grey band: evened, the band turns white and the title is gone.
        engine.evened = "evened" to SURE
        assertEquals("as drawn here", read())
        assertEquals(4, engine.images.size)
    }

    @Test
    fun `an evened read that is surer on average but holds less, or is unsure itself, is dropped`() {
        // Nine of ten words kept, all sure, but the long ones gone: the mean rises, the confidence in all is lower.
        engine.drawn = List(10) { "aaaa" }.joinToString(" ") to UNSURE
        engine.evened = List(9) { "b" }.joinToString(" ") to SURE
        assertEquals(engine.drawn.first, read())
        // A page that read no words takes no junk from the evened read.
        engine.drawn = "" to UNSURE
        engine.evened = "zz" to LightingConfig().minEvenedConfidence - 1
        assertEquals("", read())
    }

    private companion object {
        const val WIDTH = 300 // 33 blocks at 300 dpi: the right edge is well clear of the shadow's edge.
        const val HEIGHT = 24
        const val LIT = 230
        const val DARK = 90
        const val WHITE = 250

        // Above the orientation check and the line retry, below the lighting check.
        const val UNSURE = 75
        const val SURE = 95
    }
}
