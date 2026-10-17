// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/browse/BookChaptersTest.kt
// Purpose: Chapters keep the sleep timer's pages and take the shallowest outline title that starts on each.

package io.github.skm0078.audora.playback.browse

import io.github.skm0078.audora.pdf.document.OutlineEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class BookChaptersTest {
    @Test
    fun `a wrapper entry is skipped and the chapters below it are titled`() {
        val outline = listOf(
            OutlineEntry(
                "The Book",
                0,
                listOf(OutlineEntry(" One ", 0), OutlineEntry("Two", 10), OutlineEntry("Three", 25)),
            ),
        )

        val chapters = BookChapters.from(outline, pageCount = 40, maxDepth = 3)

        // Page 0 is titled by the shallowest entry on it, the wrapper.
        assertEquals(listOf(BookChapter("The Book", 0), BookChapter("Two", 10), BookChapter("Three", 25)), chapters)
    }

    @Test
    fun `an entry without a title stays blank for the tree to number`() {
        val outline = listOf(OutlineEntry("", 2), OutlineEntry("Next", 5))

        assertEquals(listOf(BookChapter("", 2), BookChapter("Next", 5)), BookChapters.from(outline, 9, 3))
    }

    @Test
    fun `no outline, no chapters`() {
        assertEquals(emptyList<BookChapter>(), BookChapters.from(emptyList(), 9, 3))
    }
}
