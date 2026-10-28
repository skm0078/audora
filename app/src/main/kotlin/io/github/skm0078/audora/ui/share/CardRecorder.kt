// File: app/src/main/kotlin/io/github/skm0078/audora/ui/share/CardRecorder.kt
// Purpose: Holds what a ShareCardFrame drew, at the card's full size, and turns it into the picture to share.

package io.github.skm0078.audora.ui.share

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toSize

/** What [ShareCardFrame] last drew, at the card's full size; [capture] turns it into the picture to share. */
@Stable
class CardRecorder internal constructor(internal val layer: GraphicsLayer) {
    /**
     * The card as a software bitmap of exactly the [CardShape]'s pixels (a hardware bitmap cannot be compressed on
     * every phone, so one is copied). Call it while the frame is on screen: the frame records each time it draws.
     * Where the GPU cannot make the picture (it gives none: a software-rendered emulator, a phone short of graphics
     * memory), the card is drawn again on the CPU, which every card can be: all its images are software bitmaps.
     *
     * @throws IllegalStateException if the frame has not drawn yet.
     */
    suspend fun capture(): Bitmap {
        // A platform type: the GPU path returns null when it cannot render, despite its signature.
        val drawn: Bitmap? = layer.toImageBitmap().asAndroidBitmap()
        return when {
            drawn == null -> drawnOnTheCpu()
            drawn.config == Bitmap.Config.HARDWARE -> drawn.copy(Bitmap.Config.ARGB_8888, false) ?: drawnOnTheCpu()
            else -> drawn
        }
    }

    private fun drawnOnTheCpu(): Bitmap {
        val size = layer.size
        check(size.width > 0 && size.height > 0) { "The card has not been drawn yet" }
        val bitmap = ImageBitmap(size.width, size.height, ImageBitmapConfig.Argb8888)
        // The layer draws with the density it recorded at; this scope only hands it the canvas.
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), size.toSize()) { drawLayer(layer) }
        return bitmap.asAndroidBitmap()
    }
}

@Composable
fun rememberCardRecorder(): CardRecorder {
    val layer = rememberGraphicsLayer()
    return remember(layer) { CardRecorder(layer) }
}
