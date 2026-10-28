// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/PageFlipModelTest.kt
// Purpose: Turning PDF pages by hand (ADR 2026-10-28-page-flip): the pages beside the shown one are drawn and read
//          ahead, a turn to one shows it at once, memory stays at three pages, zoom and the text view draw nothing
//          ahead, and edge taps turn.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.config.PageFlipConfig
import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.pdf.render.ViewPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PageFlipModelTest {
    private val port = FakePlaybackPort()
    private val document = FakeDocument(pageCount = PAGES, unitsOnPage = ::boxedUnitsOf)

    private fun TestScope.model(flip: PageFlipConfig = PageFlipConfig()) = ReaderModel(
        bookId = BOOK,
        books = FakeBooks(),
        opener = FakeOpener(document),
        player = port,
        speeds = FakeSpeeds(),
        config = ReaderConfig(flip = flip),
        scope = backgroundScope,
    ).also {
        it.start()
        it.setPageWidth(WIDTH)
    }

    private val ReaderModel.shownPage get() = (state.value.pageImage as PageImageState.Ready).page

    @Test
    fun `the pages beside the shown one are drawn ahead, after it`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        model.showPage(PAGE)

        assertEquals(PAGE, model.shownPage)
        assertEquals(setOf(PAGE - 1, PAGE, PAGE + 1), model.state.value.nearbyPages.keys)
        val afterTurn = document.renders.map { it.page }.dropWhile { it != PAGE }
        // The shown page first, then the next, then the one before.
        assertEquals(listOf(PAGE, PAGE + 1, PAGE - 1), afterTurn)
        assertTrue("all at the page view's width", document.renders.all { it.widthPx == WIDTH })
    }

    @Test
    fun `a turn to a page drawn ahead shows it at once, without drawing or reading it again`() =
        runTest(UnconfinedTestDispatcher()) {
            val model = model()
            val drawn = document.renders.count { it.page == 1 }
            val read = document.reads.count { it == 1 }

            model.nextPage()

            assertEquals(1, model.shownPage)
            assertTrue(model.state.value.content is PageContent.Ready)
            assertEquals("not drawn again", drawn, document.renders.count { it.page == 1 })
            assertEquals("not read again", read, document.reads.count { it == 1 })
        }

    @Test
    fun `no more than three pages are ever held`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        listOf(1, 2, 3, PAGE, 2, PAGES - 1).forEach { page ->
            model.showPage(page)
            val held = model.state.value.nearbyPages.keys
            assertTrue("page $page held $held", held.size <= MAX_HELD && held.all { it in page - 1..page + 1 })
        }
        assertEquals("the last page has no next", setOf(PAGES - 2, PAGES - 1), model.state.value.nearbyPages.keys)
    }

    @Test
    fun `zoomed in, nothing is drawn ahead, and back at the page's width it is again`() =
        runTest(UnconfinedTestDispatcher()) {
            val model = model()
            model.setZoomed(true)
            assertTrue("the drawn-ahead pages are let go", model.state.value.nearbyPages.isEmpty())

            model.setPageWidth(ZOOMED_WIDTH)
            assertEquals(listOf(0), document.renders.filter { it.widthPx == ZOOMED_WIDTH }.map { it.page })
            assertTrue(model.state.value.nearbyPages.isEmpty())

            model.setPageWidth(WIDTH)
            model.setZoomed(false)
            assertEquals(setOf(0, 1), model.state.value.nearbyPages.keys)
        }

    @Test
    fun `a new width lets go of pages drawn at the old one`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        model.setPageWidth(ROTATED_WIDTH)
        val widths = document.renders.filter { it.page == 1 }.map { it.widthPx }
        assertEquals("page 1 drawn again at the new width", listOf(WIDTH, ROTATED_WIDTH), widths)
    }

    @Test
    fun `the text view holds no drawn pages`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        model.setView(ReaderView.TEXT)
        assertTrue(model.state.value.nearbyPages.isEmpty())
    }

    @Test
    fun `the lighter setting draws only the next page`() = runTest(UnconfinedTestDispatcher()) {
        val model = model(PageFlipConfig(drawPrevious = false))
        model.showPage(PAGE)
        assertEquals(setOf(PAGE, PAGE + 1), model.state.value.nearbyPages.keys)
    }

    @Test
    fun `a tap in the page's outer edge turns it, in the middle or zoomed it does not`() =
        runTest(UnconfinedTestDispatcher()) {
            val model = model()
            assertTrue(model.turnAtEdge(ViewPoint(RIGHT_EDGE, MIDDLE)))
            assertEquals(1, model.state.value.page)
            assertTrue(model.turnAtEdge(ViewPoint(LEFT_EDGE, MIDDLE)))
            assertEquals(0, model.state.value.page)

            assertFalse(model.turnAtEdge(ViewPoint(MIDDLE, MIDDLE)))
            model.setZoomed(true)
            assertFalse(model.turnAtEdge(ViewPoint(RIGHT_EDGE, MIDDLE)))
            assertEquals(0, model.state.value.page)
        }

    private companion object {
        const val PAGES = 10
        const val PAGE = 4
        const val MAX_HELD = 3
        const val WIDTH = 1080
        const val ZOOMED_WIDTH = 2160
        const val ROTATED_WIDTH = 2400
        const val LEFT_EDGE = 0.05f
        const val RIGHT_EDGE = 0.95f
        const val MIDDLE = 0.5f
    }
}
