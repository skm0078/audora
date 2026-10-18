// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/ocr/PaperLightTest.kt
// Purpose: The paper light estimate: evenness of a shadowed and an evenly lit page, and the evened image.

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.config.LightingConfig
import io.github.skm0078.audora.pdf.document.PageImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaperLightTest {
    private val config = LightingConfig()

    @Test
    fun `a page half in a hard shadow is uneven and evened to white paper with ink as dark on both sides`() {
        val page = shadowed()
        val light = PaperLight.of(page, config)
        assertTrue("${light.evenness}", light.evenness < config.unevenBelow)
        val evened = light.evened()
        for ((side, columns) in listOf("lit" to (0 until FAR_LEFT), "shadow" to (FAR_RIGHT until WIDTH))) {
            for (x in columns) {
                val grey = evened.grey(x, HEIGHT / 2)
                if (isStroke(x)) {
                    assertTrue("$side ink at $x: $grey", grey in INK_EVENED)
                } else {
                    assertTrue("$side paper at $x: $grey", grey >= WHITE_ENOUGH)
                }
            }
        }
    }

    @Test
    fun `an evenly lit page with text is even, its strokes never taken for shade`() {
        assertEquals(1f, PaperLight.of(shadowed(shadow = LIT), config).evenness)
    }

    @Test
    fun `odd sizes, a black page and a page smaller than a block are handled`() {
        for ((width, height) in listOf(7 to 5, 1 to 1, 2 to 9)) {
            val page = PageImage(0, width, height, DPI, ByteArray(width * height) { (it * 37).toByte() })
            val evened = PaperLight.of(page, config).evened()
            assertEquals(width to height, evened.width to evened.height)
        }
        val black = PageImage(0, 8, 8, DPI, ByteArray(64))
        assertEquals(1f, PaperLight.of(black, config).evenness)
        assertEquals(List(64) { 0 }, PaperLight.of(black, config).evened().gray.map { it.toInt() })
    }

    @Test
    fun `no widening or smoothing samples each block alone`() {
        val bare = LightingConfig(widenBlocks = 0, smoothBlocks = 0)
        val evened = PaperLight.of(shadowed(), bare).evened()
        assertTrue(evened.grey(1, 1) >= WHITE_ENOUGH)
        assertTrue(evened.grey(WIDTH - 2, 1) >= WHITE_ENOUGH)
    }

    @Test
    fun `blocks are sized from the text's line height, never so small that a large photo has too many`() {
        val photo = PageImage(0, 400, 300, 72, ByteArray(400 * 300))
        assertEquals("from the line height, whatever the dpi", 10, PaperLight.of(photo, config, lineHeight = 59f).block)
        assertEquals("no lines read: from the dpi", 2, PaperLight.of(photo, config).block)
        val big = PageImage(0, 4000, 3000, 10, ByteArray(4000 * 3000))
        assertEquals("12 MP at a nominal 10 dpi", 7, PaperLight.of(big, config).block)
    }

    /** Paper [LIT] on the left half and [shadow] on the right, with a one-pixel stroke every [STROKE_EVERY] pixels. */
    private fun shadowed(shadow: Int = SHADOW): PageImage {
        val gray = ByteArray(WIDTH * HEIGHT) { i ->
            val x = i % WIDTH
            val paper = if (x < WIDTH / 2) LIT else shadow
            (if (isStroke(x)) paper * INK_SHARE / 100 else paper).toByte()
        }
        return PageImage(0, WIDTH, HEIGHT, DPI, gray)
    }

    private fun isStroke(x: Int) = x % STROKE_EVERY == STROKE_EVERY / 2

    private fun PageImage.grey(x: Int, y: Int) = gray[y * width + x].toInt() and 0xFF

    private companion object {
        const val WIDTH = 120
        const val HEIGHT = 30
        const val DPI = 96 // Blocks of 3 pixels.
        const val LIT = 230
        const val SHADOW = 90
        const val INK_SHARE = 40
        const val STROKE_EVERY = 6

        // The estimate blends across the shadow's edge within widen + smooth blocks; look well clear of it.
        const val FAR_LEFT = 40
        const val FAR_RIGHT = 80
        const val WHITE_ENOUGH = 250
        val INK_EVENED = 95..110 // 40 % of paper on white is about 102.
    }
}
