// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/PageToneTest.kt
// Purpose: Dark and sepia pages (plan item 18): white paper and black ink land on the tone's paper and ink.

package io.github.skm0078.audora.ui.reader

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageToneTest {
    /** [matrix] applied to an opaque pixel of 0..255 channels, as Android's ColorMatrix does. */
    private fun apply(matrix: FloatArray, r: Float, g: Float, b: Float): FloatArray = FloatArray(3) { row ->
        val m = row * 5
        matrix[m] * r + matrix[m + 1] * g + matrix[m + 2] * b + matrix[m + 3] * 255f + matrix[m + 4]
    }

    private fun channels(tone: PageTone, paper: Boolean): FloatArray {
        val color = if (paper) tone.paper!! else tone.ink!!
        return floatArrayOf(color.red * 255f, color.green * 255f, color.blue * 255f)
    }

    @Test
    fun `white becomes the paper and black the ink`() {
        listOf(PageTone.SEPIA, PageTone.DARK).forEach { tone ->
            val matrix = tone.colorMatrix()!!
            assertArrayEquals("$tone paper", channels(tone, paper = true), apply(matrix, 255f, 255f, 255f), 0.5f)
            assertArrayEquals("$tone ink", channels(tone, paper = false), apply(matrix, 0f, 0f, 0f), 0.5f)
        }
    }

    @Test
    fun `the original page is not filtered`() {
        assertNull(PageTone.ORIGINAL.colorMatrix())
    }
}
