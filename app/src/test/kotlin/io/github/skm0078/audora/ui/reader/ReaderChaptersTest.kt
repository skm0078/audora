// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/ReaderChaptersTest.kt
// Purpose: The reader's chapter list (plan item 11) and time left at the listener's speed (plan item 24).

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.pdf.document.OutlineEntry
import io.github.skm0078.audora.pdf.outline.ChapterEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderChaptersTest {
    private val port = FakePlaybackPort()
    private val outline = listOf(OutlineEntry("One", 0), OutlineEntry("Two", 4), OutlineEntry("Three", 8))

    private fun TestScope.model(document: FakeDocument = FakeDocument(pageCount = 10, outline = outline)) =
        ReaderModel(BOOK, FakeBooks(), FakeOpener(document), port, FakeSpeeds(), ReaderConfig(), backgroundScope)
            .also { it.start() }

    @Test
    fun `the outline becomes the chapter list, with the chapter being shown marked`() =
        runTest(UnconfinedTestDispatcher()) {
            val model = model()
            assertEquals(listOf("One", "Two", "Three"), model.state.value.chapters.map(ChapterEntry::title))
            assertEquals(listOf(0, 4, 8), model.state.value.chapterStarts)
            assertEquals(0, model.state.value.currentChapter)
            model.showPage(5)
            assertEquals(1, model.state.value.currentChapter)
        }

    @Test
    fun `a chapter is shown when idle and read from while reading`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        port.connect()
        model.goToChapter(model.state.value.chapters[1])
        assertEquals(4, model.state.value.page)
        assertTrue(port.starts.isEmpty())

        port.readingAt(ReadingPosition(BOOK, 4, 0))
        model.goToChapter(model.state.value.chapters[2])
        assertEquals(listOf(ReadingPosition(BOOK, 8, 0) to null), port.starts)
    }

    @Test
    fun `time left covers the book and the chapter, shrinking as reading goes on`() =
        runTest(UnconfinedTestDispatcher()) {
            val model = model()
            port.connect()
            val atStart = requireNotNull(model.state.value.timeLeft)
            val chapter = requireNotNull(atStart.chapterMs)
            assertTrue("a chapter is shorter than the book", chapter < atStart.bookMs)

            port.readingAt(ReadingPosition(BOOK, 6, 0))
            val later = requireNotNull(model.state.value.timeLeft)
            assertTrue(later.bookMs < atStart.bookMs)
            assertNotNull(later.chapterMs)
        }

    @Test
    fun `a book without an outline has no chapters and no chapter time`() = runTest(UnconfinedTestDispatcher()) {
        val model = model(FakeDocument(pageCount = 10))
        assertTrue(model.state.value.chapters.isEmpty())
        assertNull(model.state.value.currentChapter)
        assertNull(requireNotNull(model.state.value.timeLeft).chapterMs)
    }
}
