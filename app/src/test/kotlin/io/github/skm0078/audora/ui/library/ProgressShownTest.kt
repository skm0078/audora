// File: app/src/test/kotlin/io/github/skm0078/audora/ui/library/ProgressShownTest.kt
// Purpose: The library redraws for a new page or a new "Continue reading" book, not for every sentence saved.

package io.github.skm0078.audora.ui.library

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.resume.ResumeAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ProgressShownTest {
    private fun anchor(book: String, page: Int, char: Int, savedAt: Long) =
        ResumeAnchor(BookId(book), page, char, 1L, 2L, 1, savedAt)

    private val reading = listOf(anchor("moby", 4, 100, 1_000), anchor("emma", 9, 0, 500))

    @Test
    fun `a later sentence on the same page shows the same`() {
        val nextSentence = listOf(anchor("moby", 4, 180, 3_000), anchor("emma", 9, 0, 500))

        assertEquals(progressShown(reading), progressShown(nextSentence))
    }

    @Test
    fun `a new page shows`() {
        val nextPage = listOf(anchor("moby", 5, 0, 3_000), anchor("emma", 9, 0, 500))

        assertNotEquals(progressShown(reading), progressShown(nextPage))
    }

    @Test
    fun `another book becoming the last read shows, even on its old page`() {
        val switched = listOf(anchor("moby", 4, 100, 1_000), anchor("emma", 9, 40, 5_000))

        assertNotEquals(progressShown(reading), progressShown(switched))
    }
}
