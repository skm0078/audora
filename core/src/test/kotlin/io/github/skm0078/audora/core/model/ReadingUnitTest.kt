// File: core/src/test/kotlin/io/github/skm0078/audora/core/model/ReadingUnitTest.kt
// Purpose: ReadingUnit invariants and TextBox geometry.

package io.github.skm0078.audora.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingUnitTest {
    private fun unit(charStart: Int, charEnd: Int) = ReadingUnit(0, 0, charStart, charEnd, "x", Lang.EN, 0, null)

    @Test(expected = IllegalArgumentException::class)
    fun `charEnd before charStart is rejected`() {
        unit(charStart = 5, charEnd = 4)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative offsets are rejected`() {
        unit(charStart = -1, charEnd = 4)
    }

    @Test
    fun `text box union covers both boxes`() {
        val a = TextBox(left = 0f, top = 20f, right = 10f, bottom = 10f)
        val b = TextBox(left = 5f, top = 25f, right = 30f, bottom = 12f)
        assertEquals(TextBox(0f, 25f, 30f, 10f), a.union(b))
        assertEquals(30f, a.union(b).width, 0f)
        assertEquals(15f, a.union(b).height, 0f)
    }
}
