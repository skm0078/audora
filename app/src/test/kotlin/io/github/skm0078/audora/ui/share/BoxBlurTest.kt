// File: app/src/test/kotlin/io/github/skm0078/audora/ui/share/BoxBlurTest.kt
// Purpose: The cover blur keeps flat colour flat, spreads a sharp point, keeps the picture's brightness, and never
//          changes the picture it was given.

package io.github.skm0078.audora.ui.share

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoxBlurTest {
    @Test
    fun `a flat colour stays exactly that colour`() {
        val flat = IntArray(SIDE * SIDE) { TEAL }
        val blurred = BoxBlur.blur(flat, SIDE, SIDE, RADIUS, PASSES)
        assertTrue(blurred.all { it == TEAL })
    }

    @Test
    fun `a bright point spreads to its neighbours and the picture keeps its brightness`() {
        val point = IntArray(SIDE * SIDE) { BLACK }
        point[CENTRE * SIDE + CENTRE] = WHITE
        val blurred = BoxBlur.blur(point, SIDE, SIDE, RADIUS, PASSES)

        val centre = blurred[CENTRE * SIDE + CENTRE] and BLUE_MASK
        val beside = blurred[CENTRE * SIDE + CENTRE + 1] and BLUE_MASK
        assertTrue("the point is no longer sharp", centre < BLUE_MASK)
        assertTrue("its neighbour got some light", beside > 0)
        assertTrue("it fades away from the point", centre >= beside)
        val light = blurred.sumOf { it and BLUE_MASK }
        // Integer averaging rounds down at each step, so a little light is lost, never gained.
        assertTrue("brightness kept within rounding, was $light", light in BLUE_MASK / 2..BLUE_MASK)
    }

    @Test
    fun `the picture given is left as it was, and no passes changes nothing`() {
        val picture = IntArray(SIDE * SIDE) { if (it % 2 == 0) WHITE else BLACK }
        val before = picture.copyOf()
        BoxBlur.blur(picture, SIDE, SIDE, RADIUS, PASSES)
        assertArrayEquals(before, picture)
        assertArrayEquals(before, BoxBlur.blur(picture, SIDE, SIDE, RADIUS, passes = 0))
    }

    @Test
    fun `a picture narrower than the blur still blurs, repeating its edge`() {
        val strip = intArrayOf(WHITE, BLACK)
        val blurred = BoxBlur.blur(strip, 2, 1, RADIUS, PASSES)
        assertEquals(2, blurred.size)
        assertTrue(blurred.all { (it ushr ALPHA_SHIFT) == OPAQUE })
    }

    private companion object {
        const val SIDE = 21
        const val CENTRE = 10
        const val RADIUS = 2
        const val PASSES = 3
        const val BLUE_MASK = 0xFF
        const val ALPHA_SHIFT = 24
        const val OPAQUE = 0xFF
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF000000.toInt()
        const val TEAL = 0xFF336699.toInt()
    }
}
