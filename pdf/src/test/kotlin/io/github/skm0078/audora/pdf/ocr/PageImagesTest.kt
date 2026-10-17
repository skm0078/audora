// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/ocr/PageImagesTest.kt
// Purpose: Page images turn a quarter at a time, shrink by averaging, and cut into strips, pixel for pixel.

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.pdf.document.PageImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PageImagesTest {
    /** 3 × 2: 0 1 2 / 3 4 5. */
    private val image = PageImage(0, 3, 2, 300, ByteArray(6) { it.toByte() })

    private fun PageImage.rows(): List<List<Int>> = (0 until height).map { y ->
        (0 until width).map { x ->
            gray[
                y *
                    width +
                    x,
            ].toInt()
        }
    }

    @Test
    fun `quarter turns clockwise`() {
        assertSame(image, image.turned(0))
        assertEquals(listOf(listOf(3, 0), listOf(4, 1), listOf(5, 2)), image.turned(1).rows())
        assertEquals(listOf(listOf(5, 4, 3), listOf(2, 1, 0)), image.turned(2).rows())
        assertEquals(listOf(listOf(2, 5), listOf(1, 4), listOf(0, 3)), image.turned(3).rows())
        assertEquals(image.rows(), image.turned(1).turned(3).rows())
    }

    @Test
    fun `shrinking averages whole blocks and never enlarges`() {
        val big = PageImage(0, 4, 2, 300, byteArrayOf(0, 10, 20, 30, 20, 30, 40, 50))
        val small = big.shrunkTo(150)
        assertEquals(listOf(listOf(15, 35)), small.rows())
        assertEquals(150, small.dpi)
        assertSame(big, big.shrunkTo(300))
        assertSame(big, big.shrunkTo(600))
    }

    @Test
    fun `strips are whole rows`() {
        assertEquals(listOf(listOf(0, 1, 2)), image.strip(0f, 0.5f)?.rows())
        assertEquals(listOf(listOf(3, 4, 5)), image.strip(0.5f, 1f)?.rows())
        assertNull(image.strip(0.1f, 0.2f))
    }
}
