// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ChapterProgress.kt
// Purpose: The full player's progress bar with chapter marks (UI plan, full player): a tap near a mark reads from
//          that chapter.

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.pdf.outline.ChapterEntry
import io.github.skm0078.audora.ui.theme.Dimens
import kotlin.math.abs

/** Where the top-level chapters after the first page start, as shares (0..1) of the book's [pageCount] pages. */
internal fun chapterMarks(chapters: List<ChapterEntry>, pageCount: Int): List<Float> = if (pageCount <= 1) {
    emptyList()
} else {
    topLevel(chapters).filter { it.page in 1 until pageCount }.map { it.page.toFloat() / pageCount }.distinct()
}

/**
 * The top-level chapter a tap at [share] of the bar picks: a chapter whose mark is within [snap] (a share of the bar)
 * wins, so a tap just before a tick takes that tick's chapter; otherwise the last one starting on or before the page.
 */
internal fun chapterAt(chapters: List<ChapterEntry>, pageCount: Int, share: Float, snap: Float = 0f): ChapterEntry? {
    val at = share.coerceIn(0f, 1f)
    val top = topLevel(chapters)
    val near = top
        .filter { pageCount > 0 && it.page > 0 }
        .minByOrNull { abs(it.page.toFloat() / pageCount - at) }
        ?.takeIf { abs(it.page.toFloat() / pageCount - at) <= snap }
    if (near != null) return near
    val page = (at * pageCount).toInt()
    return top.lastOrNull { it.page <= page }
}

private fun topLevel(chapters: List<ChapterEntry>): List<ChapterEntry> {
    val top = chapters.minOfOrNull { it.depth } ?: return emptyList()
    return chapters.filter { it.depth == top }.sortedBy { it.page }
}

/**
 * The bar fills to [progress]; a tick marks each chapter start. With [onChapter] set, a tap reads from the chapter
 * under the finger, snapping to a tick within [SNAP_DISTANCE]. TalkBack reads the bar as progress and offers a "Read
 * from …" action per chapter (up to [MAX_CHAPTER_ACTIONS]; the Chapters sheet covers longer books).
 */
@Composable
internal fun ChapterProgress(
    progress: Float,
    chapters: List<ChapterEntry>,
    pageCount: Int,
    onChapter: ((ChapterEntry) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val marks = remember(chapters, pageCount) { chapterMarks(chapters, pageCount) }
    val pick by rememberUpdatedState(onChapter)
    val colors = MaterialTheme.colorScheme
    // Mirrored in right-to-left layouts, like the Material indicator it replaced.
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val tappable = onChapter != null && marks.isNotEmpty()
    val tap = if (tappable) {
        Modifier.pointerInput(chapters, pageCount, rtl) {
            detectTapGestures { offset ->
                // Read at the tap, so a resize since the gesture loop started cannot leave it stale.
                val snap = SNAP_DISTANCE.toPx() / size.width
                val x = offset.x / size.width
                chapterAt(chapters, pageCount, if (rtl) 1f - x else x, snap)?.let { chapter -> pick?.invoke(chapter) }
            }
        }
    } else {
        Modifier
    }
    // TalkBack cannot aim a tap at a tick, so each chapter is also an action on the bar, up to a menu's worth; a book
    // with more keeps the Chapters sheet as its TalkBack route.
    val top = remember(chapters) { topLevel(chapters) }
    val actions = if (tappable &&
        top.size <= MAX_CHAPTER_ACTIONS
    ) {
        chapterActions(top) { pick?.invoke(it) }
    } else {
        emptyList()
    }
    Canvas(
        modifier
            .fillMaxWidth()
            // A bar that takes taps is a full touch target tall; a bar that only shows progress stays slim.
            .height(if (onChapter != null) Dimens.TouchTarget else SLIM_HEIGHT)
            .then(tap)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
                if (actions.isNotEmpty()) customActions = actions
            },
    ) {
        val track = TRACK_HEIGHT.toPx()
        val top = (size.height - track) / 2
        val radius = CornerRadius(track / 2)
        drawRoundRect(colors.surfaceVariant, Offset(0f, top), Size(size.width, track), radius)
        val filled = size.width * progress.coerceIn(0f, 1f)
        drawRoundRect(colors.primary, Offset(if (rtl) size.width - filled else 0f, top), Size(filled, track), radius)
        val tick = TICK_HEIGHT.toPx()
        marks.forEach { share ->
            val x = size.width * if (rtl) 1f - share else share
            drawLine(
                colors.onSurfaceVariant,
                Offset(x, (size.height - tick) / 2),
                Offset(x, (size.height + tick) / 2),
                strokeWidth = TICK_WIDTH.toPx(),
            )
        }
    }
}

/** One "Read from …" action per top-level chapter; a blank title reads as its page. */
@Composable
private fun chapterActions(top: List<ChapterEntry>, onPick: (ChapterEntry) -> Unit): List<CustomAccessibilityAction> =
    top.map { chapter ->
        val name = chapter.title.ifBlank { stringResource(R.string.player_page, chapter.page + 1) }
        CustomAccessibilityAction(stringResource(R.string.player_chapter_action, name)) {
            onPick(chapter)
            true
        }
    }

/** More chapter actions than this would bury TalkBack's actions menu; the Chapters sheet lists any number. */
private const val MAX_CHAPTER_ACTIONS = 30

/** How close to a tick a tap counts as aiming at it. */
private val SNAP_DISTANCE = 16.dp

/** The room around the thin bar when it takes no taps (kids mode). */
private val SLIM_HEIGHT = 24.dp
private val TRACK_HEIGHT = 4.dp
private val TICK_HEIGHT = 12.dp
private val TICK_WIDTH = 2.dp
