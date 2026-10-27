// File: app/src/main/kotlin/io/github/skm0078/audora/ui/share/CardRecorder.kt
// Purpose: Holds what a ShareCardFrame drew, at the card's full size, and turns it into the picture to share.

package io.github.skm0078.audora.ui.share

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer

/** What [ShareCardFrame] last drew, at the card's full size; [capture] turns it into the picture to share. */
@Stable
class CardRecorder internal constructor(internal val layer: GraphicsLayer) {
    /**
     * The card as a software bitmap of exactly the [CardShape]'s pixels (a hardware bitmap cannot be compressed on
     * every phone, so one is copied). Call it while the frame is on screen: the frame records each time it draws.
     */
    suspend fun capture(): Bitmap {
        val drawn = layer.toImageBitmap().asAndroidBitmap()
        return if (drawn.config == Bitmap.Config.HARDWARE) drawn.copy(Bitmap.Config.ARGB_8888, false) else drawn
    }
}

@Composable
fun rememberCardRecorder(): CardRecorder {
    val layer = rememberGraphicsLayer()
    return remember(layer) { CardRecorder(layer) }
}
