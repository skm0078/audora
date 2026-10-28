// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/flip/PageFlip.kt
// Purpose: Swipe a PDF page to turn it: the page follows the finger with its neighbour sliding in beside it, then
//          slides the rest of the way (ADR 2026-10-28-page-flip).

package io.github.skm0078.audora.ui.reader.flip

import androidx.compose.animation.core.animate
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the page view knows of the pages around the one it shows. */
internal class FlipPages(val page: Int, val pageCount: Int, val nearby: Map<Int, PageImage>, val tone: PageTone)

/**
 * Wraps the drawn page. While [enabled] (not zoomed: then a sideways drag pans the page instead), a sideways drag
 * moves [content] with the finger and slides the neighbour drawn ahead in beside it; let go far or fast enough, the
 * page slides the rest of the way and [onTurn] is told the page to show. A neighbour not drawn yet slides in as blank
 * paper and appears when drawn. TalkBack users turn with the page buttons, which TalkBack's own swipes leave alone.
 */
@Composable
internal fun PageFlip(
    pages: FlipPages,
    enabled: Boolean,
    config: PageFlipConfig,
    onTurn: (Int) -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val flip = remember(config) { FlipState(config, scope) }
    // Read when a finger lets go, not when it touches: reading may turn the page during a drag.
    val current by rememberUpdatedState(pages)
    val turn by rememberUpdatedState(onTurn)
    // The turn is over once the page shown is the one turned to; the slid-in neighbour covers it until then.
    LaunchedEffect(pages.page, flip.target) { flip.settled(pages.page) }
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val width = constraints.maxWidth.toFloat()
        val flingPx = with(LocalDensity.current) { config.flingDpPerSecond * density }
        Box(
            Modifier
                .fillMaxSize()
                .flipGestures(enabled, flip, FlipGeometry(width, flingPx), { current }, { turn(it) }),
        ) {
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = flip.offset }) { content() }
            flip.from?.let { from ->
                Neighbour(pages.nearby[from - 1], pages.tone, translation = { flip.offset - width })
                Neighbour(pages.nearby[from + 1], pages.tone, translation = { flip.offset + width })
            }
        }
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

internal class FlipGeometry(val widthPx: Float, val flingPx: Float)

/**
 * The drag runs in the main pass, after the page's own pinch (which consumes two-finger moves) and vertical scroll
 * (which consumes a vertical drag), so neither is ever taken for a turn.
 */
private fun Modifier.flipGestures(
    enabled: Boolean,
    flip: FlipState,
    geometry: FlipGeometry,
    pages: () -> FlipPages,
    onTurn: (Int) -> Unit,
): Modifier = pointerInput(enabled, geometry.widthPx) {
    if (!enabled) return@pointerInput
    // Zoomed in or rotated mid-drag: this input restarts, and the page goes back to its place.
    try {
        flipGesture(flip, geometry, pages, onTurn)
    } finally {
        flip.abandon()
    }
}

private suspend fun PointerInputScope.flipGesture(
    flip: FlipState,
    geometry: FlipGeometry,
    pages: () -> FlipPages,
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
        flip.drag(pages(), slop, geometry.widthPx)
        val finished = horizontalDrag(start.id) { change ->
            tracker.addPosition(change.uptimeMillis, change.position)
            flip.drag(pages(), change.positionChange().x, geometry.widthPx)
            change.consume()
        }
        // Not finished: a second finger started a pinch, which takes over; the page goes back.
        if (finished) {
            flip.release(pages().pageCount, tracker.calculateVelocity().x, geometry, onTurn)
        } else {
            flip.abandon()
        }
    }
}

/**
 * Where the page is while a finger turns it. [offset] is how far the shown page has moved (negative: left, towards
 * the next page); [from] the page the drag began on (null when no turn is under way), so the neighbours keep their
 * places even if reading turns the page meanwhile; [target] the page a finished turn asked for.
 */
internal class FlipState(private val config: PageFlipConfig, private val scope: CoroutineScope) {
    var offset by mutableFloatStateOf(0f)
        private set
    var from by mutableStateOf<Int?>(null)
        private set
    var target by mutableStateOf<Int?>(null)
        private set
    private var settling: Job? = null

    /** Moves the page by [delta]; towards a page that does not exist (before the first, after the last) it gives. */
    fun drag(pages: FlipPages, delta: Float, widthPx: Float) {
        settling?.cancel()
        target = null
        val start = from ?: pages.page.also { from = it }
        val next = offset + delta
        val beyond = (next < 0f && start >= pages.pageCount - 1) || (next > 0f && start <= 0)
        offset = (offset + if (beyond) delta * config.edgeResistance else delta).coerceIn(-widthPx, widthPx)
    }

    /** Let go at [velocity] px/s: slides on to the next or previous page, or back. */
    fun release(pageCount: Int, velocity: Float, geometry: FlipGeometry, onTurn: (Int) -> Unit) {
        val start = from ?: return
        val way = config.swipeTurn(offset, velocity, geometry.widthPx, geometry.flingPx)
        val destination = start + way
        settling = scope.launch {
            if (way != 0 && destination in 0 until pageCount) {
                animate(offset, -way * geometry.widthPx) { value, _ -> offset = value }
                target = destination
                onTurn(destination)
                // A turn the reader refused (the book closed meanwhile) must not leave the page off screen.
                delay(SETTLE_TIMEOUT_MS)
                reset()
            } else {
                animate(offset, 0f) { value, _ -> offset = value }
                reset()
            }
        }
    }

    /** A drag that will not finish (a pinch took over, the input restarted) puts the page back; a slide carries on. */
    fun abandon() {
        if (from != null && settling?.isActive != true) {
            settling = scope.launch {
                animate(offset, 0f) { value, _ -> offset = value }
                reset()
            }
        }
    }

    /** The page shown is now [page]: if it is the one turned to, the turn is over. */
    fun settled(page: Int) {
        if (target != null && target == page) {
            settling?.cancel()
            reset()
        }
    }

    private fun reset() {
        offset = 0f
        from = null
        target = null
    }

    private companion object {
        const val SETTLE_TIMEOUT_MS = 2_000L
    }
}
