// File: app/src/main/kotlin/io/github/skm0078/audora/ui/share/ShareCardFrame.kt
// Purpose: Draws any card composable at its full picture size, shown scaled to fit, always with the ListenLock mark,
//          and records it so the very pixels on screen become the shared picture (ADR 2026-10-27-share-cards).

package io.github.skm0078.audora.ui.share

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.skm0078.audora.R
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * [content] laid out on a [CardShape.DESIGN_WIDTH_DP]-wide canvas at [shape]'s full pixel size (one card dp is
 * width / 360 px, text ignores the phone's font size so the picture matches the preview), then scaled down to fit
 * this frame's space. The ListenLock mark is drawn by the frame itself in [markColor], so no card can leave it out
 * (the maintainer's rule: always on). Stats, finished-book and video cards reuse this frame.
 */
@Composable
fun ShareCardFrame(
    shape: CardShape,
    recorder: CardRecorder,
    markColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val byWidth = constraints.maxWidth / shape.widthPx.toFloat()
        val byHeight = if (constraints.hasBoundedHeight) constraints.maxHeight / shape.heightPx.toFloat() else byWidth
        val scale = min(byWidth, byHeight)
        Box(
            Modifier
                .layout { measurable, _ ->
                    val card = measurable.measure(Constraints.fixed(shape.widthPx, shape.heightPx))
                    layout((shape.widthPx * scale).roundToInt(), (shape.heightPx * scale).roundToInt()) {
                        card.placeWithLayer(0, 0) {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                    }
                }
                .drawWithContent {
                    recorder.layer.record { this@drawWithContent.drawContent() }
                    drawLayer(recorder.layer)
                },
        ) {
            val cardDensity = Density(shape.widthPx / CardShape.DESIGN_WIDTH_DP, fontScale = 1f)
            CompositionLocalProvider(LocalDensity provides cardDensity) {
                Box(Modifier.fillMaxSize()) {
                    content()
                    ListenLockMark(markColor, Modifier.align(Alignment.BottomEnd).padding(MARK_INSET))
                }
            }
        }
    }
}

/** The small mark in the card's corner: the real logo and the name, quiet enough not to compete with the quote. */
@Composable
private fun ListenLockMark(color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(MARK_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painterResource(R.drawable.ic_launcher_logo),
            contentDescription = null,
            modifier = Modifier.size(MARK_LOGO),
        )
        Text(
            BRAND,
            color = color,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = MARK_TEXT, letterSpacing = MARK_SPACING),
        )
    }
}

// The brand on every picture, also from a Dev build (whose launcher name says "Dev").
private const val BRAND = "ListenLock"
private val MARK_INSET = 16.dp
private val MARK_GAP = 6.dp
private val MARK_LOGO = 16.dp
private val MARK_TEXT = 12.sp
private val MARK_SPACING = 0.4.sp
