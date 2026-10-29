// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/TextPages.kt
// Purpose: The text view a screenful at a time: a sideways swipe or the arrows turn to the next screen, never
//          cutting a sentence's line in two, and on to the book's next page at the end (ADR 2026-10-28-text-pages).

package io.github.skm0078.audora.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.github.skm0078.audora.R
import kotlin.math.abs

/**
 * Remembered by the reader screen across the book's pages: a turn back from a page's first screen opens the page
 * before at its last screen, as a book's page turned back shows its foot.
 */
internal class TextTurnBack {
    var openAtEnd = false

    /** A turn begun on the page before, to play once the new page shows (ADR 2026-10-29-page-curl). */
    var pending: PendingTurn? = null
}

/**
 * Turns [list] (the text view, its own scrolling off) a screenful at a time. The first sentence or paragraph not
 * wholly on screen starts the next screen, so no line is cut in two; one taller than the screen is stepped through
 * a screen's height at a time. Earlier screens are remembered, so Back returns to exactly where each began.
 */
@Stable
internal class TextScreens(private val list: LazyListState) {
    private val earlier = ArrayDeque<Pair<Int, Int>>()

    /** Screens turned on from the page's first, for TalkBack's "continued". */
    var turned by mutableIntStateOf(0)
        private set

    /** False at the page's last screen: the book's next page is the caller's to show. */
    suspend fun next(): Boolean {
        val info = list.layoutInfo
        val visible = info.visibleItemsInfo
        val last = visible.lastOrNull()
        val end = info.viewportEndOffset
        if (last == null || atEnd) return false
        earlier.addLast(list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset)
        val cut = visible.firstOrNull { it.offset + it.size > end } ?: last
        if (cut.index == list.firstVisibleItemIndex) {
            list.scrollBy((end - info.viewportStartOffset).toFloat())
        } else {
            list.scrollToItem(cut.index)
        }
        turned++
        return true
    }

    /** False at the page's first screen: the book's page before is the caller's to show. */
    suspend fun previous(): Boolean {
        if (list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0) return false
        val back = earlier.removeLastOrNull()
        if (back != null) {
            list.scrollToItem(back.first, back.second)
        } else {
            // Reached by reading on rather than by turning: back a screen's height.
            val info = list.layoutInfo
            list.scrollBy(-(info.viewportEndOffset - info.viewportStartOffset).toFloat())
        }
        turned = (turned - 1).coerceAtLeast(0)
        return true
    }

    /** Which way [keepInView] will turn for [item]: 1 on, -1 back, 0 not at all (it is wholly on this screen). */
    fun turnFor(item: Int): Int {
        val info = list.layoutInfo
        val shown = info.visibleItemsInfo.firstOrNull { it.index == item }
        val whole = shown != null && shown.offset >= info.viewportStartOffset &&
            shown.offset + shown.size <= info.viewportEndOffset
        return when {
            whole -> 0
            item >= list.firstVisibleItemIndex -> 1
            else -> -1
        }
    }

    /** True once the page's last item is wholly on screen: the next turn shows the book's next page. */
    val atEnd: Boolean
        get() {
            val info = list.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return false
            return last.index == info.totalItemsCount - 1 && last.offset + last.size <= info.viewportEndOffset
        }

    /** Reading moved to [item]: it starts a screen of its own unless it is wholly on this one already. */
    suspend fun keepInView(item: Int) {
        if (turnFor(item) == 0) return
        earlier.clear()
        list.scrollToItem(item)
        turned++
    }

    /** The page's last screen, for a turn back from the next page. */
    suspend fun toEnd() {
        val count = list.layoutInfo.totalItemsCount
        if (count > 0) list.scrollToItem(count - 1)
    }
}

/**
 * The turn's slide: the new screen comes in from the side it was turned from, a fifth of the width, fading up as it
 * settles. Drawn by the list's layer only, so a turn lays nothing out again; with the phone's animations off the
 * slide finishes at once (Compose follows the system's animation scale).
 */
@Stable
internal class TurnSlide {
    private val offset = Animatable(0f)

    /** [on]: the new screen comes in from the right, as a page turned on; else from the left; over [ms]. */
    suspend fun play(on: Boolean, ms: Int = TURN_MS) {
        offset.snapTo(if (on) 1f else -1f)
        offset.animateTo(0f, tween(ms, easing = FastOutSlowInEasing))
    }

    /** Where the slide is: 1 or -1 as it starts, 0 settled. */
    val at: Float get() = offset.value
}

/** [slide] read in the layer, so each frame of a turn only redraws. */
internal fun Modifier.turnSlide(slide: TurnSlide): Modifier = graphicsLayer {
    val at = slide.at
    translationX = at * size.width * SLIDE_SHARE
    alpha = 1f - abs(at) * FADE
}

/** A sideways swipe past [threshold] dp turns: right to left on, left to right back. */
internal fun Modifier.screenSwipes(onNext: () -> Unit, onPrevious: () -> Unit, threshold: Float = SWIPE_DP): Modifier =
    pointerInput(onNext, onPrevious) {
        var travelled = 0f
        detectHorizontalDragGestures(
            onDragStart = { travelled = 0f },
            onDragEnd = {
                if (abs(travelled) >= threshold * density) if (travelled < 0) onNext() else onPrevious()
            },
            onHorizontalDrag = { change, amount ->
                change.consume()
                travelled += amount
            },
        )
    }

/** Under the text: back and on a screen, and where the screen is, said by TalkBack after each turn. */
@Composable
internal fun TextPagesBar(
    page: Int,
    pageCount: Int,
    turned: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val where = if (turned > 0) {
        stringResource(R.string.text_pages_continued, page + 1, pageCount)
    } else {
        stringResource(R.string.reader_page_of, page + 1, pageCount)
    }
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.text_pages_previous))
        }
        Text(
            where,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = onNext) {
            Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.text_pages_next))
        }
    }
}

// Short enough not to hold up a fast reader, long enough to see which way the page went.
private const val TURN_MS = 200
private const val SLIDE_SHARE = 0.2f
private const val FADE = 0.6f

// Far enough that a slightly slanted scroll or a tap's wobble never turns.
private const val SWIPE_DP = 56f
