// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/flip/PageFlip.kt
// Purpose: Swipe a PDF page to turn it (ADR 2026-10-28-page-flip): the page curls up from its corner under the
//          finger, or slides with its neighbour beside it, or turns at once, as the reading look chooses (ADR
//          2026-10-29-page-curl). A turn made by reading on or by an arrow plays the same way, on its own.

package io.github.skm0078.audora.ui.reader.flip

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import io.github.skm0078.audora.core.config.PageFlipConfig
import io.github.skm0078.audora.ui.reader.PageImage
import io.github.skm0078.audora.ui.reader.PageTone

/**
 * What the page view knows of the pages around the one it shows; [reading] while reading aloud moves the pages on,
 * so a turn it makes plays quickly.
 */
internal class FlipPages(
    val page: Int,
    val pageCount: Int,
    val nearby: Map<Int, PageImage>,
    val tone: PageTone,
    val reading: Boolean = false,
)

/** The page shown last time the page view looked, so a change of page can tell which way it went. */
private class ShownPage {
    var page: Int? = null

    fun swap(now: Int): Int? = page.also { page = now }
}

/**
 * Wraps the drawn page. While [enabled] (not zoomed: then a sideways drag pans the page instead), a sideways drag
 * turns the page in [turn]'s style; let go far or fast enough and it turns the rest of the way and [onTurn] is told
 * the page to show. A neighbour not drawn yet shows as blank paper until drawn. TalkBack users turn with the page
 * buttons, which TalkBack's own swipes leave alone.
 */
@Composable
internal fun PageFlip(
    pages: FlipPages,
    enabled: Boolean,
    config: PageFlipConfig,
    onTurn: (Int) -> Unit,
    turn: PageTurn = PageTurn(),
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val flip = remember(config) { FlipState(config, scope) }
    // Read when a finger lets go, not when it touches: reading may turn the page during a drag.
    val current by rememberUpdatedState(pages)
    val style by rememberUpdatedState(turn)
    val turnTo by rememberUpdatedState(onTurn)
    val before = remember { ShownPage() }
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        // The end of a finger's turn, or a turn made some other way, which then plays by itself.
        LaunchedEffect(pages.page, flip.target) {
            flip.shown(pages.page, before.swap(pages.page).takeIf { it != pages.page }, current, style, width)
        }
        val flingPx = with(LocalDensity.current) { config.flingDpPerSecond * density }
        val geometry = FlipGeometry(width, height, flingPx)
        // Zoomed in or picking text: no turns, and a drag under way goes back to its place.
        val gestures = if (enabled) {
            Modifier.flipGestures(flip, geometry, { current }, { style }, { turnTo(it) })
        } else {
            Modifier
        }
        Box(Modifier.fillMaxSize().then(gestures)) {
            when (turn.style) {
                TurnStyle.CURL -> {
                    // A printed page is white paper, whatever the app's theme.
                    val paint = CurlPaint(pages.tone, pages.tone.paper ?: Color.White, turn.shadow, config)
                    CurlFrame({ flip.curl(current, width, height) }, paint, content)
                }

                TurnStyle.SLIDE -> Sliding(flip, pages, width, content)

                TurnStyle.NONE -> content()
            }
        }
    }
}

/** The page follows the finger, its neighbour sliding in beside it. */
@Composable
private fun Sliding(flip: FlipState, pages: FlipPages, width: Float, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().graphicsLayer { translationX = flip.offset }) { content() }
    flip.from?.let { from ->
        Neighbour(pages.nearby[from - 1], pages.tone, translation = { flip.offset - width })
        Neighbour(pages.nearby[from + 1], pages.tone, translation = { flip.offset + width })
    }
}

/** A page drawn ahead, sliding beside the shown one; blank paper if it is not drawn yet. */
@Composable
private fun Neighbour(image: PageImage?, tone: PageTone, translation: () -> Float) {
    val paper = tone.paper ?: MaterialTheme.colorScheme.surface
    val filter = remember(tone) { tone.colorMatrix()?.let { ColorFilter.colorMatrix(ColorMatrix(it)) } }
    Box(Modifier.fillMaxSize().graphicsLayer { translationX = translation() }.background(paper)) {
        if (image != null) {
            Image(
                bitmap = image.imageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                colorFilter = filter,
                modifier = Modifier.fillMaxWidth().aspectRatio(image.aspectRatio),
            )
        }
    }
}

internal class FlipGeometry(val widthPx: Float, val heightPx: Float, val flingPx: Float)

/**
 * The drag runs in the main pass, after the page's own pinch (which consumes two-finger moves) and vertical scroll
 * (which consumes a vertical drag), so neither is ever taken for a turn.
 */
private fun Modifier.flipGestures(
    flip: FlipState,
    geometry: FlipGeometry,
    pages: () -> FlipPages,
    turn: () -> PageTurn,
    onTurn: (Int) -> Unit,
): Modifier = pointerInput(geometry.widthPx, geometry.heightPx) {
    // Zoomed in or rotated mid-drag: this input restarts, and the page goes back to its place.
    try {
        flipGesture(flip, geometry, pages, turn, onTurn)
    } finally {
        flip.abandon()
    }
}

private suspend fun PointerInputScope.flipGesture(
    flip: FlipState,
    geometry: FlipGeometry,
    pages: () -> FlipPages,
    turn: () -> PageTurn,
    onTurn: (Int) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        var slop = 0f
        val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
            change.consume()
            slop = over
        } ?: return@awaitEachGesture
        flip.touch(down.position.y, geometry.heightPx)
        flip.drag(pages(), slop, start.position.y - down.position.y, geometry.widthPx)
        val finished = horizontalDrag(start.id) { change ->
            tracker.addPosition(change.uptimeMillis, change.position)
            val moved = change.positionChange()
            flip.drag(pages(), moved.x, moved.y, geometry.widthPx)
            change.consume()
        }
        // Not finished: a second finger started a pinch, which takes over; the page goes back.
        if (finished) {
            flip.release(pages().pageCount, tracker.calculateVelocity().x, geometry, turn(), onTurn)
        } else {
            flip.abandon()
        }
    }
}
