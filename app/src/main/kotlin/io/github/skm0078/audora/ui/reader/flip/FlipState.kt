// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/flip/FlipState.kt
// Purpose: Where a PDF page is while it turns, by a finger (ADR 2026-10-28-page-flip) or by itself when reading or
//          an arrow moves to the next page (ADR 2026-10-29-page-curl), and the curl each moment of it draws.

package io.github.skm0078.audora.ui.reader.flip

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.skm0078.audora.core.config.PageFlipConfig
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A turn no finger makes: from [before] to the page now shown, [forward] or back, [progress] 0..1 through it. */
internal class AutoTurn(val before: Int, val forward: Boolean) {
    var progress by mutableFloatStateOf(0f)
}

/**
 * Where the page is while it turns. [offset] is how far the shown page has moved (negative: left, towards the next
 * page); [from] the page the drag began on (null when no turn is under way), so the neighbours keep their places even
 * if reading turns the page meanwhile; [target] the page a finished turn asked for. A curl lifts the page's corner at
 * [cornerY] (its top or bottom, picked by where the finger went down), carried up or down by [lift].
 */
internal class FlipState(val config: PageFlipConfig, private val scope: CoroutineScope) {
    var offset by mutableFloatStateOf(0f)
        private set
    var from by mutableStateOf<Int?>(null)
        private set
    var target by mutableStateOf<Int?>(null)
        private set
    var auto by mutableStateOf<AutoTurn?>(null)
        private set
    var cornerY = 0f
        private set
    var lift by mutableFloatStateOf(0f)
        private set
    private var followsHeight = false
    private var settling: Job? = null

    /**
     * A finger went down at [y] of a page [heightPx] tall. In its top or bottom third it takes hold of that corner
     * and carries it up or down with the finger; in the middle third the page folds upright, as a whole edge.
     */
    fun touch(y: Float, heightPx: Float) {
        if (from != null) return
        val third = heightPx / THIRDS
        cornerY = if (y < third) 0f else heightPx
        followsHeight = y < third || y > heightPx - third
        lift = 0f
    }

    /** Moves the page by [dx] (and its corner by [dy]); towards a page that does not exist it gives. */
    fun drag(pages: FlipPages, dx: Float, dy: Float, widthPx: Float) {
        settling?.cancel()
        auto = null
        target = null
        val start = from ?: pages.page.also { from = it }
        val next = offset + dx
        val beyond = (next < 0f && start >= pages.pageCount - 1) || (next > 0f && start <= 0)
        offset = (offset + if (beyond) dx * config.edgeResistance else dx).coerceIn(-widthPx, widthPx)
        if (followsHeight) lift += dy
    }

    /** Let go at [velocity] px/s: turns on to the next or previous page in [turn]'s time, or settles back. */
    fun release(pageCount: Int, velocity: Float, geometry: FlipGeometry, turn: PageTurn, onTurn: (Int) -> Unit) {
        val start = from ?: return
        val way = config.swipeTurn(offset, velocity, geometry.widthPx, geometry.flingPx)
        val destination = start + way
        val turns = way != 0 && destination in 0 until pageCount
        settling = scope.launch {
            settle(if (turns) -way * geometry.widthPx else 0f, geometry.widthPx, turn.handMs(config))
            if (turns) {
                target = destination
                onTurn(destination)
                // A turn the reader refused (the book closed meanwhile) must not leave the page off screen.
                delay(SETTLE_TIMEOUT_MS)
            }
            reset()
        }
    }

    /** A drag that will not finish (a pinch took over, the input restarted) puts the page back; a turn carries on. */
    fun abandon() {
        if (from != null && settling?.isActive != true) {
            settling = scope.launch {
                settle(0f, 0f, config.slideMs)
                reset()
            }
        }
    }

    /**
     * [page] is shown now, [before] was shown until now, on a page [widthPx] wide. The end of a finger's turn; or a
     * turn no finger made (reading on, an arrow, an edge tap), which plays in [turn]'s style, quickly while reading.
     */
    fun shown(page: Int, before: Int?, pages: FlipPages, turn: PageTurn, widthPx: Float) {
        val ms = autoTurnMs(page, before, pages, turn)
        if (target != null && target == page) {
            settling?.cancel()
            reset()
        } else if (before != null && ms > 0) {
            settling?.cancel()
            settling = scope.launch {
                when (turn.style) {
                    TurnStyle.CURL -> curlBy(AutoTurn(before, forward = page > before), ms)
                    TurnStyle.SLIDE -> slideIn(page, forward = page > before, ms, widthPx)
                    TurnStyle.NONE -> Unit
                }
            }
        }
    }

    /** Slides [offset] to [to] in [fullMs] for a whole page's [widthPx]; the corner comes down to its edge with it. */
    private suspend fun settle(to: Float, widthPx: Float, fullMs: Int) {
        val startOffset = offset
        val startLift = lift
        val distance = abs(to - startOffset)
        val ms = if (widthPx > 0f) (fullMs * distance / widthPx).roundToInt() else fullMs
        if (ms <= 0 || distance == 0f) {
            offset = to
            lift = 0f
            return
        }
        animate(startOffset, to, animationSpec = tween(ms, easing = FastOutSlowInEasing)) { value, _ ->
            offset = value
            lift = startLift * abs(to - value) / distance
        }
    }

    private suspend fun curlBy(turning: AutoTurn, ms: Int) {
        auto = turning
        try {
            animate(0f, 1f, animationSpec = tween(ms, easing = FastOutSlowInEasing)) { value, _ ->
                turning.progress = value
            }
        } finally {
            auto = null
        }
    }

    /** The page now shown slides in from the side it came from, pushing the one before out. */
    private suspend fun slideIn(page: Int, forward: Boolean, ms: Int, width: Float) {
        if (width <= 0f) return
        from = page
        offset = if (forward) width else -width
        try {
            settle(0f, width, ms)
        } finally {
            reset()
        }
    }

    private fun reset() {
        offset = 0f
        lift = 0f
        from = null
        target = null
    }

    private companion object {
        const val SETTLE_TIMEOUT_MS = 2_000L
        const val THIRDS = 3f
    }
}

/**
 * How long a turn from [before] to [page] that no finger made plays: only a turn of one page, with the page left
 * behind still drawn and no finger on the page; quickly while reading. 0: it shows at once.
 */
private fun FlipState.autoTurnMs(page: Int, before: Int?, pages: FlipPages, turn: PageTurn): Int = when {
    before == null || abs(page - before) != 1 -> 0
    from != null || pages.nearby[before] == null -> 0
    pages.reading -> turn.readingMs(config)
    else -> turn.handMs(config)
}
