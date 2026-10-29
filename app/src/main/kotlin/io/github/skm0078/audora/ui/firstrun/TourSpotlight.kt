// File: app/src/main/kotlin/io/github/skm0078/audora/ui/firstrun/TourSpotlight.kt
// Purpose: One stop of the tour: the screen dimmed around what the stop points at, outlined, and a card beside it
//          saying what it is, with Skip and Next (ADR 2026-10-28-home-and-tour).

package io.github.skm0078.audora.ui.firstrun

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.theme.Dimens
import kotlin.math.min

/**
 * The stop's card sits under the outlined [hole] when it fits there, else above it, kept inside the screen; with no
 * hole (the target is off screen) it sits in the middle. Taps on the dimmed screen do nothing, so nothing opens by
 * mistake mid-tour; Back steps to the stop before.
 */
@Composable
internal fun TourSpotlight(stop: TourStop, hole: Rect?, tour: GuidedTour) {
    BackHandler(onBack = tour::back)
    var origin by remember { mutableStateOf(Offset.Zero) }
    val local = hole?.translate(-origin)
    Box(
        Modifier
            .fillMaxSize()
            .testTag(TOUR_TAG)
            .onGloballyPositioned { origin = it.positionInRoot() },
    ) {
        Scrim(local)
        CalloutLayout(local) {
            Arrow()
            Callout(stop, tour)
        }
    }
}

@Composable
private fun Scrim(hole: Rect?) {
    val outline = MaterialTheme.colorScheme.surface
    val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA)
    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .pointerInput(Unit) { detectTapGestures {} }
            .clearAndSetSemantics {},
    ) {
        drawRect(scrim)
        hole?.inflate(HOLE_MARGIN.toPx())?.let { around ->
            val corner = CornerRadius(HOLE_CORNER.toPx())
            drawRoundRect(Color.Black, around.topLeft, around.size, corner, blendMode = BlendMode.Clear)
            drawRoundRect(outline, around.topLeft, around.size, corner, style = Stroke(OUTLINE.toPx()))
        }
    }
}

/** Places the arrow (first) and the card (second, drawn over the arrow's back half) beside the hole. */
@Composable
private fun CalloutLayout(hole: Rect?, content: @Composable () -> Unit) {
    Layout(content, Modifier.fillMaxSize()) { measurables, constraints ->
        val margin = CALLOUT_MARGIN.roundToPx()
        val gap = (HOLE_MARGIN + ARROW).roundToPx()
        val width = min(constraints.maxWidth - 2 * margin, CALLOUT_MAX_WIDTH.roundToPx())
        val card = measurables[1].measure(Constraints(maxWidth = width, maxHeight = constraints.maxHeight - 2 * margin))
        val arrow = measurables[0].measure(Constraints.fixed(ARROW.roundToPx(), ARROW.roundToPx()))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val h = constraints.maxHeight
            val centreX = hole?.center?.x?.toInt() ?: (constraints.maxWidth / 2)
            val x = (centreX - card.width / 2).coerceIn(margin, constraints.maxWidth - margin - card.width)
            val below = hole == null || hole.bottom + gap + card.height <= h - margin
            val y = when {
                hole == null -> (h - card.height) / 2
                below -> hole.bottom.toInt() + gap
                else -> hole.top.toInt() - gap - card.height
            }.coerceIn(margin, (h - margin - card.height).coerceAtLeast(margin))
            if (hole != null) {
                val inset = CALLOUT_CORNER.roundToPx()
                val ax = (centreX - arrow.width / 2).coerceIn(x + inset, x + card.width - inset - arrow.width)
                arrow.place(ax, if (below) y - arrow.height / 2 else y + card.height - arrow.height / 2)
            }
            card.place(x, y)
        }
    }
}

@Composable
private fun Arrow() {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(2.dp),
        modifier = Modifier.size(ARROW).rotate(ARROW_TURN),
    ) {}
}

/** "Step 2 of 5" is read out when the stop changes, then its title and words. */
@Composable
private fun Callout(stop: TourStop, tour: GuidedTour) {
    val index = stop.ordinal
    val last = index == TourStop.entries.lastIndex
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(CALLOUT_CORNER),
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(Dimens.SpaceL), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
            Text(
                stringResource(R.string.tour_step, index + 1, TourStop.entries.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Text(
                stringResource(stop.title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(stop.body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepDots(index, Modifier.weight(1f))
                if (!last) TextButton(onClick = tour::close) { Text(stringResource(R.string.tour_skip)) }
                Button(onClick = tour::next, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) {
                    Text(stringResource(if (last) R.string.tour_done else R.string.tour_next))
                }
            }
        }
    }
}

/** For UI tests: the tour's dimmed screen while a stop shows. */
const val TOUR_TAG = "guided_tour"

/** Dark enough that the outlined target is what the eye finds, light enough that the screen behind still reads. */
private const val SCRIM_ALPHA = 0.62f

/** A square turned to a diamond, half under the card, reads as the card's pointer. */
private const val ARROW_TURN = 45f
private val HOLE_MARGIN = 6.dp
private val HOLE_CORNER = 14.dp
private val OUTLINE = 2.dp
private val ARROW = 16.dp
private val CALLOUT_MARGIN = 20.dp
private val CALLOUT_MAX_WIDTH = 420.dp
private val CALLOUT_CORNER = 20.dp
