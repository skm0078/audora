// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/photo/ImagePdf.kt
// Purpose: Wraps one JPEG in a one-page PDF with no text layer, so the scanned-page pipeline reads it (plan item 62).

package io.github.skm0078.audora.playback.photo

import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/**
 * The smallest valid PDF around a JPEG: catalog, pages, one page and the image as a DCT stream, so the photo is
 * stored as taken (no re-compression) and PDFium draws it like any scanned page. Pure Kotlin, tested on the JVM.
 */
object ImagePdf {
    /**
     * [jpeg] of [widthPx] × [heightPx] pixels on a page whose long side is [pageLongSidePt] points; the short side
     * keeps the photo's proportions.
     */
    fun write(jpeg: ByteArray, widthPx: Int, heightPx: Int, pageLongSidePt: Int): ByteArray {
        require(widthPx > 0 && heightPx > 0) { "image size must be positive, was ${widthPx}x$heightPx" }
        val scale = pageLongSidePt.toDouble() / maxOf(widthPx, heightPx)
        val pageW = (widthPx * scale).roundToInt().coerceAtLeast(1)
        val pageH = (heightPx * scale).roundToInt().coerceAtLeast(1)
        val drawing = "q $pageW 0 0 $pageH 0 0 cm /Im0 Do Q".toByteArray(Charsets.US_ASCII)
        val out = ByteArrayOutputStream(jpeg.size + OVERHEAD_BYTES)
        val offsets = ArrayList<Int>()
        fun obj(body: String, stream: ByteArray? = null) {
            offsets += out.size()
            out.ascii("${offsets.size} 0 obj\n$body\n")
            if (stream != null) {
                out.ascii("stream\n")
                out.write(stream)
                out.ascii("\nendstream\n")
            }
            out.ascii("endobj\n")
        }
        out.ascii("%PDF-1.4\n")
        // A binary comment line, so tools treat the file as binary (PDF 1.4, 7.5.2).
        out.write(BINARY_MARKER)
        obj("<< /Type /Catalog /Pages 2 0 R >>")
        obj("<< /Type /Pages /Kids [3 0 R] /Count 1 >>")
        obj(
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $pageW $pageH] " +
                "/Resources << /XObject << /Im0 4 0 R >> >> /Contents 5 0 R >>",
        )
        obj(
            "<< /Type /XObject /Subtype /Image /Width $widthPx /Height $heightPx /ColorSpace /DeviceRGB " +
                "/BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.size} >>",
            jpeg,
        )
        obj("<< /Length ${drawing.size} >>", drawing)
        val xref = out.size()
        out.ascii("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { out.ascii("%010d 00000 n \n".format(it)) }
        out.ascii("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.ascii(text: String) = write(text.toByteArray(Charsets.US_ASCII))

    private const val OVERHEAD_BYTES = 1_024
    private val BINARY_MARKER = "%\u00e2\u00e3\u00cf\u00d3\n".toByteArray(Charsets.ISO_8859_1)
}
