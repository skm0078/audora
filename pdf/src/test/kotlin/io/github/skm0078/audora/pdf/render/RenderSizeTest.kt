// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/render/RenderSizeTest.kt
// Purpose: Pages are drawn at the screen's width and their own aspect ratio, capped for very tall pages.

package io.github.skm0078.audora.pdf.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderSizeTest {
    @Test
    fun `a letter page is drawn at the screen width`() {
        assertEquals(RenderSize(1080, 1398), RenderSize.fit(612f, 792f, 1080, MAX))
    }

    @Test
    fun `a landscape page keeps its aspect ratio`() {
        assertEquals(RenderSize(1080, 835), RenderSize.fit(792f, 612f, 1080, MAX))
    }

    @Test
    fun `a very tall page is scaled down to the pixel cap`() {
        val size = RenderSize.fit(200f, 20_000f, 1080, MAX)
        assertTrue("size $size", size.width < 1080)
        assertTrue("size $size", size.width.toLong() * size.height <= MAX + size.width)
        assertEquals(100f, size.height.toFloat() / size.width, 1f)
    }

    @Test
    fun `sides never drop below one pixel`() {
        assertEquals(RenderSize(1, 1), RenderSize.fit(10_000f, 1f, 1, 1))
    }

    @Test
    fun `nonsense sizes are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { RenderSize.fit(0f, 792f, 1080, MAX) }
        assertThrows(IllegalArgumentException::class.java) { RenderSize.fit(612f, 792f, 0, MAX) }
    }

    private companion object {
        const val MAX = 4_000_000
    }
}
