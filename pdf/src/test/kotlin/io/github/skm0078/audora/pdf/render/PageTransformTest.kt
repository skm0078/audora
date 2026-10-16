// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/render/PageTransformTest.kt
// Purpose: Glyph boxes land on the right spot of the drawn page for upright, rotated and offset pages, and back.

package io.github.skm0078.audora.pdf.render

import io.github.skm0078.audora.core.model.TextBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PageTransformTest {
    /** A line of text near the top-left of a US Letter page. */
    private val box = TextBox(left = 72f, top = 700f, right = 540f, bottom = 688f)

    @Test
    fun `upright page flips y so the top of the page is the top of the image`() {
        val view = PageTransform.upright(W, H).toView(box)
        assertRect(ViewRect(72f / W, 1f - 700f / H, 540f / W, 1f - 688f / H), view)
    }

    @Test
    fun `table of PDFium samples for rotated and offset pages`() {
        data class Case(val name: String, val transform: PageTransform, val expected: ViewRect)
        val cases = listOf(
            // /Rotate 90 (clockwise): the page's bottom-left corner shows at the top-left; displayed H wide.
            Case(
                "rotate 90",
                sampled { x, y -> ViewPoint(y / H, x / W) },
                ViewRect(688f / H, 72f / W, 700f / H, 540f / W),
            ),
            Case(
                "rotate 180",
                sampled { x, y -> ViewPoint(1f - x / W, y / H) },
                ViewRect(1f - 540f / W, 688f / H, 1f - 72f / W, 700f / H),
            ),
            Case(
                "rotate 270",
                sampled { x, y -> ViewPoint(1f - y / H, 1f - x / W) },
                ViewRect(1f - 700f / H, 1f - 540f / W, 1f - 688f / H, 1f - 72f / W),
            ),
            // A crop box starting at (36, 36): PDFium shifts the origin, the boxes stay in page space.
            Case(
                "crop box offset",
                sampled { x, y -> ViewPoint((x - 36f) / (W - 72f), 1f - (y - 36f) / (H - 72f)) },
                ViewRect(36f / (W - 72f), 1f - 664f / (H - 72f), 504f / (W - 72f), 1f - 652f / (H - 72f)),
            ),
        )
        cases.forEach { case -> assertRect(case.expected, case.transform.toView(box), case.name) }
    }

    @Test
    fun `a tap maps back to the page point under it`() {
        val transforms = listOf(
            PageTransform.upright(W, H),
            sampled { x, y -> ViewPoint(y / H, x / W) },
            sampled { x, y -> ViewPoint(1f - x / W, y / H) },
            sampled { x, y -> ViewPoint((x - 36f) / (W - 72f), 1f - (y - 36f) / (H - 72f)) },
        )
        transforms.forEach { transform ->
            val point = transform.toPage(transform.toView(123f, 456f))
            assertEquals(123f, point.x, POINT_EPSILON)
            assertEquals(456f, point.y, POINT_EPSILON)
        }
    }

    @Test
    fun `a transform that collapses the page is rejected`() {
        val flat = ViewPoint(0.5f, 0.5f)
        assertThrows(IllegalArgumentException::class.java) {
            PageTransform.fromSamples(flat, flat, flat, STEP)
        }
        assertThrows(IllegalArgumentException::class.java) { PageTransform.upright(0f, H) }
    }

    /** What the renderer does: read the map off three points mapped by [map] (PDFium's FPDF_PageToDevice). */
    private fun sampled(map: (Float, Float) -> ViewPoint) =
        PageTransform.fromSamples(map(0f, 0f), map(STEP, 0f), map(0f, STEP), STEP)

    private fun assertRect(expected: ViewRect, actual: ViewRect, name: String = "") {
        assertEquals("$name left", expected.left, actual.left, EPSILON)
        assertEquals("$name top", expected.top, actual.top, EPSILON)
        assertEquals("$name right", expected.right, actual.right, EPSILON)
        assertEquals("$name bottom", expected.bottom, actual.bottom, EPSILON)
    }

    private companion object {
        const val W = 612f
        const val H = 792f
        const val STEP = 100f
        const val EPSILON = 1e-5f
        const val POINT_EPSILON = 1e-2f
    }
}
