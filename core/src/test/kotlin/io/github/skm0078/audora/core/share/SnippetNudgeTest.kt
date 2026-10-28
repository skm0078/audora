// File: core/src/test/kotlin/io/github/skm0078/audora/core/share/SnippetNudgeTest.kt
// Purpose: TalkBack's step-at-a-time moves of the page snippet box keep the same rules as a drag.

package io.github.skm0078.audora.core.share

import io.github.skm0078.audora.core.config.ShareCardConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnippetNudgeTest {
    private val config = ShareCardConfig()
    private val step = config.snippetNudgeStep
    private val box = SnippetBox(0.3f, 0.3f, 0.6f, 0.5f)

    private fun SnippetBox.assertWithinRules() {
        assertTrue("on the page: $this", left >= 0f && top >= 0f && right <= 1f && bottom <= 1f)
        assertTrue("wide enough: $this", width >= config.snippetMinSide - EPSILON)
        assertTrue("tall enough: $this", height >= config.snippetMinSide - EPSILON)
        assertTrue("at most a third: $this ($area)", area <= config.snippetMaxShare + EPSILON)
    }

    @Test
    fun eachMoveSlidesOneStepAndKeepsTheSize() {
        val cases = mapOf(
            SnippetNudge.UP to (0f to -step),
            SnippetNudge.DOWN to (0f to step),
            SnippetNudge.LEFT to (-step to 0f),
            SnippetNudge.RIGHT to (step to 0f),
        )
        for ((way, shift) in cases) {
            val moved = box.nudged(way)
            assertEquals("$way left", box.left + shift.first, moved.left, EPSILON)
            assertEquals("$way top", box.top + shift.second, moved.top, EPSILON)
            assertEquals("$way width", box.width, moved.width, EPSILON)
            assertEquals("$way height", box.height, moved.height, EPSILON)
        }
    }

    @Test
    fun movesStopAtThePagesEdges() {
        var moved = box
        repeat(STEPS_ACROSS_THE_PAGE) { moved = moved.nudged(SnippetNudge.UP).nudged(SnippetNudge.LEFT) }
        assertEquals(0f, moved.top, EPSILON)
        assertEquals(0f, moved.left, EPSILON)
        moved.assertWithinRules()
    }

    @Test
    fun biggerAndSmallerChangeTheSizeAboutTheMiddle() {
        val bigger = box.nudged(SnippetNudge.BIGGER)
        assertEquals(box.width + step, bigger.width, EPSILON)
        assertEquals(box.height + step, bigger.height, EPSILON)
        assertEquals((box.left + box.right) / 2, (bigger.left + bigger.right) / 2, EPSILON)
        val smaller = box.nudged(SnippetNudge.SMALLER)
        assertEquals(box.width - step, smaller.width, EPSILON)
        assertEquals((box.top + box.bottom) / 2, (smaller.top + smaller.bottom) / 2, EPSILON)
    }

    @Test
    fun manyStepsNeverBreakTheRules() {
        for (way in SnippetNudge.entries) {
            var nudged = box
            repeat(STEPS_ACROSS_THE_PAGE) {
                nudged = nudged.nudged(way)
                nudged.assertWithinRules()
            }
        }
    }

    private companion object {
        const val EPSILON = 1e-4f
        const val STEPS_ACROSS_THE_PAGE = 40
    }
}
