// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/ReaderResumeTest.kt
// Purpose: The reader opens where the book was left, Play continues there, and "Resume" after a crash starts once.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.resume.AnchorMatch
import io.github.skm0078.audora.core.resume.ResumePoint
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
class ReaderResumeTest {
    private val port = FakePlaybackPort()
    private val document = FakeDocument(pageCount = 10)
    private val pagesAsked = mutableListOf<Int>()

    /** The book was left on page 7, at its third sentence. */
    private val savedOnPage7 = ReaderResume { _, pageCount, unitsOf ->
        assertEquals(10, pageCount)
        pagesAsked += 7
        val unit = requireNotNull(unitsOf(7))[2]
        ResumePoint(unit.page, unit.charStart, AnchorMatch.EXACT)
    }

    private fun TestScope.model(
        resume: ReaderResume = savedOnPage7,
        autoResume: Boolean = false,
        shownPage: Int? = null,
    ) = ReaderModel(
        BOOK,
        FakeBooks(),
        FakeOpener(document),
        port,
        FakeSpeeds(),
        ReaderConfig(),
        backgroundScope,
        resume,
        autoResume,
        shownPage = shownPage,
    ).also { it.start() }

    @Test
    fun `a book left mid-way opens on that page`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()

        assertEquals(7, model.state.value.page)
        assertEquals(listOf(7), pagesAsked)
    }

    @Test
    fun `a fresh open says where it resumed`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()

        assertNotNull(model.state.value.resumedAt)
    }

    @Test
    fun `a rebuilt screen shows the page that was on screen, with no second resume notice`() =
        runTest(UnconfinedTestDispatcher()) {
            val model = model(shownPage = 3)

            assertEquals(3, model.state.value.page)
            assertNull(model.state.value.resumedAt)
        }

    @Test
    fun `after a rebuild, play on the resume page still continues at the saved sentence`() =
        runTest(UnconfinedTestDispatcher()) {
            val model = model(shownPage = 7)
            port.connect()

            model.playOrPause()

            assertEquals(listOf(ReadingPosition(BOOK, 7, 40) to null), port.starts)
        }

    @Test
    fun `play on that page continues at the saved sentence`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        port.connect()

        model.playOrPause()

        assertEquals(listOf(ReadingPosition(BOOK, 7, 40) to null), port.starts)
    }

    @Test
    fun `play on another page still starts at the top of that page`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        port.connect()

        model.showPage(2)
        model.playOrPause()

        assertEquals(listOf(ReadingPosition(BOOK, 2, 0) to null), port.starts)
    }

    @Test
    fun `a book already in the player ignores the saved position`() = runTest(UnconfinedTestDispatcher()) {
        port.connect()
        port.readingAt(ReadingPosition(BOOK, 4, 20))

        val model = model()

        assertEquals(4, model.state.value.page)
        assertTrue(pagesAsked.isEmpty())
    }

    @Test
    fun `a book never read opens on page 1`() = runTest(UnconfinedTestDispatcher()) {
        val model = model(resume = ReaderResume.None)

        assertEquals(0, model.state.value.page)
    }

    @Test
    fun `resume after a crash starts reading once the player connects, only once`() =
        runTest(UnconfinedTestDispatcher()) {
            model(autoResume = true)
            assertTrue("nothing before the player is there", port.starts.isEmpty())

            port.connect()
            port.connect()

            assertEquals(listOf(ReadingPosition(BOOK, 7, 40) to null), port.starts)
        }

    @Test
    fun `without auto resume nothing starts by itself`() = runTest(UnconfinedTestDispatcher()) {
        model()
        port.connect()

        assertTrue(port.starts.isEmpty())
    }

    @Test
    fun `a resume asked while the reader is open starts reading once`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        port.connect()
        assertTrue(port.starts.isEmpty())

        model.requestAutoResume()
        port.connect()

        assertEquals(listOf(ReadingPosition(BOOK, 7, 40) to null), port.starts)
    }

    @Test
    fun `a resume asked before the player connects waits for it`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()

        model.requestAutoResume()
        assertTrue(port.starts.isEmpty())
        port.connect()

        assertEquals(listOf(ReadingPosition(BOOK, 7, 40) to null), port.starts)
    }
}
