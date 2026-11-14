// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/TextSizeTest.kt
// Purpose: Table-driven checks of the text size buttons: each press moves one step on the slider's grid, and never
//          past the smallest or largest size.

package io.github.skm0078.audora.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class TextSizeTest {
    private val range = ReadingLook.TEXT_SCALES

    @Test
    fun `a press moves one step and lands on the grid`() {
        val cases = listOf(
            Triple(1f, true, 1.1f),
            Triple(1f, false, 0.9f),
            Triple(1.25f, true, 1.35f),
            // Off the grid (an older saved value): snapped as it moves.
            Triple(1.02f, true, 1.1f),
        )
        cases.forEach { (from, larger, expected) ->
            assertEquals("from $from, larger=$larger", expected, stepTextScale(from, larger), TOLERANCE)
        }
    }

    @Test
    fun `the buttons stop at the smallest and largest size`() {
        assertEquals(range.start, stepTextScale(range.start, larger = false), TOLERANCE)
        assertEquals(range.endInclusive, stepTextScale(range.endInclusive, larger = true), TOLERANCE)
        assertEquals(range.start, stepTextScale(range.start + 0.05f, larger = false), TOLERANCE)
        assertEquals(range.endInclusive, stepTextScale(range.endInclusive - 0.05f, larger = true), TOLERANCE)
    }

    @Test
    fun `at either end a press changes nothing, so its button turns off`() {
        // TextSizeBlock disables a button when its step returns the same size: it must, exactly.
        val smallest = stepTextScale(range.start, larger = false)
        assertEquals(smallest, stepTextScale(smallest, larger = false))
        val largest = stepTextScale(range.endInclusive, larger = true)
        assertEquals(largest, stepTextScale(largest, larger = true))
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
