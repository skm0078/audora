// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/CoverJpeg.kt
// Purpose: The library's cover for a book, as a small JPEG to put inside exported audiobook files.

package io.github.skm0078.audora.playback.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.github.skm0078.audora.core.config.AudiobookConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

object CoverJpeg {
    private const val TAG = "Export.Cover"

    /**
     * [file] (the library's WebP cover, the listener's own picture or a drawn page) scaled to
     * [AudiobookConfig.coverMaxSidePx] and encoded as JPEG. Null when there is no file or it cannot be decoded: the
     * export goes on without a cover, it never fails for one.
     */
    fun of(file: File?, config: AudiobookConfig = AudiobookConfig()): ByteArray? {
        val decoded = file?.takeIf(File::isFile)?.let { decode(it, config.coverMaxSidePx) } ?: return null
        val scaled = scaledDown(decoded, config.coverMaxSidePx)
        return try {
            ByteArrayOutputStream().use { out ->
                out.takeIf { scaled.compress(Bitmap.CompressFormat.JPEG, config.coverJpegQuality, it) }?.toByteArray()
            }
        } finally {
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
        }
    }

    /** Decoded at the smallest power-of-two size still at least [maxSide], so a big photo never fills memory. */
    private fun decode(file: File, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val longest = max(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(longest, maxSide) }
        val decoded = if (longest > 0) BitmapFactory.decodeFile(file.path, options) else null
        if (decoded == null) AudoraLog.w(TAG, "Cover not decodable, size=${file.length()}")
        return decoded
    }

    /** The largest power of two that still leaves the picture at least [maxSide] on its longer side. */
    private fun sampleSize(longest: Int, maxSide: Int): Int {
        var size = 1
        while (longest / (size * 2) >= maxSide) size *= 2
        return size
    }

    private fun scaledDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val scale = maxSide.toFloat() / longest
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
}
