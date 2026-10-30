// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/comic/ComicBookFile.kt
// Purpose: Copies a comic archive (.cbz) in, lists its pages and writes them as a photo book's PDF
//          (ADR 2026-10-26-more-ebook-formats).

package io.github.skm0078.audora.playback.comic

import android.content.ContentResolver
import android.graphics.ImageDecoder
import android.net.Uri
import io.github.skm0078.audora.core.config.PhotoConfig
import io.github.skm0078.audora.core.files.readUpTo
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.playback.photo.PhotoBookFile
import io.github.skm0078.audora.playback.text.ZipNames
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.zip.ZipFile

/** A comic's page names in reading order, and the [charset] its zip's names were read with. */
internal data class ComicArchivePages(val names: List<String>, val charset: Charset)

/** Page [index] unpacks to more than `PhotoConfig.maxComicPageBytes`; it is left blank. */
class ComicPageTooLargeException(index: Int) : IOException("comic page $index over the page size limit")

/** The archive is over `PhotoConfig.maxComicBytes`; nothing was kept. */
class ComicTooLargeException : IOException("comic archive over the size limit")

/**
 * A provider's stream cannot seek and the pages are not stored in reading order, so the archive is copied into
 * [folder] first and read with [ZipFile]. Each page is decoded from its own bytes, one at a time; a page that cannot
 * be read or decoded is left blank.
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

    /**
     * The pages of the copied archive, in reading order ([ComicPages]), named in the charset that read them: many
     * comics are zipped with names that are not UTF-8 ([ZipNames]).
     */
    fun pages(copy: File): ComicArchivePages = ZipNames.file(copy) { zip, charset ->
        val names = zip.entries().asSequence().filter { !it.isDirectory }.map { it.name }.toList()
        ComicArchivePages(ComicPages.order(names), charset)
    }

    /**
     * [pages] of [copy] as the pages of a PDF in [target], as gallery pictures are; returns how many were left blank
     * because they could not be read or decoded.
     */
    fun write(copy: File, pages: ComicArchivePages, target: File): Int = ZipFile(copy, pages.charset).use { zip ->
        val sourceAt = { index: Int ->
            ImageDecoder.createSource(ByteBuffer.wrap(pageBytes(zip, pages.names[index], index)))
        }
        val blank = pdf.writeKeepingBadPages(pages.names.size, sourceAt, target)
        AudoraLog.i(TAG, "Comic book made, pages=${pages.names.size}, blank=$blank")
        blank
    }

    /**
     * A page's picture, still encoded. Read whole, as ImageDecoder needs, but never past
     * [PhotoConfig.maxComicPageBytes]: the zip's claimed size is checked first and the bytes actually read after, as a
     * damaged or hostile zip can claim less than it unpacks to.
     */
    private fun pageBytes(zip: ZipFile, name: String, index: Int): ByteArray {
        val entry = zip.getEntry(name) ?: throw IOException("page $index is gone")
        val limit = config.maxComicPageBytes
        // The declared size refuses most oversize pages unread; the bounded read catches a size that lied.
        val bytes = if (entry.size > limit) null else zip.getInputStream(entry).use { it.readUpTo(limit + 1) }
        if (bytes == null || bytes.size > limit) throw ComicPageTooLargeException(index)
        return bytes
    }

    private companion object {
        const val TAG = "Audora.Comic"
        const val COPY_PREFIX = "comic-"
        const val COPY_SUFFIX = ".cbz"
        const val BUFFER_BYTES = 64 * 1024
    }
}
