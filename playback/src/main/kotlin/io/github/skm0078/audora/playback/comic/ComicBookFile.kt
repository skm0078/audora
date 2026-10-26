// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/comic/ComicBookFile.kt
// Purpose: Copies a comic archive (.cbz) in, lists its pages and writes them as a photo book's PDF
//          (ADR 2026-10-26-more-ebook-formats).

package io.github.skm0078.audora.playback.comic

import android.content.ContentResolver
import android.graphics.ImageDecoder
import android.net.Uri
import io.github.skm0078.audora.core.config.PhotoConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.playback.photo.PhotoBookFile
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.zip.ZipFile

/** The archive is over `PhotoConfig.maxComicBytes`; nothing was kept. */
class ComicTooLargeException : IOException("comic archive over the size limit")

/**
 * A provider's stream cannot seek and the pages are not stored in reading order, so the archive is copied into
 * [folder] first and read with [ZipFile]. Each page is decoded from its own bytes, one at a time.
 */
internal class ComicBookFile(
    private val resolver: ContentResolver,
    private val folder: File,
    private val config: PhotoConfig,
) {
    private val pdf = PhotoBookFile(config)

    /** A copy of [archive] the caller deletes; throws [ComicTooLargeException] past the size limit. */
    fun copyIn(archive: Uri): File {
        folder.mkdirs()
        val copy = File.createTempFile(COPY_PREFIX, COPY_SUFFIX, folder)
        try {
            val input = resolver.openInputStream(archive) ?: throw FileNotFoundException("provider returned no stream")
            input.use { from -> copy.outputStream().use { to -> copyWithinLimit(from, to) } }
            return copy
        } catch (e: IOException) {
            copy.delete()
            throw e
        }
    }

    private fun copyWithinLimit(from: InputStream, to: OutputStream) {
        val buffer = ByteArray(BUFFER_BYTES)
        var total = 0L
        var read = from.read(buffer)
        while (read >= 0) {
            total += read
            if (total > config.maxComicBytes) throw ComicTooLargeException()
            to.write(buffer, 0, read)
            read = from.read(buffer)
        }
    }

    /** The pages of the copied archive, in reading order ([ComicPages]). */
    fun pages(copy: File): List<String> = ZipFile(copy).use { zip ->
        ComicPages.order(zip.entries().asSequence().filter { !it.isDirectory }.map { it.name }.toList())
    }

    /** [pages] of [copy] as the pages of a PDF in [target], as gallery pictures are. */
    fun write(copy: File, pages: List<String>, target: File) {
        ZipFile(copy).use { zip ->
            pdf.write(pages.size, { index ->
                val entry = zip.getEntry(pages[index]) ?: throw IOException("page $index is gone")
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                ImageDecoder.createSource(ByteBuffer.wrap(bytes))
            }, target)
        }
        AudoraLog.i(TAG, "Comic book made, pages=${pages.size}")
    }

    private companion object {
        const val TAG = "Audora.Comic"
        const val COPY_PREFIX = "comic-"
        const val COPY_SUFFIX = ".cbz"
        const val BUFFER_BYTES = 64 * 1024
    }
}
