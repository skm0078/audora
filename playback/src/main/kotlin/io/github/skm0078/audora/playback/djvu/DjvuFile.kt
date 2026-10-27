// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/djvu/DjvuFile.kt
// Purpose: An open DjVu book through DjVuLibre (libaudora_djvu.so): its pages' hidden text, outline and metadata, and
//          its pages as pictures (ADR 2026-10-27-djvu-books).

package io.github.skm0078.audora.playback.djvu

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToInt

/** DjVuLibre could not read the file: not a DjVu, damaged, or an indirect one whose pages are separate files. */
class DjvuException(message: String) : IOException(message)

/** libaudora_djvu.so's calls (djvu_jni.cpp). A handle is used from one thread at a time. */
internal object DjvuNative {
    init {
        System.loadLibrary("audora_djvu")
    }

    /** Throws [DjvuException] when the file is not a readable, bundled DjVu. */
    external fun open(path: String): Long

    external fun close(handle: Long)

    external fun pageCount(handle: Long): Int

    external fun pageText(handle: Long, page: Int): ByteArray

    external fun outline(handle: Long): ByteArray

    external fun annotations(handle: Long): ByteArray

    /** Width and height in pixels; null when the page cannot be decoded. */
    external fun pageSize(handle: Long, page: Int): IntArray?

    /** Draws the page into [bitmap] (ARGB_8888, any size); false when the page cannot be decoded. */
    external fun render(handle: Long, page: Int, bitmap: Bitmap): Boolean
}

/**
 * A DjVu read where it lies: DjVuLibre opens the provider's file through `/proc/self/fd`, reading only what it needs
 * (a 300-page scan's text layer without its pictures). A provider whose descriptor has no size (a pipe), or whose
 * file cannot be opened again by that path, gets its file copied to [copyFolder] first, up to [maxCopyBytes]. Close it
 * to release the library's memory and the copy.
 */
class DjvuFile private constructor(
    private val handle: Long,
    private val descriptor: ParcelFileDescriptor?,
    private val copy: File?,
) : DjvuPages,
    Closeable {
    private var closed = false

    override val pageCount: Int = DjvuNative.pageCount(handle)

    @Synchronized
    override fun pageText(page: Int): String = String(DjvuNative.pageText(live(), page), Charsets.UTF_8)

    @Synchronized
    override fun outline(): String = String(DjvuNative.outline(live()), Charsets.UTF_8)

    @Synchronized
    override fun annotations(): String = String(DjvuNative.annotations(live()), Charsets.UTF_8)

    /**
     * Page [page] as a picture whose long side is at most [maxLongSidePx] (never larger than the page's own); throws
     * [IOException] when it cannot be decoded. The caller recycles it.
     */
    @Synchronized
    fun pagePicture(page: Int, maxLongSidePx: Int): Bitmap {
        val size = DjvuNative.pageSize(live(), page) ?: throw IOException("DjVu page $page has no size")
        val scale = minOf(1.0, maxLongSidePx.toDouble() / maxOf(size[0], size[1]))
        val width = (size[0] * scale).roundToInt().coerceAtLeast(1)
        val height = (size[1] * scale).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (!DjvuNative.render(handle, page, bitmap)) {
            bitmap.recycle()
            throw IOException("DjVu page $page could not be drawn")
        }
        return bitmap
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        DjvuNative.close(handle)
        descriptor?.close()
        if (copy != null && !copy.delete()) AudoraLog.w(TAG, "A DjVu copy could not be deleted")
    }

    private fun live(): Long {
        check(!closed) { "DjVu file used after close" }
        return handle
    }

    companion object {
        private const val TAG = "Audora.Djvu"
        private const val BUFFER_BYTES = 64 * 1024

        /** The cache folder a DjVu from a pipe-only provider is copied into while it is open. */
        private const val COPY_FOLDER = "djvu-copies"

        /** [open] with the app's cache folder for any copy. */
        fun open(context: Context, uri: Uri, maxCopyBytes: Long): DjvuFile =
            open(context.contentResolver, uri, File(context.cacheDir, COPY_FOLDER), maxCopyBytes)

        /**
         * Opens [uri]; throws [DjvuException] (damaged or indirect), [IOException] (the provider failed) or
         * [SecurityException].
         */
        fun open(resolver: ContentResolver, uri: Uri, copyFolder: File, maxCopyBytes: Long): DjvuFile {
            val descriptor = resolver.openFileDescriptor(uri, "r") ?: throw FileNotFoundException("no descriptor")
            if (descriptor.statSize > 0) {
                try {
                    return openOrRelease("/proc/self/fd/${descriptor.fd}", descriptor, copy = null)
                } catch (e: DjvuException) {
                    // DjVuLibre opens the path again, and a provider's file (Downloads through MediaStore, a
                    // storage FUSE path) may refuse that to this app: read through a copy instead.
                    AudoraLog.w(TAG, "DjVu not opened in place, copying it: ${e.message}")
                }
            } else {
                descriptor.close()
            }
            val copy = copyIn(resolver, uri, copyFolder, maxCopyBytes)
            return openOrRelease(copy.path, descriptor = null, copy = copy)
        }

        /** Opens [path]; on failure closes [descriptor] and deletes [copy] before rethrowing. */
        private fun openOrRelease(path: String, descriptor: ParcelFileDescriptor?, copy: File?): DjvuFile = try {
            DjvuFile(DjvuNative.open(path), descriptor, copy)
        } catch (e: IOException) {
            descriptor?.close()
            copy?.delete()
            throw e
        }

        private fun copyIn(resolver: ContentResolver, uri: Uri, folder: File, maxBytes: Long): File {
            folder.mkdirs()
            val copy = File.createTempFile("djvu-", ".djvu", folder)
            try {
                val input = resolver.openInputStream(uri) ?: throw FileNotFoundException("no stream")
                input.use { from -> copy.outputStream().use { to -> copyLimited(from, to, maxBytes) } }
                return copy
            } catch (e: IOException) {
                copy.delete()
                throw e
            }
        }

        private fun copyLimited(from: InputStream, to: OutputStream, maxBytes: Long) {
            val buffer = ByteArray(BUFFER_BYTES)
            var total = 0L
            var read = from.read(buffer)
            while (read >= 0) {
                total += read
                if (total > maxBytes) throw IOException("DjVu over the size limit")
                to.write(buffer, 0, read)
                read = from.read(buffer)
            }
        }
    }
}
