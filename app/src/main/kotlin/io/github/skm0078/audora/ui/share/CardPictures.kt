// File: app/src/main/kotlin/io/github/skm0078/audora/ui/share/CardPictures.kt
// Purpose: The pictures behind a card: the book's cover softened, and a picture the listener picked, decoded small.

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

    /** The tiny copy a soft cover is grown back from: about 1/45 of the card's width, so only colour stays. */
    private const val SOFT_WIDTH_PX = 24
    private const val SOFT_GROWN_WIDTH_PX = 270

    /** A picked picture is decoded no bigger than the largest card's long side. */
    private const val MAX_SIDE_PX = 1920

    /**
     * The cover shrunk to a few pixels and grown back with filtering: a soft wash of its colours behind the quote.
     * Two cheap scalings instead of a blur effect, so it costs a few milliseconds and no GPU work on old phones.
     */
    fun softened(cover: ImageBitmap): ImageBitmap {
        val source = cover.asAndroidBitmap()
        val tinyHeight = max(1, (source.height * SOFT_WIDTH_PX / source.width.toFloat()).roundToInt())
        val tiny = Bitmap.createScaledBitmap(source.softwareCopy(), SOFT_WIDTH_PX, tinyHeight, true)
        val grownHeight = max(1, (tinyHeight * SOFT_GROWN_WIDTH_PX / SOFT_WIDTH_PX.toFloat()).roundToInt())
        return Bitmap.createScaledBitmap(tiny, SOFT_GROWN_WIDTH_PX, grownHeight, true).asImageBitmap()
    }

    /** The listener's picture from the photo picker (no storage permission), or null if it cannot be read. */
    fun decode(context: Context, picture: Uri): ImageBitmap? = try {
        val source = ImageDecoder.createSource(context.contentResolver, picture)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longSide = max(info.size.width, info.size.height)
            if (longSide > MAX_SIDE_PX) {
                val ratio = MAX_SIDE_PX / longSide.toFloat()
                decoder.setTargetSize((info.size.width * ratio).roundToInt(), (info.size.height * ratio).roundToInt())
            }
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

    private fun Bitmap.softwareCopy(): Bitmap =
        if (config == Bitmap.Config.HARDWARE) copy(Bitmap.Config.ARGB_8888, false) else this
}
