// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReaderPageView.kt
// Purpose: The page view: the PDF page as drawn (or sepia or dark, plan item 18), pinch zoom and pan (plan item 19),
//          the spoken sentence highlighted, junk greyed, tap a line to read.

package io.github.skm0078.audora.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.PageFlipConfig
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.pdf.render.ViewPoint
import io.github.skm0078.audora.pdf.render.ViewRect
import io.github.skm0078.audora.ui.reader.flip.FlipPages
import io.github.skm0078.audora.ui.reader.flip.PageFlip
import io.github.skm0078.audora.ui.theme.readingBand
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Fills the space below the page bar; reports the width to render the page at (the screen's, times the zoom). While
 * a sharper render of the same page is drawn, the last one stays on screen, so zooming never flashes a spinner. A
 * sideways swipe turns the page while it is not zoomed in ([PageFlip]).
 */
@Composable
internal fun PageView(
    state: ReaderState,
    units: List<ReadingUnit>,
    actions: ReaderActions,
    tone: PageTone,
    flip: PageFlipConfig = PageFlipConfig(),
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth
        val viewport = IntSize(widthPx, constraints.maxHeight)
        val zoom = remember { PageZoom() }
        LaunchedEffect(widthPx, zoom.scale) {
            // Zoomed in is said before the zoomed width, back to width after it, so pages are drawn ahead only at it.
            if (zoom.zoomed) {
                actions.onPageZoomed(true)
                delay(RENDER_DELAY_MS) // a pinch in progress re-renders once it rests
            }
            actions.onPageWidth(renderWidthPx(widthPx, zoom.scale))
            if (!zoom.zoomed) actions.onPageZoomed(false)
        }
        val lastDrawn = remember { LastDrawn() }
        val image = state.pageImage
        if (image is PageImageState.Ready) lastDrawn.image = image
        val shown = (image as? PageImageState.Ready ?: lastDrawn.image.takeIf { image == PageImageState.Loading })
            ?.takeIf { it.page == state.page }
        when {
            shown != null -> {
                val sentences = remember(units) { units.filter { it.text.isNotBlank() } }
                val perSentence = rememberSentenceActions(sentences, state.playerReady, actions)
                val pages = FlipPages(state.page, state.pageCount ?: 1, state.nearbyPages, tone)
                val picking = state.snippet != null
                // While a snippet is picked, a drag moves its box: the page neither turns nor reads.
                PageFlip(pages, enabled = !zoom.zoomed && !picking, config = flip, onTurn = actions::onGoToPage) {
                    DrawnPage(state, shown.image, PageLook(pageNote(units), tone, zoom, perSentence), viewport, actions)
                }
                if (picking) {
                    BackHandler(onBack = actions::onSnippetCancel)
                    SnippetBar(
                        actions::onSnippetCancel,
                        actions::onSnippetShare,
                        Modifier.align(Alignment.BottomCenter),
                    )
                } else if (zoom.zoomed) {
                    FilledTonalButton(
                        onClick = zoom::reset,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    ) { Text(stringResource(R.string.reader_fit_width)) }
                }
            }

            image == PageImageState.Failed -> Text(
                stringResource(R.string.reader_page_render_failed),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )

            else -> CircularProgressIndicator(Modifier.align(Alignment.Center))
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

/**
 * The page image drawn last, kept while a re-render of it is in flight. A plain holder, not state: it changes only
 * when the page image does, which already recomposes.
 */
private class LastDrawn {
    var image: PageImageState.Ready? = null
}

/**
 * How the drawn page looks: why nothing is highlighted (if so), its colours and its zoom. [sentenceActions] are
 * TalkBack's way to each sentence of the page, which it reads as one picture.
 */
private class PageLook(
    val note: Int?,
    val tone: PageTone,
    val zoom: PageZoom,
    val sentenceActions: List<CustomAccessibilityAction>,
)

@Composable
private fun DrawnPage(state: ReaderState, image: PageImage, look: PageLook, viewport: IntSize, actions: ReaderActions) {
    // New scroll states per page, so every page opens at its top left.
    val vertical = remember(state.page) { ScrollState(0) }
    val horizontal = remember(state.page) { ScrollState(0) }
    var pageSize by remember { mutableStateOf(IntSize.Zero) }
    val highlight = state.spokenHighlight
    val zoom = look.zoom
    LaunchedEffect(zoom.scale) {
        val target = zoom.consumePending() ?: return@LaunchedEffect
        withFrameNanos { } // the page is laid out at the new size first, so the scrolls can reach the target
        horizontal.scrollTo(target.x.roundToInt())
        vertical.scrollTo(target.y.roundToInt())
    }
    LaunchedEffect(highlight, state.following, pageSize, viewport) {
        if (state.following) {
            vertical.keepInView(highlight.map { it.top to it.bottom }, pageSize.height, viewport.height)
            if (zoom.zoomed) {
                horizontal.keepInView(
                    highlight.map {
                        it.left to it.right
                    },
                    pageSize.width,
                    viewport.width,
                )
            }
        }
    }
    val zoomActions = zoomActions(zoom, viewport, horizontal, vertical)
    val pageWidth = with(LocalDensity.current) { (viewport.width * zoom.scale).toDp() }
    Column(
        Modifier
            .fillMaxSize()
            .pinchToZoom(zoom, horizontal, vertical)
            .verticalScroll(vertical)
            .horizontalScroll(horizontal, enabled = zoom.zoomed),
    ) {
        look.note?.let {
            Text(
                stringResource(it),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        Box(
            Modifier
                .width(pageWidth)
                .aspectRatio(image.aspectRatio)
                .onSizeChanged { pageSize = it }
                .pageTaps(state.playerReady && state.snippet == null, actions, LocalHapticFeedback.current),
        ) {
            PageLayers(state, image, look, highlight, zoomActions)
            state.snippet?.let { SnippetPicker(it, actions::onSnippetChanged) }
        }
    }
}

/**
 * TalkBack cannot pinch: Zoom in and Zoom out step the zoom about the middle of the screen, each offered only while it
 * can still go that way. Fit width (the button shown while zoomed) goes back in one step.
 */
@Composable
private fun zoomActions(
    zoom: PageZoom,
    viewport: IntSize,
    horizontal: ScrollState,
    vertical: ScrollState,
): List<CustomAccessibilityAction> {
    val zoomIn = stringResource(R.string.a11y_page_zoom_in)
    val zoomOut = stringResource(R.string.a11y_page_zoom_out)
    fun step(factor: Float) = CustomAccessibilityAction(if (factor > 1f) zoomIn else zoomOut) {
        val middle = Offset(viewport.width / 2f, viewport.height / 2f)
        zoom.zoomBy(factor, middle, Offset(horizontal.value.toFloat(), vertical.value.toFloat()))
        true
    }
    return listOfNotNull(
        step(PageZoom.ACTION_STEP).takeIf { zoom.scale < PageZoom.MAX_SCALE },
        step(1f / PageZoom.ACTION_STEP).takeIf { zoom.zoomed },
    )
}

/** The page drawn in its colours, then the greyed lines and the spoken sentence's band over it. */
@Composable
private fun PageLayers(
    state: ReaderState,
    image: PageImage,
    look: PageLook,
    highlight: List<ViewRect>,
    zoomActions: List<CustomAccessibilityAction>,
) {
    val tone = look.tone
    Image(
        bitmap = image.imageBitmap(),
        contentDescription = stringResource(R.string.reader_page_image, state.page + 1),
        contentScale = ContentScale.FillBounds,
        colorFilter = remember(tone) { tone.colorMatrix()?.let { ColorFilter.colorMatrix(ColorMatrix(it)) } },
        modifier = Modifier.fillMaxSize().semantics {
            val pageActions = zoomActions + look.sentenceActions
            if (pageActions.isNotEmpty()) customActions = pageActions
        },
    )
    Dimmed(state.unreadableHighlight, tone.paper ?: MaterialTheme.colorScheme.surface)
    Highlight(highlight, MaterialTheme.colorScheme.readingBand(Color.White), tone)
}

/**
 * Tap a line to read from it, long-press for its menu (with a tick, as long-presses elsewhere give); positions are
 * fractions of the page, whatever the zoom.
 */
private fun Modifier.pageTaps(enabled: Boolean, actions: ReaderActions, haptics: HapticFeedback): Modifier =
    pointerInput(enabled, actions) {
        if (enabled) {
            detectTapGestures(
                onLongPress = { offset ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    actions.onPageLongPressed(ViewPoint(offset.x / size.width, offset.y / size.height))
                },
                onTap = { offset -> actions.onPageTapped(ViewPoint(offset.x / size.width, offset.y / size.height)) },
            )
        }
    }

/**
 * One rounded rect per line of the spoken sentence. On light paper, multiply blending tints the paper like a
 * highlighter and leaves the ink dark, so the text stays readable whatever the theme. Multiply would vanish on a
 * dark page, so there the band is drawn translucent over it instead.
 */
@Composable
private fun Highlight(lines: List<ViewRect>, color: Color, tone: PageTone) {
    val dark = tone == PageTone.DARK
    val band = if (dark) color.copy(alpha = DARK_HIGHLIGHT_ALPHA) else color
    val blend = if (dark) BlendMode.SrcOver else BlendMode.Multiply
    Canvas(Modifier.fillMaxSize()) {
        val pad = HIGHLIGHT_PADDING.toPx()
        val radius = CornerRadius(HIGHLIGHT_CORNER.toPx())
        lines.forEach { line ->
            val topLeft = Offset(line.left * size.width - pad, line.top * size.height - pad)
            val extent = Size(
                (line.right - line.left) * size.width + 2 * pad,
                (line.bottom - line.top) * size.height + 2 * pad,
            )
            drawRoundRect(band, topLeft, extent, radius, blendMode = blend)
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

/**
 * Scrolls so the highlight's [spans] (start and end, as fractions of the page along this scroll's axis) are on
 * screen, a little in from the start, if any of them is outside the viewport.
 */
private suspend fun ScrollState.keepInView(spans: List<Pair<Float, Float>>, pagePx: Int, viewportPx: Int) {
    if (spans.isEmpty() || pagePx <= 0) return
    val start = (spans.minOf { it.first } * pagePx).toInt()
    val end = (spans.maxOf { it.second } * pagePx).toInt()
    if (start >= value && end <= value + viewportPx) return
    animateScrollTo((start - viewportPx * HIGHLIGHT_LEAD).toInt().coerceAtLeast(0))
}

private val HIGHLIGHT_PADDING = 2.dp
private val HIGHLIGHT_CORNER = 3.dp

/** How much of the ink under an unreadable line is covered: enough to read as greyed, not enough to hide it. */
private const val DIMMED_ALPHA = 0.6f

/** A dark page's highlight band: strong enough to find, faint enough to read the light ink through. */
private const val DARK_HIGHLIGHT_ALPHA = 0.4f

/** Where a highlight that scrolled into view sits: this fraction of the viewport in from its start. */
private const val HIGHLIGHT_LEAD = 0.25f

/** How long a zoom rests before the page is re-rendered sharper for it. */
private const val RENDER_DELAY_MS = 300L
