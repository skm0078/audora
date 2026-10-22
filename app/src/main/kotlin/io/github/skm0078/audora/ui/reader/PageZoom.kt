// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/PageZoom.kt
// Purpose: Pinch zoom and pan for the page view (plan item 19): the zoom level, a two-finger pinch that keeps the
//          point under the fingers still, and the render width that keeps zoomed text sharp.

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt

/**
 * The page view's zoom, kept across page turns so a zoomed column stays zoomed. The page is laid out at [scale]
 * times the screen width inside two scrolls, so taps, the spoken-sentence band and the greyed lines, which are all
 * fractions of the page's size, follow the zoom with no matrix of their own.
 */
@Stable
internal class PageZoom {
    var scale by mutableFloatStateOf(MIN_SCALE)
        private set

    /** Where the scrolls should go once the page is laid out at the new [scale]: the pinch's still point. */
    var pending: Offset? = null
        private set

    val zoomed: Boolean get() = scale > MIN_SCALE

    /** Zooms by [factor] about [centroid] (in the viewport), given the scrolls' current positions. */
    fun zoomBy(factor: Float, centroid: Offset, scrolled: Offset) {
        val next = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        if (next == scale) return
        pending = zoomedScroll(scrolled, centroid, next / scale)
        scale = next
    }

    fun reset() {
        scale = MIN_SCALE
        pending = Offset.Zero
    }

    fun consumePending(): Offset? = pending.also { pending = null }

    companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 4f
    }
}

/**
 * The scroll position that keeps the content under [centroid] still when the content grows by [ratio]: the point
 * at `scrolled + centroid` moves to `(scrolled + centroid) * ratio`, minus the same `centroid`.
 */
internal fun zoomedScroll(scrolled: Offset, centroid: Offset, ratio: Float): Offset = Offset(
    ((scrolled.x + centroid.x) * ratio - centroid.x).coerceAtLeast(0f),
    ((scrolled.y + centroid.y) * ratio - centroid.y).coerceAtLeast(0f),
)

/**
 * The width to render the page at for [scale]: rounded to [RENDER_STEP]s of zoom so a pinch re-renders a few times,
 * not every frame. The renderer's own pixel cap still bounds very tall pages.
 */
internal fun renderWidthPx(viewportWidthPx: Int, scale: Float): Int {
    val stepped = (scale / RENDER_STEP).roundToInt().coerceAtLeast(1) * RENDER_STEP
    return (viewportWidthPx * stepped).roundToInt()
}

/**
 * Two fingers pinch; one finger is left to the scrolls underneath (so a drag scrolls and flings as before, and a
 * zoomed page pans). Runs in the initial pass and consumes the pinch, so the scrolls do not also move.
 */
internal fun Modifier.pinchToZoom(zoom: PageZoom, horizontal: ScrollState, vertical: ScrollState): Modifier =
    pointerInput(zoom, horizontal, vertical) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= PINCH_POINTERS) {
                    val factor = event.calculateZoom()
                    if (factor != 1f) {
                        val scrolled = Offset(horizontal.value.toFloat(), vertical.value.toFloat())
                        zoom.zoomBy(factor, event.calculateCentroid(useCurrent = true), scrolled)
                    }
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }

private const val PINCH_POINTERS = 2

/** Zoom steps the page is re-rendered at: half a screen width each. */
private const val RENDER_STEP = 0.5f
