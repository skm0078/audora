// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/outline/ChapterStartsTest.kt
// Purpose: Chapter start pages from PDF outlines of the shapes real books have.

package io.github.skm0078.audora.pdf.outline

import io.github.skm0078.audora.pdf.document.OutlineEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterStartsTest {
    private fun entry(page: Int?, vararg children: OutlineEntry) = OutlineEntry("t", page, children.toList())

    private fun starts(vararg outline: OutlineEntry, pageCount: Int = 100) =
        ChapterStarts.from(outline.toList(), pageCount, maxDepth = 3)

    @Test
    fun `top-level entries are the chapters`() {
        assertEquals(listOf(0, 12, 40), starts(entry(12), entry(0), entry(40, entry(45), entry(50))))
    }

    @Test
    fun `a single wrapping entry is skipped for the level below`() {
        assertEquals(listOf(3, 20, 61), starts(entry(0, entry(3), entry(20, entry(25)), entry(61))))
    }

    @Test
    fun `an outline that never splits keeps its pages`() {
        assertEquals(listOf(4, 9), starts(entry(9, entry(4))))
    }

    @Test
    fun `entries without a page or outside the book are ignored`() {
        assertEquals(listOf(5, 30), starts(entry(null), entry(5), entry(30), entry(-1), entry(100)))
    }

    @Test
    fun `duplicate pages count once`() {
        assertEquals(listOf(2, 8), starts(entry(2), entry(2), entry(8)))
    }

    @Test
    fun `levels below maxDepth are not used`() {
        val deep = entry(0, entry(0, entry(0, entry(10), entry(20))))
        assertEquals(listOf(0), starts(deep))
    }

    @Test
    fun `no outline means no chapters`() {
        assertEquals(emptyList<Int>(), starts())
    }
}
