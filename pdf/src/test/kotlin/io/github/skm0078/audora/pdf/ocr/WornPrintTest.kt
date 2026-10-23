// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/ocr/WornPrintTest.kt
// Purpose: The black and white share of a page, the softening radius for its resolution, and the softened image.

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.config.WornPrintConfig
import io.github.skm0078.audora.pdf.document.PageImage
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class WornPrintTest {
    private val config = WornPrintConfig()

    private fun image(width: Int, height: Int, dpi: Int = DPI, grey: (Int, Int) -> Int): PageImage =
        PageImage(PAGE, width, height, dpi, ByteArray(width * height) { grey(it % width, it / width).toByte() })

    private fun PageImage.greys(): List<Int> = gray.map { it.toInt() and 0xFF }

    @Test
    fun `the black and white share counts pixels past either threshold`() {
        val cases = listOf(
            "ink and paper" to image(4, 1) { x, _ -> if (x % 2 == 0) 0 else 255 } to 1f,
            "mid grey" to image(4, 1) { _, _ -> 128 } to 0f,
            "half grey" to image(4, 1) { x, _ -> if (x < 2) 255 else 128 } to 0.5f,
            "thresholds are exclusive" to image(2, 1) { x, _ -> if (x == 0) config.darkBelow else config.lightAbove } to
                0f,
        )
        for ((named, expected) in cases) {
            val (name, page) = named
            assertEquals(name, expected, WornPrint.bilevelShare(page, config), DELTA)
        }
    }

    @Test
    fun `the radius is a three-hundredth of an inch, at least one pixel`() {
        val cases = listOf(DPI to 1, 2 * DPI to 2, LOW_DPI to 1)
        for ((dpi, radius) in cases) {
            assertEquals("$dpi dpi", radius, WornPrint.radiusFor(image(1, 1, dpi) { _, _ -> 0 }, config))
        }
    }

    @Test
    fun `a lone white pixel spreads evenly over its neighbours and the page keeps its size`() {
        val page = image(3, 3) { x, y -> if (x == 1 && y == 1) 255 else 0 }
        val softened = WornPrint.softened(page, radius = 1, passes = 1)
        // Row then column: 255 / 3 = 85, then 85 / 3 = 28 everywhere (edges repeat their outermost pixel).
        assertEquals(List(9) { 28 }, softened.greys())
        assertEquals(listOf(PAGE, 3, 3, DPI), listOf(softened.pageIndex, softened.width, softened.height, softened.dpi))
    }

    @Test
    fun `a flat page, a single pixel and a page wider than the radius stay as they are where flat`() {
        val flat = image(5, 4) { _, _ -> 200 }
        assertArrayEquals(flat.gray, WornPrint.softened(flat, radius = 2, passes = 2).gray)
        val dot = image(1, 1) { _, _ -> 77 }
        assertArrayEquals(dot.gray, WornPrint.softened(dot, radius = 3, passes = 2).gray)
        // A dark left half and a light right half: far from the edge each side keeps its grey, the edge is between.
        val edge = WornPrint.softened(image(8, 1) { x, _ -> if (x < 4) 0 else 255 }, radius = 1, passes = 2).greys()
        assertEquals(listOf(0, 0), edge.take(2))
        assertEquals(listOf(255, 255), edge.takeLast(2))
        assertEquals(edge.sorted(), edge)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a radius of zero is refused`() {
        WornPrint.softened(image(1, 1) { _, _ -> 0 }, radius = 0, passes = 1)
    }

    private companion object {
        const val PAGE = 4
        const val DPI = 300
        const val LOW_DPI = 72
        const val DELTA = 1e-6f
    }
}
