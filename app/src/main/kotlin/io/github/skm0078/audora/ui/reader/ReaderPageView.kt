// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReaderPageView.kt
// Purpose: The page view: the PDF page as drawn, the spoken sentence highlighted, junk greyed, tap a line to read.

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.pdf.render.ViewPoint
import io.github.skm0078.audora.pdf.render.ViewRect

/** Fills the space below the page bar; reports its width so the page is drawn at the screen's resolution. */
@Composable
internal fun PageView(state: ReaderState, units: List<ReadingUnit>, actions: ReaderActions) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth
        val viewportHeightPx = constraints.maxHeight
        LaunchedEffect(widthPx) { actions.onPageWidth(widthPx) }
        when (val image = state.pageImage) {
            is PageImageState.Ready -> if (image.page == state.page) {
                DrawnPage(state, image.image, pageNote(units), viewportHeightPx, actions)
            } else {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }

            PageImageState.None, PageImageState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

            PageImageState.Failed -> Text(
                stringResource(R.string.reader_page_render_failed),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        }
    }
}

/** Switches between the drawn page and its sentences as text. */
@Composable
internal fun ViewToggle(view: ReaderView, actions: ReaderActions) {
    when (view) {
        ReaderView.PAGE -> IconButton(onClick = { actions.onViewChanged(ReaderView.TEXT) }) {
            Icon(painterResource(R.drawable.ic_view_text), stringResource(R.string.reader_show_text))
        }

        ReaderView.TEXT -> IconButton(onClick = { actions.onViewChanged(ReaderView.PAGE) }) {
            Icon(painterResource(R.drawable.ic_view_page), stringResource(R.string.reader_show_page))
        }
    }
}

/** Why nothing will be highlighted on this page, if so: no text at all, or text without positions. */
private fun pageNote(units: List<ReadingUnit>): Int? = when {
    units.isEmpty() -> R.string.reader_page_no_text
    units.all { it.bounds.isNullOrEmpty() } -> R.string.reader_page_no_highlight
    else -> null
}

@Composable
private fun DrawnPage(state: ReaderState, image: PageImage, note: Int?, viewportHeightPx: Int, actions: ReaderActions) {
    // A new scroll state per page, so every page opens at its top.
    val scroll = remember(state.page) { ScrollState(0) }
    var pageHeightPx by remember { mutableIntStateOf(0) }
    val highlight = state.spokenHighlight
    LaunchedEffect(highlight, state.following, pageHeightPx, viewportHeightPx) {
        if (state.following) scroll.keepInView(highlight, pageHeightPx, viewportHeightPx)
    }
    val enabled = state.playerReady
    Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
        note?.let {
            Text(
                stringResource(it),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(image.aspectRatio)
                .onSizeChanged { pageHeightPx = it.height }
                .pointerInput(enabled, actions) {
                    if (enabled) {
                        detectTapGestures(
                            onLongPress = { offset ->
                                actions.onPageLongPressed(ViewPoint(offset.x / size.width, offset.y / size.height))
                            },
                            onTap = { offset ->
                                actions.onPageTapped(ViewPoint(offset.x / size.width, offset.y / size.height))
                            },
                        )
                    }
                },
        ) {
            Image(
                bitmap = image.imageBitmap(),
                contentDescription = stringResource(R.string.reader_page_image, state.page + 1),
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
            Dimmed(state.unreadableHighlight, MaterialTheme.colorScheme.surface)
            Highlight(highlight, MaterialTheme.colorScheme.primaryContainer)
        }
    }
}

/**
 * One rounded rect per line of the spoken sentence. Multiply blending tints the paper like a highlighter and
 * leaves the ink dark, so the text stays readable whatever the theme.
 */
@Composable
private fun Highlight(lines: List<ViewRect>, color: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val pad = HIGHLIGHT_PADDING.toPx()
        val radius = CornerRadius(HIGHLIGHT_CORNER.toPx())
        lines.forEach { line ->
            val topLeft = Offset(line.left * size.width - pad, line.top * size.height - pad)
            val extent = Size(
                (line.right - line.left) * size.width + 2 * pad,
                (line.bottom - line.top) * size.height + 2 * pad,
            )
            drawRoundRect(color, topLeft, extent, radius, blendMode = BlendMode.Multiply)
        }
    }
}

/** Greys the lines of unreadable sentences (RI-9): the paper colour over them, translucent, fades their ink. */
@Composable
private fun Dimmed(lines: List<ViewRect>, color: Color) {
    if (lines.isEmpty()) return
    Canvas(Modifier.fillMaxSize()) {
        val pad = HIGHLIGHT_PADDING.toPx()
        lines.forEach { line ->
            val topLeft = Offset(line.left * size.width - pad, line.top * size.height - pad)
            val extent = Size(
                (line.right - line.left) * size.width + 2 * pad,
                (line.bottom - line.top) * size.height + 2 * pad,
            )
            drawRect(color.copy(alpha = DIMMED_ALPHA), topLeft, extent)
        }
    }
}

/** Scrolls so the highlight is on screen, a little below the top, if any of it is outside the viewport. */
private suspend fun ScrollState.keepInView(highlight: List<ViewRect>, pageHeightPx: Int, viewportHeightPx: Int) {
    if (highlight.isEmpty() || pageHeightPx <= 0) return
    val top = (highlight.minOf { it.top } * pageHeightPx).toInt()
    val bottom = (highlight.maxOf { it.bottom } * pageHeightPx).toInt()
    if (top >= value && bottom <= value + viewportHeightPx) return
    animateScrollTo((top - viewportHeightPx * HIGHLIGHT_LEAD).toInt().coerceAtLeast(0))
}

private val HIGHLIGHT_PADDING = 2.dp
private val HIGHLIGHT_CORNER = 3.dp

/** How much of the ink under an unreadable line is covered: enough to read as greyed, not enough to hide it. */
private const val DIMMED_ALPHA = 0.6f

/** Where a highlight that scrolled into view sits: this fraction of the viewport below its top. */
private const val HIGHLIGHT_LEAD = 0.25f
