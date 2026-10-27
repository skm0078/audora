// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/comic/ComicPagesTest.kt
// Purpose: A comic archive's pages are its pictures in natural reading order, without Mac and hidden junk
//          (ADR 2026-10-26-more-ebook-formats).

package io.github.skm0078.audora.playback.comic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComicPagesTest {
    @Test
    fun `pages sort as numbers, not as text`() {
        val names = listOf("page10.jpg", "page2.jpg", "page1.jpg", "page002b.png", "Page3.JPG")

        assertEquals(
            listOf("page1.jpg", "page2.jpg", "page002b.png", "Page3.JPG", "page10.jpg"),
            ComicPages.order(names),
        )
    }

    @Test
    fun `chapters in folders keep their own order`() {
        val names = listOf("ch10/01.png", "ch2/02.png", "ch2/01.png", "ch1/01.png")

        assertEquals(listOf("ch1/01.png", "ch2/01.png", "ch2/02.png", "ch10/01.png"), ComicPages.order(names))
    }

    @Test
    fun `junk entries are not pages`() {
        val junk = listOf("__MACOSX/._01.jpg", "art/._01.jpg", ".cover.jpg", "ComicInfo.xml", "notes.txt", "art/")

        assertEquals(listOf("art/01.webp"), ComicPages.order(junk + "art/01.webp"))
        junk.forEach { assertFalse(it, ComicPages.isPage(it)) }
        assertTrue(ComicPages.isPage("art/01.webp"))
    }
}
