// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/ReaderPageViewTest.kt
// Purpose: Page view rules (P4-03): when pages are drawn, the spoken sentence's highlight, tap a line to read.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.pdf.render.ViewPoint
import io.github.skm0078.audora.pdf.render.ViewRect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderPageViewTest {
    private val port = FakePlaybackPort()
    private val document = FakeDocument(pageCount = 10, unitsOnPage = ::boxedUnitsOf, failingRenders = setOf(7))
    private val views = FakeViews()
    private val config = ReaderConfig()

    private fun TestScope.model() = ReaderModel(
        bookId = BOOK,
        books = FakeBooks(),
        opener = FakeOpener(document),
        player = port,
        speeds = FakeSpeeds(),
        config = config,
        scope = backgroundScope,
        views = views,
    ).also { it.start() }

    @Test
    fun `the page is drawn once the screen reports its width, at that width`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        assertEquals(PageImageState.None, model.state.value.pageImage)
        assertTrue(document.renders.isEmpty())

        model.setPageWidth(WIDTH)
        val image = model.state.value.pageImage as PageImageState.Ready
        assertEquals(0, image.page)
        val render = document.renders.first()
        assertEquals("the shown page is drawn first", 0, render.page)
        assertEquals(WIDTH, render.widthPx)
        assertEquals(config.pageMaxPixels, render.maxPixels)

        val drawn = document.renders.size
        model.setPageWidth(WIDTH)
        assertEquals("the same width draws nothing again", drawn, document.renders.size)
    }

    @Test
    fun `turning the page draws the new page`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        model.setPageWidth(WIDTH)
        model.nextPage()
        assertEquals(1, (model.state.value.pageImage as PageImageState.Ready).page)
        assertEquals("page 1 was drawn ahead, once", 1, document.renders.count { it.page == 1 })
    }

    @Test
    fun `a thumbnail is drawn at its own width and leaves the page view alone`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        model.setPageWidth(WIDTH)
        val shown = model.state.value.pageImage

        val thumb = model.thumbnail(3, THUMB_WIDTH) as FakePageImage

        assertEquals(3, thumb.page)
        assertEquals(THUMB_WIDTH, thumb.widthPx)
        assertEquals(config.pageMaxPixels, thumb.maxPixels)
        assertEquals(shown, model.state.value.pageImage)
        assertNull("a page that can't be drawn", model.thumbnail(7, THUMB_WIDTH))
        assertNull("no width", model.thumbnail(3, 0))
    }

    @Test
    fun `no thumbnail before the book is open`() = runTest(UnconfinedTestDispatcher()) {
        val closed = ReaderModel(
            bookId = BOOK,
            books = FakeBooks(),
            opener = FakeOpener(document),
            player = port,
            speeds = FakeSpeeds(),
            config = config,
            scope = backgroundScope,
            views = views,
        )
        assertNull(closed.thumbnail(0, THUMB_WIDTH))
    }

    @Test
    fun `a page that cannot be drawn says so`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        model.setPageWidth(WIDTH)
        model.showPage(7)
        assertEquals(PageImageState.Failed, model.state.value.pageImage)
    }

    @Test
    fun `the text view draws nothing and is remembered`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        model.setPageWidth(WIDTH)
        model.setView(ReaderView.TEXT)
        assertEquals(ReaderView.TEXT, model.state.value.view)
        assertEquals(PageImageState.None, model.state.value.pageImage)
        assertEquals(ReaderView.TEXT, views.saved)

        val drawn = document.renders.size
        model.nextPage()
        assertEquals("nothing drawn in the text view", drawn, document.renders.size)
        assertTrue("and nothing held for a swipe", model.state.value.nearbyPages.isEmpty())

        model.setView(ReaderView.PAGE)
        assertEquals(1, (model.state.value.pageImage as PageImageState.Ready).page)
        assertEquals(ReaderView.PAGE, views.saved)
    }

    @Test
    fun `a book opens in the view last chosen`() = runTest(UnconfinedTestDispatcher()) {
        views.saved = ReaderView.TEXT
        val model = model()
        model.setPageWidth(WIDTH)
        assertEquals(ReaderView.TEXT, model.state.value.view)
        assertTrue(document.renders.isEmpty())
    }

    @Test
    fun `the spoken sentence's line is highlighted on the page`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        model.setPageWidth(WIDTH)
        port.connect()
        assertEquals(emptyList<ViewRect>(), model.state.value.spokenHighlight)

        val spoken = boxedUnitsOf(0)[1]
        port.readingAt(ReadingPosition(BOOK, 0, spoken.charStart))
        val line = model.state.value.spokenHighlight.single()
        val box = requireNotNull(spoken.bounds).single()
        assertEquals(box.left / PAGE_WIDTH, line.left, EPSILON)
        assertEquals(box.right / PAGE_WIDTH, line.right, EPSILON)
        assertEquals(1f - box.top / PAGE_HEIGHT, line.top, EPSILON)
        assertEquals(1f - box.bottom / PAGE_HEIGHT, line.bottom, EPSILON)
    }

    @Test
    fun `nothing is highlighted while browsing another page`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        model.setPageWidth(WIDTH)
        port.connect()
        port.readingAt(ReadingPosition(BOOK, 0, boxedUnitsOf(0)[1].charStart))
        model.showPage(3)
        assertEquals(emptyList<ViewRect>(), model.state.value.spokenHighlight)
    }

    @Test
    fun `a tap on a line, or just beside it, picks its sentence`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        model.setPageWidth(WIDTH)
        val units = boxedUnitsOf(0)
        assertEquals(units[2], model.sentenceAt(viewPointOf(x = 300f, y = lineMiddle(units[2]))))
        // 3 pt above the first line's top: within the slop.
        val aboveFirst = requireNotNull(units[0].bounds).single().top + 3f
        assertEquals(units[0], model.sentenceAt(viewPointOf(x = 100f, y = aboveFirst)))
    }

    @Test
    fun `a tap in the margin, or in the text view, picks nothing`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        model.setPageWidth(WIDTH)
        assertNull(model.sentenceAt(viewPointOf(x = 300f, y = 100f)))

        model.setView(ReaderView.TEXT)
        assertNull(model.sentenceAt(viewPointOf(x = 300f, y = lineMiddle(boxedUnitsOf(0)[0]))))
    }

    private fun lineMiddle(unit: ReadingUnit): Float {
        val box = requireNotNull(unit.bounds).single()
        return (box.top + box.bottom) / 2f
    }

    /** Where page point ([x], [y]) shows on the fake (unrotated) page image. */
    private fun viewPointOf(x: Float, y: Float) = ViewPoint(x / PAGE_WIDTH, 1f - y / PAGE_HEIGHT)

    private companion object {
        const val WIDTH = 1080
        const val THUMB_WIDTH = 120
        const val EPSILON = 1e-5f
    }
}
