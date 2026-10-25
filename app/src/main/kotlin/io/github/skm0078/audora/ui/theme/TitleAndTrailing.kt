// File: app/src/main/kotlin/io/github/skm0078/audora/ui/theme/TitleAndTrailing.kt
// Purpose: An option row's title with its value, help and chevron: on one line when both fit whole, else the value
//          on its own line under the title. A long value ("English (US), Kokoro Michael, male (premium)") never
//          squeezes the title to a letter per line (phone test issue 11).

package io.github.skm0078.audora.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.unit.Constraints

/**
 * [title], then [trailing] at the line's end when the title's whole first line and the trailing part fit side by
 * side; otherwise [trailing] goes under the title, starting where the title starts, with the full width to wrap in.
 * The title is measured at its natural width, so it is never cut or narrowed to make room for a value.
 */
@Composable
internal fun TitleAndTrailing(
    title: @Composable () -> Unit,
    trailing: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (trailing == null) {
        Box(modifier, contentAlignment = Alignment.CenterStart) { title() }
        return
    }
    val parts = @Composable {
        Box { title() }
        Box { trailing() }
    }
    Layout(content = parts, modifier = modifier) { measurables, constraints ->
        val (titlePart, trailingPart) = measurables
        val gap = Dimens.SpaceS.roundToPx()
        val wanted = titlePart.natural() + gap + trailingPart.natural()
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else wanted
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxWidth = width)
        if (wanted <= width) {
            val end = trailingPart.measure(loose)
            val start = titlePart.measure(loose.copy(maxWidth = (width - end.width - gap).coerceAtLeast(0)))
            val height = maxOf(start.height, end.height, constraints.minHeight)
            layout(width, height) {
                start.placeRelative(0, (height - start.height) / 2)
                end.placeRelative(width - end.width, (height - end.height) / 2)
            }
        } else {
            val top = titlePart.measure(loose)
            val below = trailingPart.measure(loose)
            val height = maxOf(top.height + below.height, constraints.minHeight)
            layout(width, height) {
                top.placeRelative(0, 0)
                below.placeRelative(0, top.height)
            }
        }
    }
}

/** The width on one line, unwrapped. */
private fun Measurable.natural(): Int = maxIntrinsicWidth(Constraints.Infinity)
