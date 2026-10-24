// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/CoverBitmaps.kt
// Purpose: The covers' files and pictures (plan item 21, ADR-068): where each cover is kept, decoding a picture small,
//          and writing WEBP so a cover is never half written.

package io.github.skm0078.audora.ui.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import io.github.skm0078.audora.core.config.CoverConfig
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File
import java.io.IOException
import java.io.InputStream

/** A picture to keep as a cover, opened twice (once for its size, once to decode it small); null if unreadable. */
fun interface CoverImage {
    fun open(): InputStream?

    companion object {
        fun of(bytes: ByteArray) = CoverImage { bytes.inputStream() }
    }
}

/**
 * `covers/<id>.webp` for a drawn first page, `covers/<id>@p<n>.webp` for another page, `covers-chosen/<id>.webp` for
 * a kept picture. A file's book id is its name up to the page mark.
 */
internal class CoverFolders(filesDir: File) {
    val drawn = File(filesDir, DRAWN)
    val chosen = File(filesDir, CHOSEN)

    fun drawnFile(id: BookId, page: Int) =
        File(drawn, if (page == FIRST_PAGE) "${id.value}$EXTENSION" else "${id.value}$PAGE_MARK$page$EXTENSION")

    fun chosenFile(id: BookId) = File(chosen, "${id.value}$EXTENSION")

    fun bookIdOf(fileName: String) =
        fileName.substringBefore(PAGE_MARK).removeSuffix(PARTIAL_SUFFIX).removeSuffix(EXTENSION)

    companion object {
        const val FIRST_PAGE = 0
        const val PARTIAL_SUFFIX = ".part"
        private const val DRAWN = "covers"
        private const val CHOSEN = "covers-chosen"
        private const val PAGE_MARK = "@p"
        private const val EXTENSION = ".webp"
    }
}

/** Decodes and writes cover pictures at [config]'s size. Blocking; the caller is on the covers' IO dispatcher. */
internal class CoverBitmaps(private val config: CoverConfig) {
    fun decode(file: File): Bitmap? = if (file.isFile) BitmapFactory.decodeFile(file.path) else null

    /** A drawn cover kept before; one that cannot be read is deleted, so it is drawn again. */
    fun decodeCached(file: File): Bitmap? {
        if (!file.exists()) return null
        return decode(file) ?: run {
            AudoraLog.w(TAG, "Cached cover unreadable, drawing it again")
            file.delete()
            null
        }
    }

    /** At most twice the cover's width while decoding (a power-of-two step), then scaled to it exactly. */
    fun decodeSmall(image: CoverImage): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        image.open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val decoded = if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth) }
            image.open()?.use { BitmapFactory.decodeStream(it, null, options) }
        } else {
            null
        }
        return decoded?.let(::scaled)
    }

    /** Written beside the final name, then renamed, so a cover is never half written; a failed save costs a redraw. */
    fun writeWebp(file: File, cover: Bitmap): Boolean {
        val partial = File(file.parentFile, "${file.name}${CoverFolders.PARTIAL_SUFFIX}")
        return try {
            file.parentFile?.mkdirs()
            partial.outputStream().use { cover.compress(Bitmap.CompressFormat.WEBP_LOSSY, config.webpQuality, it) }
            if (!partial.renameTo(file)) throw IOException("rename failed")
            true
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Could not keep the cover", e)
            partial.delete()
            false
        }
    }

    private fun sampleSize(width: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= config.widthPx * 2) sample *= 2
        return sample
    }

    private fun scaled(decoded: Bitmap): Bitmap {
        val height = (decoded.height.toLong() * config.widthPx / decoded.width).toInt()
            .coerceIn(1, config.maxPixels / config.widthPx)
        return Bitmap.createScaledBitmap(decoded, config.widthPx, height, true).also {
            if (it !== decoded) decoded.recycle()
        }
    }

    private companion object {
        const val TAG = "Audora.Covers"
    }
}

/** PDFium leaves blank paper transparent; a cover is paper, so it gets white behind it. */
internal fun onWhite(page: Bitmap): Bitmap {
    val cover = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
    Canvas(cover).apply {
        drawColor(Color.WHITE)
        drawBitmap(page, 0f, 0f, null)
    }
    page.recycle()
    return cover
}
