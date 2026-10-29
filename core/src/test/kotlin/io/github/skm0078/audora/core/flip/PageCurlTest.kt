// File: core/src/test/kotlin/io/github/skm0078/audora/core/flip/PageCurlTest.kt
// Purpose: The page curl's geometry (ADR 2026-10-29-page-curl): the fold, the flat and lifted parts, the flap, the
//          spine holding the page, and the path of a turn no finger makes.

package io.github.skm0078.audora.core.flip

import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageCurlTest {
    @Test
    fun `a page with its corner where it lies is flat`() {
        assertNull(PageCurl.fold(W, H, CurlPoint(W, H), H))
    }

    @Test
    fun `a corner carried straight left folds the page along an upright line halfway`() {
        val fold = requireNotNull(PageCurl.fold(W, H, CurlPoint(W - 400f, H), H))
        assertEquals(W - 200f, fold.mid.x, EPS)
        assertEquals("the lifted side is towards the corner", 1f, fold.nx, EPS)
        assertEquals(0f, fold.ny, EPS)
        val flat = PageCurl.flat(W, H, fold)
        assertEquals(W - 200f, flat.maxOf { it.x }, EPS)
        val flap = PageCurl.flap(W, H, fold)
        assertEquals("the flap reaches as far left as the finger", W - 400f, flap.minOf { it.x }, EPS)
        assertEquals(W - 200f, flap.maxOf { it.x }, EPS)
    }

    @Test
    fun `the flat and lifted parts together are the whole page`() {
        val fold = requireNotNull(PageCurl.fold(W, H, CurlPoint(300f, 1200f), H))
        val total = area(PageCurl.flat(W, H, fold)) + area(PageCurl.lifted(W, H, fold))
        assertEquals(W * H, total, W * H * 1e-4f)
        val lifted = area(PageCurl.lifted(W, H, fold))
        assertEquals("turning over keeps the paper's size", lifted, area(PageCurl.flap(W, H, fold)), 1f)
    }

    @Test
    fun `the corner lands on the finger`() {
        val finger = CurlPoint(500f, 1300f)
        val fold = requireNotNull(PageCurl.fold(W, H, finger, H))
        val landed = fold.reflect(CurlPoint(W, H))
        assertEquals(finger.x, landed.x, EPS)
        assertEquals(finger.y, landed.y, EPS)
    }

    @Test
    fun `the reflection matrix mirrors exactly as reflect does`() {
        val fold = requireNotNull(PageCurl.fold(W, H, CurlPoint(250f, 900f), H))
        val m = fold.reflection()
        val (a, b, c, d) = m.toList()
        val (e, f) = m[4] to m[5]
        for (p in listOf(CurlPoint(0f, 0f), CurlPoint(W, H), CurlPoint(321f, 654f))) {
            val r = fold.reflect(p)
            assertEquals(r.x, a * p.x + c * p.y + e, EPS)
            assertEquals(r.y, b * p.x + d * p.y + f, EPS)
        }
    }

    @Test
    fun `the spine holds the page, so a finger past its reach is pulled back`() {
        val held = PageCurl.constrain(W, H, CurlPoint(-3 * W, H - 50f), H)
        assertTrue("within a page's width of the spine corner", hypot(held.x, held.y - H) <= W + EPS)
        val far = PageCurl.constrain(W, H, CurlPoint(W / 2, -2 * H), H)
        assertTrue("within the diagonal of the far spine corner", hypot(far.x, far.y) <= hypot(W, H) + EPS)
    }

    @Test
    fun `a whole turn carries the corner across the page and leaves nothing flat`() {
        val end = PageCurl.turning(W, H, 1f, forward = true, lift = LIFT)
        assertEquals(-W, end.x, EPS)
        val fold = requireNotNull(PageCurl.fold(W, H, end, H))
        assertEquals(0f, abs(area(PageCurl.flat(W, H, fold))), 1f)
    }

    @Test
    fun `a turn rises in the middle and starts and ends on the corner's edge`() {
        assertEquals(H, PageCurl.turning(W, H, 0f, forward = true, lift = LIFT).y, EPS)
        assertEquals(H - LIFT * H, PageCurl.turning(W, H, 0.5f, forward = true, lift = LIFT).y, EPS)
        assertEquals("a page turned back comes in from the left", -W, PageCurl.turning(W, H, 0f, false, LIFT).x, EPS)
        assertEquals(W, PageCurl.turning(W, H, 1f, forward = false, lift = LIFT).x, EPS)
    }

    @Test
    fun `the top corner lifts towards the top`() {
        val fold = requireNotNull(PageCurl.fold(W, H, CurlPoint(W - 300f, 200f), 0f))
        assertTrue("the lifted side points up towards the top corner", fold.ny < 0f)
    }

    /** The shoelace formula: a polygon's area, whichever way round it is listed. */
    private fun area(points: List<CurlPoint>): Float {
        if (points.size < 3) return 0f
        var twice = 0f
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            twice += a.x * b.y - b.x * a.y
        }
        return abs(twice) / 2
    }

    private companion object {
        const val W = 1080f
        const val H = 1600f
        const val EPS = 0.01f
        const val LIFT = 0.12f
    }
}
