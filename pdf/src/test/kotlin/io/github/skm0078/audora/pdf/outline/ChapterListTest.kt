// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/outline/ChapterListTest.kt
// Purpose: The chapter list (plan item 11): outline order, depth limit, bad pages left out, the current row.

package io.github.skm0078.audora.pdf.outline

import io.github.skm0078.audora.pdf.document.OutlineEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterListTest {
    private val outline = listOf(
        OutlineEntry(" Preface ", 1),
        OutlineEntry(
            "Part I",
            null,
            listOf(
                OutlineEntry("Chapter 1", 4, listOf(OutlineEntry("1.1 Start", 4), OutlineEntry("Deep", 5))),
                OutlineEntry("Chapter 2", 9),
                OutlineEntry("Broken", 99),
            ),
        ),
    )

    @Test
    fun `entries come in outline order with their depth, down to the limit`() {
        val rows = ChapterList.from(outline, pageCount = 20, maxDepth = 2)
        assertEquals(
            listOf(ChapterEntry("Preface", 1, 0), ChapterEntry("Chapter 1", 4, 1), ChapterEntry("Chapter 2", 9, 1)),
            rows,
        )
        assertEquals(5, ChapterList.from(outline, pageCount = 20, maxDepth = 3).size)
        assertEquals(emptyList<ChapterEntry>(), ChapterList.from(emptyList(), pageCount = 20, maxDepth = 3))
    }

    @Test
    fun `the current row is the latest start on or before the page, sections over chapters`() {
        val rows = ChapterList.from(outline, pageCount = 20, maxDepth = 3)
        assertNull(ChapterList.currentIndex(rows, 0))
        assertEquals(0, ChapterList.currentIndex(rows, 3))
        assertEquals("1.1 Start", rows[requireNotNull(ChapterList.currentIndex(rows, 4))].title)
        assertEquals("Deep", rows[requireNotNull(ChapterList.currentIndex(rows, 7))].title)
        assertEquals("Chapter 2", rows[requireNotNull(ChapterList.currentIndex(rows, 19))].title)
    }
}
