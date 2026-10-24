// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/photo/ImagePdf.kt
// Purpose: Wraps JPEGs in a PDF with no text layer, one per page, so the scanned-page pipeline reads them (ADR-043,
//          ADR-066).

package io.github.skm0078.audora.playback.photo

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.math.roundToInt

/**
 * The smallest valid PDF around JPEGs: catalog, pages, and per page a page object, the image as a DCT stream and the
 * drawing, so each photo is stored as taken (no re-compression) and PDFium draws it like any scanned page. Pure
 * Kotlin, tested on the JVM.
 */
object ImagePdf {
    /**
     * [jpeg] of [widthPx] × [heightPx] pixels on a page whose long side is [pageLongSidePt] points; the short side
     * keeps the photo's proportions.
     */
    fun write(jpeg: ByteArray, widthPx: Int, heightPx: Int, pageLongSidePt: Int): ByteArray {
        val out = ByteArrayOutputStream(jpeg.size + ImagePdfWriter.OVERHEAD_BYTES)
        ImagePdfWriter(out, pageCount = 1, pageLongSidePt).apply {
            addPage(jpeg, widthPx, heightPx)
            finish()
        }
        return out.toByteArray()
    }
}

/**
 * Writes a PDF of [pageCount] image pages to [out] one page at a time, so a book of many photos never holds more
 * than one JPEG in memory. Object numbers are fixed in advance (1 catalog, 2 pages, then three per page), so the page
 * list can be written first. Exactly [pageCount] pages must be added before [finish]. [out] is not closed.
 */
class ImagePdfWriter(out: OutputStream, private val pageCount: Int, private val pageLongSidePt: Int) {
    private val out = CountingStream(out)
    private val offsets = ArrayList<Long>()
    private var pages = 0

    init {
        require(pageCount > 0) { "pageCount must be > 0, was $pageCount" }
        require(pageLongSidePt > 0) { "pageLongSidePt must be > 0, was $pageLongSidePt" }
        ascii("%PDF-1.4\n")
        // A binary comment line, so tools treat the file as binary (PDF 1.4, 7.5.2).
        this.out.write(BINARY_MARKER)
        obj("<< /Type /Catalog /Pages 2 0 R >>")
        val kids = (0 until pageCount).joinToString(" ") { "${firstObjectOf(it)} 0 R" }
        obj("<< /Type /Pages /Kids [$kids] /Count $pageCount >>")
    }

    fun addPage(jpeg: ByteArray, widthPx: Int, heightPx: Int) {
        require(widthPx > 0 && heightPx > 0) { "image size must be positive, was ${widthPx}x$heightPx" }
        check(pages < pageCount) { "all $pageCount pages are already written" }
        val scale = pageLongSidePt.toDouble() / maxOf(widthPx, heightPx)
        val pageW = (widthPx * scale).roundToInt().coerceAtLeast(1)
        val pageH = (heightPx * scale).roundToInt().coerceAtLeast(1)
        val first = firstObjectOf(pages)
        val drawing = "q $pageW 0 0 $pageH 0 0 cm /Im0 Do Q".toByteArray(Charsets.US_ASCII)
        obj(
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $pageW $pageH] " +
                "/Resources << /XObject << /Im0 ${first + 1} 0 R >> >> /Contents ${first + 2} 0 R >>",
        )
        obj(
            "<< /Type /XObject /Subtype /Image /Width $widthPx /Height $heightPx /ColorSpace /DeviceRGB " +
                "/BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.size} >>",
            jpeg,
        )
        obj("<< /Length ${drawing.size} >>", drawing)
        pages++
    }

    fun finish() {
        check(pages == pageCount) { "$pages of $pageCount pages written" }
        val xref = out.count
        ascii("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { ascii("%010d 00000 n \n".format(it)) }
        ascii("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        out.flush()
    }

    private fun obj(body: String, stream: ByteArray? = null) {
        offsets += out.count
        ascii("${offsets.size} 0 obj\n$body\n")
        if (stream != null) {
            ascii("stream\n")
            out.write(stream)
            ascii("\nendstream\n")
        }
        ascii("endobj\n")
    }

    private fun ascii(text: String) = out.write(text.toByteArray(Charsets.US_ASCII))

    /** Page [index]'s page object; its image and drawing are the next two. */
    private fun firstObjectOf(index: Int): Int = FIRST_PAGE_OBJECT + index * OBJECTS_PER_PAGE

    private class CountingStream(private val target: OutputStream) : OutputStream() {
        var count = 0L
            private set

        override fun write(b: Int) {
            target.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            target.write(b, off, len)
            count += len
        }

        override fun flush() = target.flush()
    }

    internal companion object {
        const val OVERHEAD_BYTES = 1_024
        private const val FIRST_PAGE_OBJECT = 3
        private const val OBJECTS_PER_PAGE = 3
        private val BINARY_MARKER = "%âãÏÓ\n".toByteArray(Charsets.ISO_8859_1)
    }
}
