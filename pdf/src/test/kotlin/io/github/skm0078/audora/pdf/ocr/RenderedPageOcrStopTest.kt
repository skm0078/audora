// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/ocr/RenderedPageOcrStopTest.kt
// Purpose: Page OCR stops between steps for a closing book or an interrupted reader and survives low memory.

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.document.PageImage
import io.github.skm0078.audora.pdf.document.PdfPageError
import io.github.skm0078.audora.pdf.quality.OcrError
import io.github.skm0078.audora.pdf.quality.PageRenderer
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderedPageOcrStopTest {
    private val dir: File = Files.createTempDirectory("ocr-stop").toFile()

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    /** Draws a small page; [abandoned] says whether its book is closing. */
    private class Pages(var abandoned: Boolean = false, var outOfMemory: Boolean = false) : PageRenderer {
        override fun render(pageIndex: Int, dpi: Int, maxPixels: Long): Outcome<PageImage, PdfPageError> {
            if (outOfMemory) throw OutOfMemoryError("Failed to allocate a 48 MB bitmap")
            return Outcome.Success(PageImage(pageIndex, 4, 4, dpi, ByteArray(16)))
        }

        override fun abandoned(): Boolean = abandoned
    }

    /** Reads every image unsure, so the page would go on to the orientation check; [onRead] runs after each read. */
    private class UnsureEngine(val onRead: (Int) -> Unit = {}) : OcrEngine {
        var calls = 0
        override val cacheTag = "fake-unsure"

        override fun installedLangs() = setOf(Lang.EN)

        override fun recognize(image: PageImage, langs: Set<Lang>): Outcome<RecognizedPage, OcrError> {
            calls++
            onRead(calls)
            val words = listOf(RecognizedWord("faint", 0, 0, image.width, 1, UNSURE))
            return Outcome.Success(RecognizedPage(listOf(RecognizedLine(words, 0)), image.width, image.height))
        }
    }

    private fun ocr(pages: PageRenderer, engine: OcrEngine) =
        RenderedPageOcr(pages, engine, OcrCache(dir, 1_000_000), "book", "book=test")

    @Test
    fun `a closing book's page stops after the step in progress and is not cached`() {
        val pages = Pages()
        val engine = UnsureEngine { calls -> if (calls == 1) pages.abandoned = true }
        val read = ocr(pages, engine).recognize(0, setOf(Lang.EN))
        assertEquals(Outcome.Failure(OcrError.Failed("stopped: the book is closing")), read)
        assertEquals("the orientation check never ran", 1, engine.calls)
        pages.abandoned = false
        assertTrue("read again once wanted", ocr(pages, engine).recognize(0, setOf(Lang.EN)) is Outcome.Success)
    }

    @Test
    fun `an interrupted reader stops between steps with InterruptedException`() {
        val engine = UnsureEngine { Thread.currentThread().interrupt() }
        assertThrows(InterruptedException::class.java) { ocr(Pages(), engine).recognize(0, setOf(Lang.EN)) }
        assertEquals(1, engine.calls)
        assertTrue("the interrupt is consumed", !Thread.interrupted())
    }

    @Test
    fun `a page too large for memory fails instead of crashing`() {
        val read = ocr(Pages(outOfMemory = true), UnsureEngine()).recognize(0, setOf(Lang.EN))
        val error = (read as Outcome.Failure).error as OcrError.Failed
        assertEquals("out of memory reading the page", error.detail)
    }

    private companion object {
        const val UNSURE = 20
    }
}
