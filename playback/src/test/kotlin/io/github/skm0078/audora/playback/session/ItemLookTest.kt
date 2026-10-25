// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/session/ItemLookTest.kt
// Purpose: The notification's second line: author, chapter and page, leaving out what the book does not have.

package io.github.skm0078.audora.playback.session

import org.junit.Assert.assertEquals
import org.junit.Test

class ItemLookTest {
    private val texts = object : ItemTexts {
        override fun pageOf(page: Int, pageCount: Int) = "Page $page of $pageCount"

        override fun chapterOf(chapter: Int, chapterCount: Int) = "Chapter $chapter of $chapterCount"

        override fun loadingNextPage() = "…"
    }

    @Test
    fun `author, chapter and page, in that order`() {
        val look = ItemLook("Emma", author = "Jane Austen", chapterStarts = listOf(0, 10, 20))
        assertEquals("Jane Austen · Chapter 2 of 3 · Page 15 of 30", look.whereLine(14, 30, texts))
    }

    @Test
    fun `a page before the first chapter and a book without chapters show only the page`() {
        assertEquals("Page 1 of 30", ItemLook("Emma", chapterStarts = listOf(4, 10)).whereLine(0, 30, texts))
        assertEquals("Page 5 of 30", ItemLook("Emma").whereLine(4, 30, texts))
    }

    @Test
    fun `one chapter is not counted, and a blank author is left out`() {
        val look = ItemLook("Emma", author = " ", chapterStarts = listOf(0))
        assertEquals("Page 3 of 30", look.whereLine(2, 30, texts))
    }
}
