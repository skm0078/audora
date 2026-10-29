// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/flip/CurlFrame.kt
// Purpose: Draws a page curl around live content (ADR 2026-10-29-page-curl): the page beneath, the part that stays
//          flat, a shadow beside the fold and the turned-over back of the paper. One Canvas, no shader, no 3D.

package io.github.skm0078.audora.ui.reader.flip

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.skm0078.audora.core.config.PageFlipConfig
import io.github.skm0078.audora.core.flip.CurlPoint
import io.github.skm0078.audora.core.flip.Fold
import io.github.skm0078.audora.core.flip.PageCurl
import io.github.skm0078.audora.ui.reader.PageTone
import kotlin.math.roundToInt

/**
 * One frame of a curl. The sheet being turned is either the live content ([image] is then the page beneath it) or
 * [image] itself (the live content then lies beneath; null turns blank paper). [back] is the turned sheet's own
 * picture, shown faintly on the back of the paper; null leaves the back plain paper. The corner at the right edge, at
 * [cornerY], is carried to [finger].
 */
internal class CurlScene(
    val contentOnTop: Boolean,
    val image: ImageBitmap?,
    val back: ImageBitmap?,
    val finger: CurlPoint,
    val cornerY: Float,
)

/** How a curl is shaded: the page's paper and tone, and the strengths from the reading look and the config. */
internal class CurlPaint(val tone: PageTone, val paper: Color, val shadow: TurnShadow, val config: PageFlipConfig)

/**
 * [content] with a curl drawn around it while [scene] gives one; with none, [content] alone, untouched. [scene] is
 * read while drawing only, so each frame of a turn redraws without composing or laying anything out again.
 */
@Composable
internal fun CurlFrame(scene: () -> CurlScene?, paint: CurlPaint, content: @Composable () -> Unit) {
    val filter = remember(paint.tone) { paint.tone.colorMatrix()?.let { ColorFilter.colorMatrix(ColorMatrix(it)) } }
    Box(Modifier.fillMaxSize()) {
        // Beneath the content: the page under a turning content sheet.
        Canvas(Modifier.fillMaxSize()) {
            val curl = scene()?.takeIf { it.contentOnTop } ?: return@Canvas
            drawSheet(curl.image, paint.paper, filter)
        }
        Box(Modifier.fillMaxSize().clipToFlat(scene)) { content() }
        // Over the content: a turning picture, the shadow and the back of the paper.
        Canvas(Modifier.fillMaxSize()) {
            val curl = scene() ?: return@Canvas
            val fold = PageCurl.fold(size.width, size.height, curl.finger, curl.cornerY)
            if (!curl.contentOnTop) {
                // A turning picture lying flat (no fold) covers the content whole.
                if (fold == null) {
                    drawSheet(curl.image, paint.paper, filter)
                } else {
                    clipPath(path(PageCurl.flat(size.width, size.height, fold))) {
                        drawSheet(curl.image, paint.paper, filter)
                    }
                }
            }
            if (fold == null) return@Canvas
            drawShadow(fold, paint)
            drawBack(fold, curl.back, filter, paint)
        }
    }
}

/** While the content is the sheet being turned, only its flat part shows; the page beneath shows where it lifted. */
private fun Modifier.clipToFlat(scene: () -> CurlScene?): Modifier = drawWithContent {
    val curl = scene()
    val fold = curl?.takeIf { it.contentOnTop }?.let { PageCurl.fold(size.width, size.height, it.finger, it.cornerY) }
    if (fold == null) {
        drawContent()
    } else {
        clipPath(path(PageCurl.flat(size.width, size.height, fold))) { this@drawWithContent.drawContent() }
    }
}

/** A page on its paper, its picture across the width from the top as the page view lays it out; blank if null. */
private fun DrawScope.drawSheet(image: ImageBitmap?, paper: Color, filter: ColorFilter?) {
    drawRect(paper)
    if (image != null) drawImage(image, dstOffset = IntOffset.Zero, dstSize = widthFit(image), colorFilter = filter)
}

private fun DrawScope.widthFit(image: ImageBitmap): IntSize = IntSize(
    size.width.roundToInt(),
    (size.width * image.height / image.width.coerceAtLeast(1)).roundToInt(),
)

/** On the page beneath, beside the fold: the shade the lifted paper casts. */
private fun DrawScope.drawShadow(fold: Fold, paint: CurlPaint) {
    val reach = size.width * paint.config.shadowShare
    clipPath(path(PageCurl.lifted(size.width, size.height, fold))) {
        drawRect(
            Brush.linearGradient(
                0f to Color.Black.copy(alpha = paint.shadow.strength),
                1f to Color.Transparent,
                start = Offset(fold.mid.x, fold.mid.y),
                end = Offset(fold.mid.x + fold.nx * reach, fold.mid.y + fold.ny * reach),
            ),
        )
    }
}

/**
 * The back of the turned-over paper: paper colour, the sheet's print faintly mirrored through it, and a soft shade
 * that deepens towards the fold, where the paper bends.
 */
private fun DrawScope.drawBack(fold: Fold, back: ImageBitmap?, filter: ColorFilter?, paint: CurlPaint) {
    val flap = path(PageCurl.flap(size.width, size.height, fold))
    val reach = size.width * paint.config.shadowShare
    clipPath(flap) {
        drawRect(paint.paper)
        if (back != null) {
            withTransform({ transform(mirror(fold)) }) {
                drawImage(
                    back,
                    dstOffset = IntOffset.Zero,
                    dstSize = widthFit(back),
                    alpha = paint.config.backAlpha,
                    colorFilter = filter,
                )
            }
        }
        drawRect(
            Brush.linearGradient(
                0f to Color.Black.copy(alpha = paint.shadow.strength * BACK_SHADE),
                1f to Color.Transparent,
                start = Offset(fold.mid.x, fold.mid.y),
                end = Offset(fold.mid.x - fold.nx * reach, fold.mid.y - fold.ny * reach),
            ),
        )
    }
}

/** [Fold.reflection]'s [a, b, c, d, e, f] placed in Compose's 4x4 matrix. */
private fun mirror(fold: Fold): Matrix {
    val m = fold.reflection()
    return Matrix().apply {
        values[Matrix.ScaleX] = m[0]
        values[Matrix.SkewY] = m[1]
        values[Matrix.SkewX] = m[2]
        values[Matrix.ScaleY] = m[SCALE_Y]
        values[Matrix.TranslateX] = m[TRANSLATE_X]
        values[Matrix.TranslateY] = m[TRANSLATE_Y]
    }
}

private fun path(points: List<CurlPoint>): Path = Path().apply {
    points.firstOrNull()?.let { moveTo(it.x, it.y) }
    points.drop(1).forEach { lineTo(it.x, it.y) }
    close()
}

// The back of the paper is shaded more lightly than the shadow it casts: it faces the light.
private const val BACK_SHADE = 0.6f
private const val SCALE_Y = 3
private const val TRANSLATE_X = 4
private const val TRANSLATE_Y = 5
