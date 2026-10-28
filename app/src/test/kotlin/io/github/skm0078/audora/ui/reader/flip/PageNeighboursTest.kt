// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/flip/PageNeighboursTest.kt
// Purpose: The pages held for a turn by hand: one width only, three pages at most, next first, sentences kept.

package io.github.skm0078.audora.ui.reader.flip

import io.github.skm0078.audora.core.config.PageFlipConfig
import io.github.skm0078.audora.ui.reader.FakePageImage
import io.github.skm0078.audora.ui.reader.PageContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageNeighboursTest {
    private val neighbours = PageNeighbours(PageFlipConfig())

    private fun image(page: Int) = FakePageImage(page, WIDTH, MAX_PIXELS)

    @Test
    fun `the next page is wanted first, and only pages in the book`() {
        assertEquals(listOf(5, 3), neighbours.wanted(4, PAGES))
        assertEquals(listOf(1), neighbours.wanted(0, PAGES))
        assertEquals(listOf(PAGES - 2), neighbours.wanted(PAGES - 1, PAGES))
        assertEquals(emptyList<Int>(), neighbours.wanted(0, 1))
    }

    @Test
    fun `an image is found only at the width it was drawn at`() {
        neighbours.keepImage(1, WIDTH, image(1))
        assertNotNull(neighbours.image(1, WIDTH))
        assertNull(neighbours.image(1, OTHER_WIDTH))

        neighbours.useWidth(OTHER_WIDTH)
        assertNull("a new width lets the old images go", neighbours.image(1, WIDTH))
    }

    @Test
    fun `only the page and its neighbours are kept`() {
        (0 until PAGES).forEach { neighbours.keepImage(it, WIDTH, image(it)) }
        (0 until PAGES).forEach { neighbours.keepContent(it, PageContent.Ready(emptyList())) }

        neighbours.keepAround(4, PAGES)

        assertEquals(setOf(3, 4, 5), neighbours.images().keys)
        assertNull(neighbours.content(2))
        assertNotNull(neighbours.content(5))
    }

    @Test
    fun `dropping images keeps sentences, clearing drops both`() {
        neighbours.keepImage(1, WIDTH, image(1))
        neighbours.keepContent(1, PageContent.Ready(emptyList()))

        neighbours.dropImages()
        assertTrue(neighbours.images().isEmpty())
        assertNotNull(neighbours.content(1))

        neighbours.clear()
        assertNull(neighbours.content(1))
    }

    @Test
    fun `with drawing the page before turned off, only the next is wanted`() {
        assertEquals(listOf(5), PageNeighbours(PageFlipConfig(drawPrevious = false)).wanted(4, PAGES))
    }

    private companion object {
        const val PAGES = 10
        const val WIDTH = 1080
        const val OTHER_WIDTH = 1920
        const val MAX_PIXELS = 4_000_000
    }
}
