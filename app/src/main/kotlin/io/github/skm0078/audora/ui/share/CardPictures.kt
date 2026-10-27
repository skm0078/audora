// File: app/src/main/kotlin/io/github/skm0078/audora/ui/share/CardPictures.kt
// Purpose: The pictures behind a card: the book's cover blurred, and a picture the listener picked, decoded small.

package io.github.skm0078.audora.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/** Blocking: call off the main thread. */
internal object CardPictures {
    private const val TAG = "ShareCard"

    /**
     * The cover is blurred at this width (an eighth of the card's) and drawn stretched: a blur at full size would
     * cost 64 times the work for a picture that is soft anyway.
     */
    private const val BLUR_WIDTH_PX = 135

    /** Box-blur radius, in pixels of the small copy; three passes of it look like a Gaussian blur. */
    private const val BLUR_RADIUS_PX = 6
    private const val BLUR_PASSES = 3

    /** A picked picture is decoded no bigger than the largest card's long side. */
    private const val MAX_SIDE_PX = 1920

    /**
     * The cover blurred behind the quote: shrunk to [BLUR_WIDTH_PX], blurred on the CPU (three box passes each way),
     * then stretched with filtering when drawn. A few milliseconds once per card screen, and it looks the same on
     * every phone (no GPU blur effect, which a captured picture may not carry).
     */
    fun softened(cover: ImageBitmap): ImageBitmap {
        val source = cover.asAndroidBitmap().softwareCopy()
        val height = max(1, (source.height * BLUR_WIDTH_PX / source.width.toFloat()).roundToInt())
        val small = Bitmap.createScaledBitmap(source, BLUR_WIDTH_PX, height, true)
        val pixels = IntArray(BLUR_WIDTH_PX * height)
        small.getPixels(pixels, 0, BLUR_WIDTH_PX, 0, 0, BLUR_WIDTH_PX, height)
        val blurred = BoxBlur.blur(pixels, BLUR_WIDTH_PX, height, BLUR_RADIUS_PX, BLUR_PASSES)
        val out = Bitmap.createBitmap(BLUR_WIDTH_PX, height, Bitmap.Config.ARGB_8888)
        out.setPixels(blurred, 0, BLUR_WIDTH_PX, 0, 0, BLUR_WIDTH_PX, height)
        return out.asImageBitmap()
    }

    /** The listener's picture from the photo picker (no storage permission), or null if it cannot be read. */
    fun decode(context: Context, picture: Uri): ImageBitmap? = try {
        val source = ImageDecoder.createSource(context.contentResolver, picture)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            cappedSize(info.size.width, info.size.height)?.let { (w, h) -> decoder.setTargetSize(w, h) }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }.asImageBitmap()
    } catch (e: IOException) {
        AudoraLog.w(TAG, "Picked picture could not be decoded: ${e.javaClass.simpleName}")
        null
    } catch (e: SecurityException) {
        // The picker's grant can lapse if the screen was recreated long after the pick.
        AudoraLog.w(TAG, "Picked picture no longer readable: ${e.javaClass.simpleName}")
        null
    }

    /**
     * The size a [width] by [height] picture is decoded at: its long side brought down to [MAX_SIDE_PX] (at most
     * about 15 MB in memory, held only while the card screen is open), or null when it is already that small.
     */
    fun cappedSize(width: Int, height: Int, maxSide: Int = MAX_SIDE_PX): Pair<Int, Int>? {
        val longSide = max(width, height)
        if (longSide <= maxSide) return null
        val ratio = maxSide / longSide.toFloat()
        return max(1, (width * ratio).roundToInt()) to max(1, (height * ratio).roundToInt())
    }

    private fun Bitmap.softwareCopy(): Bitmap =
        if (config == Bitmap.Config.HARDWARE) copy(Bitmap.Config.ARGB_8888, false) else this
}
