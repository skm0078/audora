// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/photo/PhotoBookFile.kt
// Purpose: Writes photos, upright and scaled, as the pages of a PDF with no text layer (ADR-043, ADR-066); comic
//          pages, a damaged one left blank, and scanned DjVu pages too (ADR 2026-10-26-more-ebook-formats,
//          ADR 2026-10-27-djvu-books).

package io.github.skm0078.audora.playback.photo

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import io.github.skm0078.audora.core.config.PhotoConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
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
    fun write(pages: Int, sourceAt: (Int) -> ImageDecoder.Source, target: File) =
        writePictures(pages, { decodeUpright(sourceAt(it)) }, target)

    /**
     * [pages] pictures as [write] writes them, except that a page whose picture cannot be had (reading or decoding it
     * throws IOException, or it would not fit in memory) becomes a blank page the size of the page before it: one
     * damaged page of a comic no longer loses the rest, and the page numbers still match the archive's. Returns how
     * many pages are blank; throws IOException when none could be drawn, so a comic of broken pictures is still
     * reported as unreadable.
     */
    fun writeKeepingBadPages(pages: Int, sourceAt: (Int) -> ImageDecoder.Source, target: File): Int {
        var blank = 0
        var lastWidth = BLANK_WIDTH_PX
        var lastHeight = BLANK_HEIGHT_PX
        writePictures(pages, { page ->
            val picture = pictureOrNull(page) { decodeUpright(sourceAt(page)) }
            if (picture == null) {
                blank++
                blankPicture(lastWidth, lastHeight)
            } else {
                picture.also {
                    lastWidth = it.width
                    lastHeight = it.height
                }
            }
        }, target)
        if (blank == pages) throw IOException("none of the $pages pictures could be drawn")
        if (blank > 0) AudoraLog.w(TAG, "Blank pages for pictures that could not be drawn: $blank of $pages")
        return blank
    }

    /** The picture [draw] makes, or null (logged by page and exception type) when it cannot be read or held. */
    private fun pictureOrNull(page: Int, draw: () -> Bitmap): Bitmap? = try {
        draw()
    } catch (e: IOException) {
        AudoraLog.w(TAG, "Page $page could not be drawn: ${e.javaClass.simpleName}")
        null
    } catch (e: OutOfMemoryError) {
        // One picture too big for memory: the page is left blank, the rest of the book is still made.
        AudoraLog.w(TAG, "Page $page did not fit in memory: ${e.javaClass.simpleName}")
        null
    }

    /** A white page; RGB_565 holds it in half the memory, and it is JPEG-encoded as any other. */
    private fun blankPicture(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565).apply { eraseColor(Color.WHITE) }

    /**
     * [pages] pictures already drawn, each by [pictureAt] only when its page is written (a scanned DjVu's page); the
     * picture is recycled once written.
     */
    fun writePictures(pages: Int, pictureAt: (Int) -> Bitmap, target: File) {
        FileOutputStream(target).use { file ->
            val out = BufferedOutputStream(file)
            val writer = ImagePdfWriter(out, pages, config.pageLongSidePt)
            for (page in 0 until pages) writePage(writer, pictureAt(page))
            writer.finish()
            out.flush()
            file.fd.sync()
        }
        AudoraLog.i(TAG, "Photo book made, pages=$pages, bytes=${target.length()}")
    }

    private fun writePage(writer: ImagePdfWriter, bitmap: Bitmap) {
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

        // A blank page before any picture has been drawn: A4's shape, small, as it holds nothing.
        const val BLANK_WIDTH_PX = 707
        const val BLANK_HEIGHT_PX = 1_000
    }
}
