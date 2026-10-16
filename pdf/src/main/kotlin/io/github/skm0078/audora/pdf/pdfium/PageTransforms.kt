// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/pdfium/PageTransforms.kt
// Purpose: Reads PDFium's page-to-display map off three sampled points, for the reader and for OCR boxes alike.

package io.github.skm0078.audora.pdf.pdfium

import io.github.skm0078.audora.pdf.render.PageTransform
import io.github.skm0078.audora.pdf.render.ViewPoint
import io.legere.pdfiumandroid.core.unlocked.PdfPageU
import kotlin.math.roundToInt

/** Virtual canvas width for reading the transform: 1 px rounding is 1/10 000 of the page. */
private const val SAMPLE_CANVAS_PX = 10_000

/** Distance between the sampled page points; any non-zero value gives the same map. */
private const val SAMPLE_STEP_POINTS = 100.0

/**
 * PDFium only maps points (FPDF_PageToDevice, integer pixels), so the affine map is read off three page points
 * mapped onto a large virtual canvas, which keeps the rounding below a thousandth of the page. Call under PDFium's
 * lock; null for a page without a size.
 */
internal fun pageTransformOf(page: PdfPageU): PageTransform? {
    // Width and height as displayed: PDFium swaps them for a page with /Rotate 90 or 270.
    val width = page.getPageWidthPoint().toFloat()
    val height = page.getPageHeightPoint().toFloat()
    if (width <= 0f || height <= 0f) return null
    val sizeX = SAMPLE_CANVAS_PX
    val sizeY = (SAMPLE_CANVAS_PX * height / width).roundToInt().coerceAtLeast(1)
    fun sample(x: Double, y: Double): ViewPoint {
        val device = page.mapPageCoordsToDevice(0, 0, sizeX, sizeY, 0, x, y)
        return ViewPoint(device.x.toFloat() / sizeX, device.y.toFloat() / sizeY)
    }
    return PageTransform.fromSamples(
        origin = sample(0.0, 0.0),
        alongX = sample(SAMPLE_STEP_POINTS, 0.0),
        alongY = sample(0.0, SAMPLE_STEP_POINTS),
        step = SAMPLE_STEP_POINTS.toFloat(),
    )
}
