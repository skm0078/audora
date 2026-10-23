// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/ReaderNavigationTest.kt
// Purpose: Getting around a book: chapter list (item 11), time left at your speed (item 24), undo jump (item 78).

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.pdf.document.OutlineEntry
import io.github.skm0078.audora.pdf.outline.ChapterEntry
import io.github.skm0078.audora.playback.contract.StudyAction
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
class ReaderNavigationTest {
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
    fun `undo jump is offered for this book only and goes to the player`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        port.connect()
        model.undoJump()
        assertTrue("nothing to undo", port.calls.isEmpty())
        port.state.value = port.state.value.copy(undoJumpTo = ReadingPosition(BookId("another-book"), 3, 0))
        assertNull(model.state.value.undoJumpTo)
        port.state.value = port.state.value.copy(undoJumpTo = ReadingPosition(BOOK, 3, 0))
        assertEquals(ReadingPosition(BOOK, 3, 0), model.state.value.undoJumpTo)
        model.undoJump()
        assertEquals(listOf("undo jump"), port.calls)
    }

    @Test
    fun `what did it say asks the service for a one-shot slower replay, never a saved speed`() =
        runTest(UnconfinedTestDispatcher()) {
            val model = model()
            model.replayPrevious()
            assertTrue("not before the player is there", port.calls.isEmpty())

            port.connect()
            model.replayPrevious()

            assertEquals(listOf("replay ${ReaderConfig().replayFactor}"), port.calls)
        }

    @Test
    fun `paragraph skips go to the service once the player is there`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        model.skipParagraph(forward = true)
        assertTrue("not before the player is there", port.calls.isEmpty())

        port.connect()
        model.skipParagraph(forward = true)
        model.skipParagraph(forward = false)

        assertEquals(listOf("paragraph forward=true", "paragraph forward=false"), port.calls)
    }

    @Test
    fun `A and B are set only while this book is read, and clearing always goes through`() =
        runTest(UnconfinedTestDispatcher()) {
            val model = model()
            port.connect()
            model.study(StudyAction.MARK_START)
            assertTrue("nothing is being read", port.calls.isEmpty())

            port.readingAt(ReadingPosition(BOOK, 2, 0))
            model.study(StudyAction.MARK_START)
            model.study(StudyAction.MARK_END, loop = false)
            model.study(StudyAction.CLEAR)

            assertEquals(
                listOf("study MARK_START loop=true", "study MARK_END loop=false", "study CLEAR loop=true"),
                port.calls,
            )
        }

    @Test
    fun `the range shown is this book's only, and a selection is read through the player`() =
        runTest(UnconfinedTestDispatcher()) {
            val model = model()
            port.connect()
            val otherBook = BookId("another-book")
            val elsewhere = StudyRangeState(ReadingPosition(otherBook, 1, 0), end = null, loop = true)
            port.state.value = port.state.value.copy(study = elsewhere)
            assertNull(model.state.value.study)

            model.readRange(ReadingPosition(BOOK, 3, 15), ReadingPosition(BOOK, 3, 80), loop = false)

            assertEquals(listOf("range 3:15..3:80 loop=false"), port.calls)
        }

    @Test
    fun `a book without an outline has no chapters and no chapter time`() = runTest(UnconfinedTestDispatcher()) {
        val model = model(FakeDocument(pageCount = 10))
        assertTrue(model.state.value.chapters.isEmpty())
        assertNull(model.state.value.currentChapter)
        assertNull(requireNotNull(model.state.value.timeLeft).chapterMs)
    }
}
