// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/document/RenderSizeTest.kt
// Purpose: OCR page images keep the requested dpi when they fit the pixel budget and shrink when they do not.

package io.github.skm0078.audora.pdf.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderSizeTest {
    @Test
    fun `an A4 page at 300 dpi fits the default budget`() {
        assertEquals(RenderSize(2480, 3509, 300), RenderSize.of(595, 842, 300, 12_000_000))
    }

    @Test
    fun `a large page is drawn at the highest dpi that fits`() {
        val cases = listOf(
            Triple(1684, 2384, 12_000_000L), // A1 poster
            Triple(595, 842, 4_000_000L),
            Triple(14_400, 14_400, 1_000_000L), // PDF's largest page
        )
        for ((w, h, budget) in cases) {
            val size = requireNotNull(RenderSize.of(w, h, 300, budget))
            val next = requireNotNull(RenderSize.of(w, h, size.dpi + 1, Long.MAX_VALUE))
            assertTrue("$w x $h: $size", size.dpi < 300)
            assertTrue("$w x $h: $size", size.width.toLong() * size.height <= budget)
            assertTrue("$w x $h: ${size.dpi + 1} dpi would fit too", next.width.toLong() * next.height > budget)
        }
    }

    @Test
    fun `a page without area has no size`() {
        assertNull(RenderSize.of(0, 842, 300, 12_000_000))
        assertNull(RenderSize.of(595, -1, 300, 12_000_000))
        assertNull(RenderSize.of(595, 842, 0, 12_000_000))
    }
}
