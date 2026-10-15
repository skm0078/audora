// File: core/src/test/kotlin/io/github/skm0078/audora/core/resume/ResumeLocatorTest.kt
// Purpose: A book's anchor becomes a start point; never-read books, shrunk documents and failed pages are handled.

package io.github.skm0078.audora.core.resume

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResumeLocatorTest {
    private val store = FakeResumeAnchorStore()
    private val fingerprint = SentenceFingerprint()
    private val locator = ResumeLocator(store, pipelineVersion = 1)
    private val page = unitsOf(7, "First of page.", "Second of page.")

    private suspend fun save(index: Int) {
        val unit = page[index]
        store.put(
            ResumeAnchor(
                BOOK,
                unit.page,
                unit.charStart,
                fingerprint.head(unit.text),
                fingerprint.tail(unit.text),
                1,
                0,
            ),
        )
    }

    @Test
    fun `a book never read has no resume point`() = runTest {
        assertNull(locator.locate(BOOK, pageCount = 10) { error("no page should be read") })
    }

    @Test
    fun `the saved sentence is found on its page`() = runTest {
        save(1)
        var asked: Int? = null

        val point = locator.locate(BOOK, pageCount = 10) {
            asked = it
            page
        }

        assertEquals(7, asked)
        assertEquals(ResumePoint(7, page[1].charStart, AnchorMatch.EXACT), point)
    }

    @Test
    fun `a document that lost the page resumes at the top of its last page`() = runTest {
        save(1)

        val point = locator.locate(BOOK, pageCount = 5) { error("page 7 does not exist") }

        assertEquals(ResumePoint(4, 0, AnchorMatch.PAGE_START), point)
    }

    @Test
    fun `a page that cannot be extracted resumes at its top`() = runTest {
        save(1)

        assertEquals(ResumePoint(7, 0, AnchorMatch.PAGE_START), locator.locate(BOOK, pageCount = 10) { null })
    }
}
