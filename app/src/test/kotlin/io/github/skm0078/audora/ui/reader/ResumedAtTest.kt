// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/ResumedAtTest.kt
// Purpose: "Picked up on page N": the sentence the saved place points into, cut to its first words.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResumedAtTest {
    private val book = BookId("b")

    private fun unit(index: Int, charStart: Int, text: String) =
        ReadingUnit(3, index, charStart, charStart + text.length, text, Lang.EN, 0, null)

    private val units = listOf(
        unit(0, 0, "Call me Ishmael."),
        unit(1, 17, "Some years ago, never mind how long precisely, having little money."),
    )

    @Test
    fun `the sentence holding the saved offset, cut to its first words`() {
        val resumed = resumedAt(ReadingPosition(book, 3, 30), units, maxWords = 4)
        assertEquals(ResumedAt(3, "Some years ago, never"), resumed)
    }

    @Test
    fun `an offset at a sentence's start is that sentence`() {
        assertEquals("Call me Ishmael.", resumedAt(ReadingPosition(book, 3, 0), units).words)
    }

    @Test
    fun `a page without text says only the page`() {
        val resumed = resumedAt(ReadingPosition(book, 3, 0), emptyList())
        assertEquals(3, resumed.page)
        assertNull(resumed.words)
    }
}
