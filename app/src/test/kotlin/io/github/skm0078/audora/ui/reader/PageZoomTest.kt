// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/PageZoomTest.kt
// Purpose: Pinch zoom (plan item 19): the point under the fingers stays put, the zoom stays in range, and the page
//          is re-rendered in steps.

package io.github.skm0078.audora.ui.reader

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageZoomTest {
    @Test
    fun `the point under the fingers stays under them`() {
        val cases = listOf(
            Triple(Offset(0f, 0f), Offset(100f, 200f), 2f) to Offset(100f, 200f),
            Triple(Offset(50f, 300f), Offset(100f, 100f), 2f) to Offset(200f, 700f),
            Triple(Offset(200f, 700f), Offset(100f, 100f), 0.5f) to Offset(50f, 300f),
            Triple(Offset(0f, 0f), Offset(100f, 100f), 0.5f) to Offset(0f, 0f),
        )
        cases.forEach { (input, expected) ->
            val (scrolled, centroid, ratio) = input
            assertEquals("$input", expected, zoomedScroll(scrolled, centroid, ratio))
        }
    }

    @Test
    fun `zoom stays between fit width and four times`() {
        val zoom = PageZoom()
        zoom.zoomBy(10f, Offset.Zero, Offset.Zero)
        assertEquals(PageZoom.MAX_SCALE, zoom.scale)
        zoom.zoomBy(0.01f, Offset.Zero, Offset.Zero)
        assertEquals(PageZoom.MIN_SCALE, zoom.scale)
        zoom.consumePending()
        zoom.zoomBy(0.5f, Offset.Zero, Offset.Zero)
        assertNull("already at fit width, nothing to scroll", zoom.consumePending())
    }

    @Test
    fun `the page is re-rendered in half-width steps`() {
        val cases = mapOf(1f to 1000, 1.2f to 1000, 1.3f to 1500, 2f to 2000, 3.9f to 4000)
        cases.forEach { (scale, width) -> assertEquals("at $scale", width, renderWidthPx(1000, scale)) }
    }
}
