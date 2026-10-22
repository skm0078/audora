// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/ChapterProgressTest.kt
// Purpose: The full player's chapter marks (UI plan, wave 4): where they sit and which chapter a tap picks.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.pdf.outline.ChapterEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterProgressTest {
    private val chapters = listOf(
        ChapterEntry("One", 0, 0),
        ChapterEntry("One a", 5, 1),
        ChapterEntry("Two", 25, 0),
        ChapterEntry("Three", 50, 0),
    )

    @Test
    fun `marks the top-level chapters after the first page`() {
        assertEquals(listOf(0.25f, 0.5f), chapterMarks(chapters, 100))
        assertEquals(emptyList<Float>(), chapterMarks(chapters, 1))
        assertEquals(emptyList<Float>(), chapterMarks(emptyList(), 100))
    }

    @Test
    fun `a tap picks the chapter it falls in`() {
        assertEquals("One", chapterAt(chapters, 100, 0.1f)?.title)
        assertEquals("Two", chapterAt(chapters, 100, 0.3f)?.title)
        assertEquals("Three", chapterAt(chapters, 100, 1.5f)?.title)
        assertNull(chapterAt(listOf(ChapterEntry("Late", 40, 0)), 100, 0.1f))
    }

    @Test
    fun `a tap just before a tick takes that tick's chapter`() {
        assertEquals("One", chapterAt(chapters, 100, 0.24f)?.title)
        assertEquals("Two", chapterAt(chapters, 100, 0.24f, snap = 0.02f)?.title)
        assertEquals("Two", chapterAt(chapters, 100, 0.26f, snap = 0.02f)?.title)
        assertEquals("One", chapterAt(chapters, 100, 0.2f, snap = 0.02f)?.title)
    }
}
