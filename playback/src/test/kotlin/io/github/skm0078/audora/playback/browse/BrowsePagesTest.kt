// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/browse/BrowsePagesTest.kt
// Purpose: Browse paging: pages, the last short page, past the end, and page numbers that overflow an Int.

package io.github.skm0078.audora.playback.browse

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowsePagesTest {
    private val rows = (1..5).toList()

    @Test
    fun `pages split the rows in order`() {
        assertEquals(listOf(1, 2), BrowsePages.of(rows, 0, 2))
        assertEquals(listOf(3, 4), BrowsePages.of(rows, 1, 2))
        assertEquals(listOf(5), BrowsePages.of(rows, 2, 2))
    }

    @Test
    fun `a page past the end is empty`() {
        assertEquals(emptyList<Int>(), BrowsePages.of(rows, 3, 2))
    }

    @Test
    fun `a page number that overflows an Int is empty, not the first rows`() {
        assertEquals(emptyList<Int>(), BrowsePages.of(rows, Int.MAX_VALUE, 2))
        assertEquals(emptyList<Int>(), BrowsePages.of(rows, Int.MAX_VALUE / 2 + 1, 2))
    }

    @Test
    fun `negative page and size are treated as the first page of one row`() {
        assertEquals(listOf(1), BrowsePages.of(rows, -1, 0))
    }
}
