// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/photo/PhotoBookFile.kt
// Purpose: Writes photos, upright and scaled, as the pages of a PDF with no text layer (ADR-043, ADR-066); comic
//          pages too (ADR 2026-10-26-more-ebook-formats).

package io.github.skm0078.audora.playback.photo

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import io.github.skm0078.audora.core.config.PhotoConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * The file side of [PhotoBooks] and comic books: decoding, scaling and JPEG encoding of each picture, and the PDF
 * around them.
 */
internal class PhotoBookFile(private val config: PhotoConfig) {
    /**
     * Each picture, upright and scaled, as a page of a PDF in [target]: one picture in memory at a time, and the
     * file flushed to disk before it is moved, so a crash never leaves half a book that looks whole.
     */
    fun write(sources: List<ImageDecoder.Source>, target: File) = write(sources.size, sources::get, target)

    /** [pages] pictures, each made by [sourceAt] only when its page is written (a comic's page from its archive). */
    fun write(pages: Int, sourceAt: (Int) -> ImageDecoder.Source, target: File) {
        FileOutputStream(target).use { file ->
            val out = BufferedOutputStream(file)
            val writer = ImagePdfWriter(out, pages, config.pageLongSidePt)
            for (page in 0 until pages) writePage(writer, sourceAt(page))
            writer.finish()
            out.flush()
            file.fd.sync()
        }
        AudoraLog.i(TAG, "Photo book made, pages=$pages, bytes=${target.length()}")
    }

    private fun writePage(writer: ImagePdfWriter, source: ImageDecoder.Source) {
        val bitmap = decodeUpright(source)
        try {
            val jpeg = ByteArrayOutputStream().use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, config.jpegQuality, out)) { "JPEG encoder failed" }
                out.toByteArray()
            }
            writer.addPage(jpeg, bitmap.width, bitmap.height)
        } finally {
            bitmap.recycle()
        }
    }

    /** ImageDecoder applies the EXIF turn and caps the long side while decoding: a 50 MP photo never loads whole. */
    private fun decodeUpright(source: ImageDecoder.Source): Bitmap =
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longSide = maxOf(info.size.width, info.size.height)
            if (longSide > config.maxLongSidePx) {
                val scale = config.maxLongSidePx.toDouble() / longSide
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

    private companion object {
        const val TAG = "Audora.Photo"
    }
}
