// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/TextTurns.kt
// Purpose: How a text view's screen turns (ADR 2026-10-29-page-curl): the screen before the turn is kept as one
//          picture, which curls away over the new screen (or the new screen curls in over it), or the new screen
//          slides in, or it simply shows, as the reading look chooses.

package io.github.skm0078.audora.ui.reader

import android.graphics.Bitmap
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toSize
import io.github.skm0078.audora.core.config.PageFlipConfig
import io.github.skm0078.audora.core.flip.PageCurl
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.ui.reader.flip.CurlFrame
import io.github.skm0078.audora.ui.reader.flip.CurlPaint
import io.github.skm0078.audora.ui.reader.flip.CurlScene
import io.github.skm0078.audora.ui.reader.flip.PageTurn
import io.github.skm0078.audora.ui.reader.flip.TurnStyle

/**
 * A turn begun on one page of the book and played on [page], once it shows: [shot] is the screen turned away from
 * (null unless the turn curls), [reading] whether reading aloud made it.
 */
internal class PendingTurn(val page: Int, val forward: Boolean, val shot: ImageBitmap?, val reading: Boolean)

/** A curl under way over the text: [shot] curls away ([forward]) or lies beneath while the new screen curls in. */
private class TextCurl(val forward: Boolean, val shot: ImageBitmap) {
    var progress by mutableFloatStateOf(0f)
}

/**
 * Plays the text view's turns in the reading look's [turn] style. The screen is recorded as it draws (a display list,
 * no pixels), and turned into a picture only when a curl needs one: about 8 MB for a phone's text area, held for the
 * turn alone, or from the page's last sentence until the next page shows while reading aloud.
 */
@Stable
internal class TextTurner(val layer: GraphicsLayer, val config: PageFlipConfig) {
    var turn: PageTurn = PageTurn()
    val slide = TurnSlide()
    private var curl by mutableStateOf<TextCurl?>(null)

    /** The screen as it is now, before a turn moves it; null unless the turn curls (or the picture fails). */
    suspend fun before(): ImageBitmap? = if (turn.style == TurnStyle.CURL) layer.snapshot() else null

    /** The turn's animation, [forward] or back, after the list moved; [reading] when reading aloud made it. */
    suspend fun play(forward: Boolean, shot: ImageBitmap?, reading: Boolean) {
        val ms = if (reading) turn.readingMs(config) else turn.handMs(config)
        if (ms <= 0) return
        when (turn.style) {
            TurnStyle.CURL -> if (shot != null) curlBy(TextCurl(forward, shot), ms)
            TurnStyle.SLIDE -> slide.play(on = forward, ms = ms)
            TurnStyle.NONE -> Unit
        }
    }

    /** The curl to draw now over a text area [width] by [height]; null when none is under way. */
    fun scene(width: Float, height: Float): CurlScene? = curl?.let { turning ->
        val finger = PageCurl.turning(width, height, turning.progress, turning.forward, config.readingLift)
        val shot = turning.shot
        // On: the old screen curls away over the new. Back: the new one curls in over the old.
        CurlScene(
            contentOnTop = !turning.forward,
            image = shot,
            back = shot.takeIf { turning.forward },
            finger = finger,
            cornerY = height,
        )
    }

    private suspend fun curlBy(turning: TextCurl, ms: Int) {
        curl = turning
        try {
            animate(0f, 1f, animationSpec = tween(ms, easing = FastOutSlowInEasing)) { value, _ ->
                turning.progress = value
            }
        } finally {
            curl = null
        }
    }
}

@Composable
internal fun rememberTextTurner(config: PageFlipConfig, turn: PageTurn): TextTurner {
    val layer = rememberGraphicsLayer()
    return remember(layer, config) { TextTurner(layer, config) }.also { it.turn = turn }
}

/**
 * The text area in its turn: [list] recorded for a picture to curl, slid, or as it is. The list keeps one place in
 * the tree whatever happens, so a turn never resets its scroll or its rows.
 */
@Composable
internal fun TurningText(
    turner: TextTurner,
    look: ReadingLook,
    modifier: Modifier,
    list: @Composable (Modifier) -> Unit,
) {
    // With the printed colours the text lies on the reader's own background (its Scaffold's).
    val paper = look.tone.paper ?: MaterialTheme.colorScheme.background
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val paint = CurlPaint(look.tone, paper, look.turn.shadow, turner.config)
        CurlFrame({ turner.scene(width, height) }, paint) {
            list(Modifier.fillMaxSize().turnSlide(turner.slide).recordInto(turner.layer))
        }
    }
}

/** Records what the list draws into [layer], then draws it: the same pixels, one more display list. */
private fun Modifier.recordInto(layer: GraphicsLayer): Modifier = drawWithContent {
    layer.record { this@drawWithContent.drawContent() }
    drawLayer(layer)
}

/**
 * The layer as a picture. The GPU makes it where it can; where it gives none (a software-rendered emulator, a phone
 * short of graphics memory) it is drawn on the CPU at two bytes a pixel. Null if it has not drawn yet or both fail:
 * the turn then shows without its curl.
 */
private suspend fun GraphicsLayer.snapshot(): ImageBitmap? {
    if (size.width <= 0 || size.height <= 0) return null
    return try {
        // A platform type: the GPU path returns null when it cannot render, despite its signature.
        val drawn: Bitmap? = toImageBitmap().asAndroidBitmap()
        drawn?.asImageBitmap() ?: drawnOnTheCpu()
    } catch (e: IllegalStateException) {
        AudoraLog.w(TAG, "Could not keep the screen for a page curl; turning without it", e)
        null
    }
}

private fun GraphicsLayer.drawnOnTheCpu(): ImageBitmap {
    val bitmap = ImageBitmap(size.width, size.height, ImageBitmapConfig.Rgb565)
    val layer = this
    // The layer draws with the density it recorded at; this scope only hands it the canvas.
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), size.toSize()) { drawLayer(layer) }
    return bitmap
}

private const val TAG = "Audora.TextTurns"
