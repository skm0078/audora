// File: core/src/main/kotlin/io/github/skm0078/audora/core/flip/PageCurl.kt
// Purpose: The geometry of a page curl (ADR 2026-10-29-page-curl): where a lifted corner folds the page, the part
//          that stays flat, and the flap of paper turned over it. Pure maths, drawn by the reader on a Canvas.

package io.github.skm0078.audora.core.flip

import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

/** A point on the page, in pixels from its top-left corner. */
data class CurlPoint(val x: Float, val y: Float)

/**
 * The line a page folds along: through [mid], square to the unit vector ([nx], [ny]), which points to the lifted
 * side (the side of the corner being turned).
 */
data class Fold(val mid: CurlPoint, val nx: Float, val ny: Float) {
    /** How far [p] is from the fold: positive on the lifted side, negative on the side that stays flat. */
    fun side(p: CurlPoint): Float = (p.x - mid.x) * nx + (p.y - mid.y) * ny

    /** [p] mirrored across the fold: where a point of the lifted part lands once it is turned over. */
    fun reflect(p: CurlPoint): CurlPoint {
        val d = side(p)
        return CurlPoint(p.x - 2 * d * nx, p.y - 2 * d * ny)
    }

    /**
     * The same mirror as an affine matrix, in Android's order: x' = a·x + c·y + e and y' = b·x + d·y + f, returned
     * as [a, b, c, d, e, f], so the turned-over flap can be drawn with the page's own picture.
     */
    fun reflection(): FloatArray {
        val a = 1 - 2 * nx * nx
        val b = -(2 * nx * ny)
        val d = 1 - 2 * ny * ny
        val k = 2 * (mid.x * nx + mid.y * ny)
        return floatArrayOf(a, b, b, d, k * nx, k * ny)
    }
}

/**
 * The page's right-hand corner at [cornerY] (its top, 0, or its bottom, the height) is lifted and carried to a
 * finger point; the page folds along the line halfway between them, square to it. The page is held at its left edge
 * (the spine), so the finger is kept within the page's reach of both spine corners: the paper never tears away.
 */
object PageCurl {
    /** [finger] moved back within reach of the spine, so the page stays joined to it. */
    fun constrain(width: Float, height: Float, finger: CurlPoint, cornerY: Float): CurlPoint {
        val near = CurlPoint(0f, cornerY)
        val far = CurlPoint(0f, height - cornerY)
        val reachNear = width
        val reachFar = hypot(width, height)
        return within(within(finger, near, reachNear), far, reachFar)
    }

    /** The fold for the corner at [cornerY] carried to [finger]; null while the page lies flat (nothing lifted). */
    fun fold(width: Float, height: Float, finger: CurlPoint, cornerY: Float): Fold? {
        val held = constrain(width, height, finger, cornerY)
        val dx = width - held.x
        val dy = cornerY - held.y
        val length = hypot(dx, dy)
        if (length < FLAT_PX) return null
        return Fold(CurlPoint((width + held.x) / 2, (cornerY + held.y) / 2), dx / length, dy / length)
    }

    /** The part of the page that stays flat, as a polygon (empty once the whole page is lifted). */
    fun flat(width: Float, height: Float, fold: Fold): List<CurlPoint> = clip(page(width, height), fold, lifted = false)

    /** The part of the page lifted off, where it lay before turning (the page beneath shows here). */
    fun lifted(width: Float, height: Float, fold: Fold): List<CurlPoint> =
        clip(page(width, height), fold, lifted = true)

    /** The lifted part turned over: the back of the paper, lying across the flat part. */
    fun flap(width: Float, height: Float, fold: Fold): List<CurlPoint> = lifted(width, height, fold).map(fold::reflect)

    /**
     * Where the corner is carried for a turn that no finger makes (reading on, an arrow), at [progress] 0..1: from
     * the right edge to the left for a turn [forward], the other way for a page turned back in. The corner rises by
     * [lift] of the height in the middle of the turn, so the page curls rather than sliding flat.
     */
    fun turning(width: Float, height: Float, progress: Float, forward: Boolean, lift: Float): CurlPoint {
        val t = progress.coerceIn(0f, 1f)
        val x = if (forward) width - 2 * width * t else -width + 2 * width * t
        val y = height - lift * height * sin(PI * t).toFloat()
        return CurlPoint(x, y)
    }

    private fun page(width: Float, height: Float) =
        listOf(CurlPoint(0f, 0f), CurlPoint(width, 0f), CurlPoint(width, height), CurlPoint(0f, height))

    /** One pass of Sutherland-Hodgman: [polygon] cut by the fold, keeping the [lifted] side or the flat one. */
    private fun clip(polygon: List<CurlPoint>, fold: Fold, lifted: Boolean): List<CurlPoint> {
        val sign = if (lifted) 1f else -1f
        val inside = { p: CurlPoint -> fold.side(p) * sign >= 0f }
        val out = ArrayList<CurlPoint>(polygon.size + 1)
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[(i + 1) % polygon.size]
            if (inside(a)) out += a
            if (inside(a) != inside(b)) out += crossing(a, b, fold)
        }
        return out
    }

    private fun crossing(a: CurlPoint, b: CurlPoint, fold: Fold): CurlPoint {
        val da = fold.side(a)
        val t = da / (da - fold.side(b))
        return CurlPoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
    }

    private fun within(p: CurlPoint, centre: CurlPoint, reach: Float): CurlPoint {
        val dx = p.x - centre.x
        val dy = p.y - centre.y
        val distance = hypot(dx, dy)
        if (distance <= reach) return p
        return CurlPoint(centre.x + dx / distance * reach, centre.y + dy / distance * reach)
    }

    /** Closer than this to its corner, the page is flat: no fold to draw. */
    private const val FLAT_PX = 0.5f
}
