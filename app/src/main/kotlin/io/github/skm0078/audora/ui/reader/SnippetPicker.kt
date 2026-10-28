// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/SnippetPicker.kt
// Purpose: The box dragged over a drawn page to share part of it, and its bar with Share and Cancel (ADR
//          2026-10-28-page-snippet).

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.share.SnippetBox
import io.github.skm0078.audora.core.share.SnippetCorner
import io.github.skm0078.audora.core.share.SnippetNudge
import io.github.skm0078.audora.core.share.nudged
import io.github.skm0078.audora.ui.theme.Dimens
import kotlin.math.roundToInt

/**
 * Over the page image, in its own coordinates: the page outside [box] dimmed, the box ringed with a handle at each
 * corner. A drag that starts on a handle resizes from it, one inside the box moves it; elsewhere nothing happens.
 * TalkBack users get the box already drawn around the sentence they picked, actions that move or resize it a step at a
 * time ([SnippetNudge]), how much of the page it covers, and the bar's Share.
 */
@Composable
internal fun SnippetPicker(box: SnippetBox, onBox: (SnippetBox) -> Unit) {
    val current by rememberUpdatedState(box)
    val report by rememberUpdatedState(onBox)
    val ring = MaterialTheme.colorScheme.primary
    val description = stringResource(R.string.reader_snippet_box)
    val covers = stringResource(R.string.a11y_snippet_state, (box.area * PERCENT).roundToInt())
    val actions = SnippetNudge.entries.map { way ->
        CustomAccessibilityAction(stringResource(way.label)) {
            report(current.nudged(way))
            true
        }
    }
    Canvas(
        Modifier
            .fillMaxSize()
            .semantics {
                contentDescription = description
                stateDescription = covers
                customActions = actions
            }
            .pointerInput(Unit) {
                var grab: Grab? = null
                // The box as this drag has made it: drag events can come faster than the screen redraws.
                var working = current
                val handle = HANDLE_REACH.toPx()
                detectDragGestures(
                    onDragStart = { start ->
                        working = current
                        grab = grabAt(working, start, size.width.toFloat(), size.height.toFloat(), handle)
                    },
                    onDragEnd = { grab = null },
                    onDragCancel = { grab = null },
                    onDrag = { change, drag ->
                        val dx = drag.x / size.width
                        val dy = drag.y / size.height
                        val next = when (val held = grab) {
                            null -> null
                            Grab.Move -> working.moved(dx, dy)
                            is Grab.Corner -> working.resized(held.corner, dx, dy)
                        }
                        if (next != null) {
                            change.consume()
                            working = next
                            report(next)
                        }
                    },
                )
            },
    ) { drawSnippet(box, ring) }
}

/** The page outside [box] shaded, the box ringed in [ring], a handle at each corner. */
private fun DrawScope.drawSnippet(box: SnippetBox, ring: Color) {
    val topLeft = Offset(box.left * size.width, box.top * size.height)
    val extent = Size(box.width * size.width, box.height * size.height)
    val shade = Color.Black.copy(alpha = SHADE_ALPHA)
    // Four bands around the box, so the box itself keeps the page's own colours.
    drawRect(shade, Offset.Zero, Size(size.width, topLeft.y))
    drawRect(shade, Offset(0f, topLeft.y + extent.height), Size(size.width, size.height - topLeft.y - extent.height))
    drawRect(shade, Offset(0f, topLeft.y), Size(topLeft.x, extent.height))
    drawRect(
        shade,
        Offset(topLeft.x + extent.width, topLeft.y),
        Size(size.width - topLeft.x - extent.width, extent.height),
    )
    drawRect(ring, topLeft, extent, style = Stroke(RING_WIDTH.toPx()))
    val radius = HANDLE_RADIUS.toPx()
    listOf(
        topLeft,
        Offset(topLeft.x + extent.width, topLeft.y),
        Offset(topLeft.x, topLeft.y + extent.height),
        Offset(topLeft.x + extent.width, topLeft.y + extent.height),
    ).forEach { corner ->
        drawCircle(Color.White, radius + RING_WIDTH.toPx(), corner)
        drawCircle(ring, radius, corner)
    }
}

/** Under the page while a snippet is picked: what to do, then Cancel and Share. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SnippetBar(onCancel: () -> Unit, onShare: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), tonalElevation = BAR_ELEVATION, shadowElevation = BAR_ELEVATION) {
        Column(Modifier.padding(Dimens.ScreenPadding), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
            Text(stringResource(R.string.reader_snippet_hint), style = MaterialTheme.typography.bodyMedium)
            // Buttons wrap under each other at large text sizes rather than squeeze their words.
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
            ) {
                OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.password_cancel)) }
                Button(onClick = onShare) { Text(stringResource(R.string.share_card_send)) }
            }
        }
    }
}

private val SnippetNudge.label: Int
    get() = when (this) {
        SnippetNudge.UP -> R.string.a11y_snippet_up
        SnippetNudge.DOWN -> R.string.a11y_snippet_down
        SnippetNudge.LEFT -> R.string.a11y_snippet_left
        SnippetNudge.RIGHT -> R.string.a11y_snippet_right
        SnippetNudge.BIGGER -> R.string.a11y_snippet_bigger
        SnippetNudge.SMALLER -> R.string.a11y_snippet_smaller
    }

private sealed interface Grab {
    data object Move : Grab

    data class Corner(val corner: SnippetCorner) : Grab
}

/** A handle within [reach] px of [start] wins; then the inside of the box; else nothing is held. */
private fun grabAt(box: SnippetBox, start: Offset, width: Float, height: Float, reach: Float): Grab? {
    val corners = mapOf(
        SnippetCorner.TOP_LEFT to Offset(box.left * width, box.top * height),
        SnippetCorner.TOP_RIGHT to Offset(box.right * width, box.top * height),
        SnippetCorner.BOTTOM_LEFT to Offset(box.left * width, box.bottom * height),
        SnippetCorner.BOTTOM_RIGHT to Offset(box.right * width, box.bottom * height),
    )
    val nearest = corners.minBy { (_, at) -> (at - start).getDistance() }
    val inside = start.x / width in box.left..box.right && start.y / height in box.top..box.bottom
    return when {
        (nearest.value - start).getDistance() <= reach -> Grab.Corner(nearest.key)
        inside -> Grab.Move
        else -> null
    }
}

// A handle answers a touch this far from its centre: a 48 dp target around a small dot.
private val HANDLE_REACH = 24.dp
private val HANDLE_RADIUS = 7.dp
private val RING_WIDTH = 2.dp
private val BAR_ELEVATION = 3.dp

// The page outside the box, darkened enough to show what is left out while staying readable.
private const val SHADE_ALPHA = 0.45f

private const val PERCENT = 100
