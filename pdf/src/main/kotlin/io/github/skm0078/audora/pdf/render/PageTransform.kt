// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/render/PageTransform.kt
// Purpose: Maps glyph boxes in PDF page points onto the rendered page image and taps back (P4-03, ADR-024).

package io.github.skm0078.audora.pdf.render

import io.github.skm0078.audora.core.model.TextBox
import kotlin.math.abs

/** A point in PDF page space: points, origin at the bottom-left of the unrotated page (like [TextBox]). */
data class PagePoint(val x: Float, val y: Float)

/** A point on the displayed page in fractions of its width ([u]) and height ([v]), from the top-left corner. */
data class ViewPoint(val u: Float, val v: Float)

/**
 * A rectangle on the displayed page in fractions of its width and height: (0, 0) is the top-left corner of the
 * image as shown, (1, 1) the bottom-right. Resolution-independent, so a page drawn at any size uses the same rects.
 */
data class ViewRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(left <= right && top <= bottom) { "inverted rect: $this" }
    }
}

/**
 * The affine map from page space to view fractions: `u = a·x + b·y + c`, `v = d·x + e·y + f`.
 *
 * PDFium builds it ([fromSamples]), so the page's `/Rotate`, its crop box and an origin that is not at (0, 0)
 * are all accounted for exactly as when the page is drawn. Rotations are multiples of 90°, so an axis-aligned
 * box stays axis-aligned.
 */
class PageTransform private constructor(
    private val a: Float,
    private val b: Float,
    private val c: Float,
    private val d: Float,
    private val e: Float,
    private val f: Float,
) {
    private val determinant = a * e - b * d

    init {
        require(abs(determinant) > DEGENERATE) { "page transform collapses the page: $this" }
    }

    fun toView(box: TextBox): ViewRect {
        val corners = listOf(
            toView(box.left, box.bottom),
            toView(box.left, box.top),
            toView(box.right, box.bottom),
            toView(box.right, box.top),
        )
        return ViewRect(
            left = corners.minOf { it.u },
            top = corners.minOf { it.v },
            right = corners.maxOf { it.u },
            bottom = corners.maxOf { it.v },
        )
    }

    fun toView(x: Float, y: Float): ViewPoint = ViewPoint(u = a * x + b * y + c, v = d * x + e * y + f)

    /** The page point under a tap at [point]. */
    fun toPage(point: ViewPoint): PagePoint {
        val du = point.u - c
        val dv = point.v - f
        return PagePoint(x = (e * du - b * dv) / determinant, y = (a * dv - d * du) / determinant)
    }

    override fun toString(): String = "PageTransform(u=$a·x+$b·y+$c, v=$d·x+$e·y+$f)"

    companion object {
        private const val DEGENERATE = 1e-12f

        /**
         * From where three page points land in view fractions: the page origin, a point [step] points along x,
         * and one [step] points along y. This is how the PDFium renderer asks for the map, since PDFium exposes
         * it only as a point-mapping function.
         */
        fun fromSamples(origin: ViewPoint, alongX: ViewPoint, alongY: ViewPoint, step: Float): PageTransform {
            require(step > 0f) { "step must be positive, was $step" }
            return PageTransform(
                a = (alongX.u - origin.u) / step,
                b = (alongY.u - origin.u) / step,
                c = origin.u,
                d = (alongX.v - origin.v) / step,
                e = (alongY.v - origin.v) / step,
                f = origin.v,
            )
        }

        /** An unrotated page of [width] × [height] points with its origin at the bottom-left corner. */
        fun upright(width: Float, height: Float): PageTransform {
            require(width > 0f && height > 0f) { "page size must be positive, was $width × $height" }
            return PageTransform(a = 1f / width, b = 0f, c = 0f, d = 0f, e = -1f / height, f = 1f)
        }
    }
}
