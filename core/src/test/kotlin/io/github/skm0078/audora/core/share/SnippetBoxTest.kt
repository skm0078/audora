// File: core/src/test/kotlin/io/github/skm0078/audora/core/share/SnippetBoxTest.kt
// Purpose: A page snippet's box stays on the page, keeps its handles grabbable and never covers more than a third of
//          the page, however it is first drawn, moved or resized (ADR 2026-10-28-page-snippet).

package io.github.skm0078.audora.core.share

import io.github.skm0078.audora.core.config.ShareCardConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnippetBoxTest {
    private val config = ShareCardConfig()

    private fun SnippetBox.assertWithinRules() {
        assertTrue("on the page: $this", left >= 0f && top >= 0f && right <= 1f && bottom <= 1f)
        assertTrue("wide enough: $this", width >= config.snippetMinSide - EPSILON)
        assertTrue("tall enough: $this", height >= config.snippetMinSide - EPSILON)
        assertTrue("at most a third: $this ($area)", area <= config.snippetMaxShare + EPSILON)
    }

    @Test
    fun aSentenceGetsABoxAroundItsLinesWithALittlePaper() {
        val lines = listOf(SnippetBox(0.1f, 0.40f, 0.9f, 0.43f), SnippetBox(0.1f, 0.44f, 0.6f, 0.47f))
        val box = requireNotNull(SnippetBox.around(lines))
        assertEquals(0.1f - config.snippetPad, box.left, EPSILON)
        assertEquals(0.40f - config.snippetPad, box.top, EPSILON)
        assertEquals(0.9f + config.snippetPad, box.right, EPSILON)
        assertEquals(0.47f + config.snippetPad, box.bottom, EPSILON)
        box.assertWithinRules()
    }

    @Test
    fun aSentenceTallerThanAThirdKeepsItsFirstLines() {
        val lines = (0 until 30).map { SnippetBox(0.05f, 0.1f + it * 0.025f, 0.95f, 0.12f + it * 0.025f) }
        val box = requireNotNull(SnippetBox.around(lines))
        assertEquals(0.1f - config.snippetPad, box.top, EPSILON)
        assertEquals(config.snippetMaxShare, box.area, EPSILON)
        box.assertWithinRules()
    }

    @Test
    fun aOneWordLineStillGetsAGrabbableBoxAndNoLinesGetNone() {
        val box = requireNotNull(SnippetBox.around(listOf(SnippetBox(0.98f, 0.99f, 0.99f, 0.995f))))
        box.assertWithinRules()
        assertNull(SnippetBox.around(emptyList()))
        SnippetBox.middle().assertWithinRules()
    }

    @Test
    fun movingStopsAtThePagesEdges() {
        val box = SnippetBox(0.2f, 0.2f, 0.6f, 0.4f)
        val moved = box.moved(dx = 0.9f, dy = -0.9f)
        assertEquals(1f, moved.right, EPSILON)
        assertEquals(0f, moved.top, EPSILON)
        assertEquals(box.width, moved.width, EPSILON)
        assertEquals(box.height, moved.height, EPSILON)
    }

    @Test
    fun pullingACornerPastAThirdShortensTheOtherSide() {
        val box = SnippetBox(0.1f, 0.3f, 0.5f, 0.5f)
        // Mostly sideways: the width follows the finger, the height gives way.
        val wide = box.resized(SnippetCorner.BOTTOM_RIGHT, dx = 0.5f, dy = 0.4f, config = config)
        assertEquals(1f, wide.right, EPSILON)
        assertEquals(box.top, wide.top, EPSILON)
        wide.assertWithinRules()
        // Mostly down: the height follows, the width gives way, and the far corner stays put.
        val tall = box.resized(SnippetCorner.TOP_LEFT, dx = -0.1f, dy = -0.3f, config = config)
        assertEquals(0f, tall.top, EPSILON)
        assertEquals(box.right, tall.right, EPSILON)
        assertEquals(box.bottom, tall.bottom, EPSILON)
        tall.assertWithinRules()
    }

    @Test
    fun aCornerCannotCrossTheOtherSide() {
        val box = SnippetBox(0.2f, 0.2f, 0.6f, 0.4f)
        val squeezed = box.resized(SnippetCorner.TOP_RIGHT, dx = -0.9f, dy = 0.9f, config = config)
        assertEquals(box.left + config.snippetMinSide, squeezed.right, EPSILON)
        assertEquals(box.bottom - config.snippetMinSide, squeezed.top, EPSILON)
        squeezed.assertWithinRules()
    }

    private companion object {
        const val EPSILON = 1e-4f
    }
}
