// File: pdf/src/androidTest/kotlin/io/github/skm0078/audora/pdf/pdfium/ScannedPdf.kt
// Purpose: Writes a long image-only PDF (a "scanned book") at test time, so the large-book tests commit no large file.

package io.github.skm0078.audora.pdf.pdfium

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/**
 * Every page is one 1-bit picture of printed lines (no text layer at all), drawn at [ScannedPdf.write]'s dpi on a
 * US Letter page, with its own page number, so PDFium decodes a distinct image per page as it would for a scanned
 * book. Flate-compressed bilevel images keep 700 pages to a few megabytes.
 */
object ScannedPdf {
    fun write(file: File, pages: Int, dpi: Int = DEFAULT_DPI) {
        val width = PAGE_WIDTH_PT * dpi / POINTS_PER_INCH
        val height = PAGE_HEIGHT_PT * dpi / POINTS_PER_INCH
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = dpi * TEXT_SIZE_INCH
        }
        val objects = ArrayList<ByteArray>()
        objects += latin("<< /Type /Catalog /Pages 2 0 R >>")
        val kids = (0 until pages).joinToString(" ") { "${FIRST_PAGE_OBJECT + it * OBJECTS_PER_PAGE} 0 R" }
        objects += latin("<< /Type /Pages /Kids [$kids] /Count $pages >>")
        for (page in 0 until pages) {
            val content = FIRST_PAGE_OBJECT + page * OBJECTS_PER_PAGE + 1
            val image = content + 1
            objects += latin(
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $PAGE_WIDTH_PT $PAGE_HEIGHT_PT] " +
                    "/Resources << /XObject << /Im0 $image 0 R >> >> /Contents $content 0 R >>",
            )
            val draw = "q $PAGE_WIDTH_PT 0 0 $PAGE_HEIGHT_PT 0 0 cm /Im0 Do Q"
            objects += latin("<< /Length ${draw.length} >>\nstream\n$draw\nendstream")
            drawPage(bitmap, paint, page, dpi)
            val bits = deflate(bilevel(bitmap))
            objects += latin(
                "<< /Type /XObject /Subtype /Image /Width $width /Height $height /ColorSpace /DeviceGray " +
                    "/BitsPerComponent 1 /Filter /FlateDecode /Length ${bits.size} >>\nstream\n",
            ) + bits + latin("\nendstream")
        }
        bitmap.recycle()
        writeObjects(file, objects)
    }

    private fun drawPage(bitmap: Bitmap, paint: Paint, page: Int, dpi: Int) {
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val margin = dpi.toFloat()
        val lineHeight = paint.textSize * LINE_SPACING
        var y = margin
        var line = 1
        while (y < bitmap.height - margin) {
            canvas.drawText("Page ${page + 1}, line $line of a scanned book about nothing much.", margin, y, paint)
            y += lineHeight
            line++
        }
    }

    /** Rows of 1-bit pixels, most significant bit first, 1 = white (DeviceGray), each row padded to a byte. */
    private fun bilevel(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val rowBytes = (width + BITS_PER_BYTE - 1) / BITS_PER_BYTE
        val out = ByteArray(rowBytes * bitmap.height)
        val row = IntArray(width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, width, 0, y, width, 1)
            for (x in 0 until width) {
                if (Color.red(row[x]) >= INK_BELOW) {
                    val index = y * rowBytes + x / BITS_PER_BYTE
                    out[index] = (out[index].toInt() or (HIGH_BIT ushr (x % BITS_PER_BYTE))).toByte()
                }
            }
        }
        return out
    }

    private fun deflate(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(bytes.size / COMPRESSION_GUESS)
        DeflaterOutputStream(out, Deflater(Deflater.BEST_SPEED)).use { it.write(bytes) }
        return out.toByteArray()
    }

    private fun writeObjects(file: File, objects: List<ByteArray>) {
        file.outputStream().buffered().use { out ->
            var offset = 0L
            val offsets = LongArray(objects.size)
            fun emit(bytes: ByteArray) {
                out.write(bytes)
                offset += bytes.size
            }
            emit(latin("%PDF-1.4\n"))
            objects.forEachIndexed { i, body ->
                offsets[i] = offset
                emit(latin("${i + 1} 0 obj\n"))
                emit(body)
                emit(latin("\nendobj\n"))
            }
            val xref = offset
            emit(latin("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n"))
            offsets.forEach { emit(latin("%010d 00000 n \n".format(it))) }
            emit(latin("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n"))
        }
    }

    private fun latin(text: String): ByteArray = text.toByteArray(Charsets.ISO_8859_1)

    private const val DEFAULT_DPI = 150
    private const val PAGE_WIDTH_PT = 612
    private const val PAGE_HEIGHT_PT = 792
    private const val POINTS_PER_INCH = 72
    private const val TEXT_SIZE_INCH = 0.15f
    private const val LINE_SPACING = 1.6f
    private const val FIRST_PAGE_OBJECT = 3
    private const val OBJECTS_PER_PAGE = 3
    private const val BITS_PER_BYTE = 8
    private const val HIGH_BIT = 0x80
    private const val INK_BELOW = 128
    private const val COMPRESSION_GUESS = 20
}
