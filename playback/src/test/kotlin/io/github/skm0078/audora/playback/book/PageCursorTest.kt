// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/book/PageCursorTest.kt
// Purpose: Reading starts at the sentence containing the chosen offset and walks the following pages once.

package io.github.skm0078.audora.playback.book

import io.github.skm0078.audora.playback.page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageCursorTest {
    @Test
    fun walksFromTheStartPageToTheLast() {
        val cursor = PageCursor(pageCount = 4, startPage = 2, startChar = 0)
        assertEquals(2, cursor.nextPage())
        assertEquals(3, cursor.nextPage())
        assertNull(cursor.nextPage())
        assertNull(cursor.nextPage())
    }

    @Test
    fun theStartPageBeginsAtTheSentenceContainingTheOffset() {
        val cursor = PageCursor(pageCount = 3, startPage = 1, startChar = 25)
        // Sentences cover 0..9, 10..19, 20..29, 30..39: offset 25 is inside the third.
        assertEquals(listOf(2, 3), cursor.playable(1, page(1, 4)).map { it.index })
        assertEquals("other pages are whole", 4, cursor.playable(2, page(2, 4)).size)
    }

    @Test
    fun anOffsetPastTheLastSentenceSkipsThePage() {
        val cursor = PageCursor(pageCount = 3, startPage = 0, startChar = 1_000)
        assertEquals(emptyList<Any>(), cursor.playable(0, page(0, 4)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun aStartPageOutsideTheBookIsRefused() {
        PageCursor(pageCount = 3, startPage = 3, startChar = 0)
    }
}
