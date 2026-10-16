// File: pdf/src/androidTest/kotlin/io/github/skm0078/audora/pdf/pdfium/PdfiumPageRendererTest.kt
// Purpose: P4-03 done-when on real PDFium: the line boxes of every sentence land on the ink of the drawn page.

package io.github.skm0078.audora.pdf.pdfium

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.core.result.errorOrNull
import io.github.skm0078.audora.core.result.getOrNull
import io.github.skm0078.audora.pdf.document.PdfBook
import io.github.skm0078.audora.pdf.document.PdfPageError
import io.github.skm0078.audora.pdf.render.ViewRect
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder
import java.io.File
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfiumPageRendererTest {
    @Test
    fun englishPageHighlightsCoverItsInk() {
        val file = TestPdfs.file(EN)
        assertHighlightsCoverInk(file, page = 0, expectLandscape = false)
    }

    @Test
    fun rotatedPagesAreDrawnTurnedAndHighlightsFollow() {
        listOf(90, 180, 270).forEach { rotate ->
            val file = File(context.cacheDir, "test-pdfs/rotated-$rotate.pdf")
            file.parentFile?.mkdirs()
            GeneratedPdf.write(file, pages = 1, linesPerPage = ROTATED_LINES, rotate = rotate)
            assertHighlightsCoverInk(file, page = 0, expectLandscape = rotate % 180 != 0)
        }
    }

    @Test
    fun outOfRangeAndClosedAreTypedErrors() {
        val renderer = requireNotNull(TestPdfs.engine(TestPdfs.file(EN)).openRenderer(null, "test").getOrNull())
        val outOfRange = renderer.render(PAGE_COUNT_EN, WIDTH, MAX_PIXELS)
        assertEquals(PdfPageError.PageOutOfRange(PAGE_COUNT_EN, PAGE_COUNT_EN), outOfRange.errorOrNull())
        renderer.close()
        assertEquals(PdfPageError.BookClosed, renderer.render(0, WIDTH, MAX_PIXELS).errorOrNull())
    }

    /**
     * Draws [page] and checks the sentences' line boxes against the pixels: every box covers some ink, and most
     * of the page's ink lies under a box (what cleanup drops, such as a footer, is the only ink outside). A
     * transform that is off by a rotation or a flip puts the boxes on blank paper and fails both.
     */
    private fun assertHighlightsCoverInk(file: File, page: Int, expectLandscape: Boolean) {
        val units = requireNotNull(
            PdfBook.open(TestPdfs.engine(file), logContext = "test=${file.name}").getOrNull(),
        ).use { requireNotNull(it.readingUnits(page, ReadingUnitBuilder()).getOrNull()) }
        assertTrue("${file.name}: no units", units.isNotEmpty())
        val renderer = requireNotNull(TestPdfs.engine(file).openRenderer(null, "test=${file.name}").getOrNull())
        val rendered = renderer.use { requireNotNull(it.render(page, WIDTH, MAX_PIXELS).getOrNull()) }
        val bitmap = rendered.bitmap
        assertEquals(WIDTH, bitmap.width)
        assertEquals("${file.name}: landscape", expectLandscape, bitmap.width > bitmap.height)
        assertEquals(rendered.aspectRatio, bitmap.width.toFloat() / bitmap.height, ASPECT_TOLERANCE)

        val rects = units.flatMap { it.bounds.orEmpty() }.map(rendered.transform::toView)
        rects.forEach { rect ->
            assertTrue("${file.name}: $rect off the page", rect.left >= -EDGE && rect.right <= 1 + EDGE)
            assertTrue("${file.name}: $rect off the page", rect.top >= -EDGE && rect.bottom <= 1 + EDGE)
            assertTrue("${file.name}: no ink under $rect", bitmap.inkIn(rect) > 0)
        }
        val ink = bitmap.inkIn(ViewRect(0f, 0f, 1f, 1f))
        val covered = bitmap.inkUnder(rects)
        assertTrue("${file.name}: only $covered of $ink ink pixels under a box", covered >= ink * MIN_INK_COVERED)
    }

    private fun Bitmap.inkIn(rect: ViewRect): Int = pixelsIn(rect).count { isInk(it) }

    private fun Bitmap.inkUnder(rects: List<ViewRect>): Int {
        val under = HashSet<Int>()
        rects.forEach { rect -> pixelsIn(rect).filter { isInk(it) }.forEach { under += it } }
        return under.size
    }

    /** Indices (y · width + x) of the pixels in [rect], grown by [PAD_PX] for anti-aliased glyph edges. */
    private fun Bitmap.pixelsIn(rect: ViewRect): Sequence<Int> {
        val left = ((rect.left * width).roundToInt() - PAD_PX).coerceIn(0, width)
        val right = ((rect.right * width).roundToInt() + PAD_PX).coerceIn(0, width)
        val top = ((rect.top * height).roundToInt() - PAD_PX).coerceIn(0, height)
        val bottom = ((rect.bottom * height).roundToInt() + PAD_PX).coerceIn(0, height)
        return sequence { for (y in top until bottom) for (x in left until right) yield(y * width + x) }
    }

    private fun Bitmap.isInk(index: Int): Boolean {
        val pixel = getPixel(index % width, index / width)
        val luminance = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / RGB_CHANNELS
        return luminance < INK_LUMINANCE
    }

    private companion object {
        val context get() = InstrumentationRegistry.getInstrumentation().targetContext
        const val EN = "en-udhr-noto.pdf"
        const val PAGE_COUNT_EN = 8
        const val ROTATED_LINES = 20
        const val WIDTH = 600
        const val MAX_PIXELS = 4_000_000
        const val ASPECT_TOLERANCE = 0.01f
        const val EDGE = 0.01f
        const val PAD_PX = 2
        const val RGB_CHANNELS = 3
        const val INK_LUMINANCE = 128
        const val MIN_INK_COVERED = 0.8
    }
}
