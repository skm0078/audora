// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/CoverBitmaps.kt
// Purpose: The covers' pictures (plan item 21, ADR-068): decoding a picture small, and writing WEBP so a cover is
//          never half written. Where each cover is kept is playback's CoverFolders.

package io.github.skm0078.audora.ui.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import io.github.skm0078.audora.core.config.CoverConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.playback.covers.CoverFolders
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

/** Decodes and writes cover pictures at [config]'s size. Blocking; the caller is on the covers' IO dispatcher. */
internal class CoverBitmaps(private val config: CoverConfig) {
    /** 16-bit on a low-memory phone ([CoverConfig.lowMemory]): half the bytes per cover in the memory cache. */
    val colours: Bitmap.Config = if (config.lowMemory) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888

    fun decode(file: File): Bitmap? =
        if (file.isFile) BitmapFactory.decodeFile(file.path, BitmapFactory.Options().withColours()) else null

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
            val options = BitmapFactory.Options().withColours().apply { inSampleSize = sampleSize(bounds.outWidth) }
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

    private fun BitmapFactory.Options.withColours() = apply { inPreferredConfig = colours }

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

/** PDFium leaves blank paper transparent; a cover is paper, so it gets white behind it, in [colours]. */
internal fun onWhite(page: Bitmap, colours: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap {
    val cover = Bitmap.createBitmap(page.width, page.height, colours)
    Canvas(cover).apply {
        drawColor(Color.WHITE)
        drawBitmap(page, 0f, 0f, null)
    }
    page.recycle()
    return cover
}
