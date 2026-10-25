// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/PageScrubber.kt
// Purpose: The page scrubber (plan item 17): drag along the book, see each page's picture and number above the
//          thumb, let go to jump there.

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.theme.Dimens
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * @param thumbnails the book's page pictures; null shows the page number alone.
 * @param onGoToPage called once, when the drag ends, with the page under the thumb.
 */
@Composable
internal fun PageScrubber(page: Int, pageCount: Int, thumbnails: PageThumbnails?, onGoToPage: (Int) -> Unit) {
    if (pageCount < 2) return
    var dragging by remember { mutableStateOf<Int?>(null) }
    val shown = dragging ?: page
    val where = stringResource(R.string.reader_page_of, shown + 1, pageCount)
    val name = stringResource(R.string.reader_pick_page)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = Dimens.SpaceL)) {
        val trackPx = constraints.maxWidth
        val range = 0f..(pageCount - 1).toFloat()
        // TalkBack's own slider step is a twentieth of the book (20 pages of 400); a swipe here turns one page.
        Box(
            Modifier.clearAndSetSemantics {
                contentDescription = name
                stateDescription = where
                progressBarRangeInfo = ProgressBarRangeInfo(shown.toFloat(), range)
                setProgress { target ->
                    val next = scrubberStep(shown, target, pageCount)
                    if (next != page) onGoToPage(next)
                    true
                }
            },
        ) {
            Slider(
                value = shown.toFloat(),
                onValueChange = { dragging = it.roundToInt().coerceIn(0, pageCount - 1) },
                onValueChangeFinished = {
                    dragging?.takeIf { it != page }?.let(onGoToPage)
                    dragging = null
                },
                valueRange = range,
            )
        }
        dragging?.let { target ->
            val density = LocalDensity.current
            val previewPx = with(density) { PREVIEW_WIDTH.roundToPx() }
            // The card's bottom sits just above the slider, centred on the thumb.
            val liftPx = with(density) { (Dimens.TouchTarget + Dimens.SpaceS).roundToPx() }
            val thumbX = (trackPx * target / (pageCount - 1).toFloat()).roundToInt()
            Popup(alignment = Alignment.BottomStart, offset = IntOffset(thumbX - previewPx / 2, -liftPx)) {
                ScrubPreview(target, where, thumbnails)
            }
        }
    }
}

@Composable
private fun ScrubPreview(page: Int, label: String, thumbnails: PageThumbnails?) {
    val picture by produceState<ImageBitmap?>(null, page, thumbnails) {
        delay(PREVIEW_DELAY_MS) // a fast drag renders only where it slows down
        value = thumbnails?.get(page)
    }
    Card(Modifier.width(PREVIEW_WIDTH)) {
        Column(
            Modifier.padding(Dimens.SpaceS),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            picture?.let {
                Image(
                    it,
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** One page from [shown] towards [target] (an accessibility service's step), within the book's [pageCount] pages. */
internal fun scrubberStep(shown: Int, target: Float, pageCount: Int): Int = when {
    target > shown -> shown + 1
    target < shown -> shown - 1
    else -> shown
}.coerceIn(0, pageCount - 1)

/** The preview card's width; thumbnails are rendered at this width in pixels. */
internal val PREVIEW_WIDTH = 120.dp

/** How long the thumb rests on a page before its picture is fetched. */
private const val PREVIEW_DELAY_MS = 80L
