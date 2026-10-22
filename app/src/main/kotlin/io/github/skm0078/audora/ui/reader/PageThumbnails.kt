// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/PageThumbnails.kt
// Purpose: Small page pictures for the page scrubber (plan item 17): rendered once from the open PDF, then kept in
//          memory and in cacheDir so dragging back over a page is instant.

package io.github.skm0078.audora.ui.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One book's thumbnails at [widthPx]. Files live in [directory] (under cacheDir, so Android may clear them when space
 * is short; they are re-rendered then). A failed render or file is logged with the book and page and shows no
 * picture, never an error: the scrubber still shows the page number.
 *
 * @param render draws a page of the open book at a width; null when it cannot (closed, failed).
 * @param logLabel the book's short id for log lines, never its title.
 */
class PageThumbnails(
    private val directory: File,
    private val widthPx: Int,
    private val logLabel: String,
    private val render: suspend (page: Int, widthPx: Int) -> PageImage?,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val memory = LruCache<Int, ImageBitmap>(MEMORY_PAGES)

    suspend fun get(page: Int): ImageBitmap? {
        memory.get(page)?.let { return it }
        val image = withContext(io) { read(page) } ?: render(page, widthPx)?.imageBitmap()?.also { store(page, it) }
        image?.let { memory.put(page, it) }
        return image
    }

    private fun fileFor(page: Int) = File(directory, "$page-$widthPx.webp")

    private fun read(page: Int): ImageBitmap? {
        val file = fileFor(page)
        if (!file.isFile) return null
        val bitmap = BitmapFactory.decodeFile(file.path)
        if (bitmap == null) {
            AudoraLog.w(TAG, "Unreadable thumbnail dropped, book=$logLabel page=$page")
            file.delete()
        }
        return bitmap?.asImageBitmap()
    }

    private suspend fun store(page: Int, image: ImageBitmap) = withContext(io) {
        val file = fileFor(page)
        try {
            directory.mkdirs()
            val temp = File(directory, "${file.name}.part")
            temp.outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.WEBP_LOSSY, QUALITY, it) }
            if (!temp.renameTo(file)) throw IOException("rename failed")
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Thumbnail not cached, book=$logLabel page=$page", e)
        }
    }

    private companion object {
        const val TAG = "Audora.Thumbnails"

        /** About two screens of dragging back and forth; a thumbnail is a few tens of KB. */
        const val MEMORY_PAGES = 48

        /** WebP quality: page text stays legible at thumbnail size. */
        const val QUALITY = 80
    }
}
