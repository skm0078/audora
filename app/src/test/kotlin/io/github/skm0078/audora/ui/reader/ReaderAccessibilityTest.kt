// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/ReaderAccessibilityTest.kt
// Purpose: TalkBack's page scrubber turns one page per swipe, its sentence actions name each sentence by its first
//          words, and the sleep timer's time left is said in whole minutes.

package io.github.skm0078.audora.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderAccessibilityTest {
    @Test
    fun `a swipe on the scrubber turns one page, whatever step TalkBack asks for`() {
        assertEquals(41, scrubberStep(shown = 40, target = 60f, pageCount = 400))
        assertEquals(39, scrubberStep(shown = 40, target = 20f, pageCount = 400))
        assertEquals(40, scrubberStep(shown = 40, target = 40f, pageCount = 400))
    }

    @Test
    fun `the scrubber stays inside the book`() {
        assertEquals(399, scrubberStep(shown = 399, target = 420f, pageCount = 400))
        assertEquals(0, scrubberStep(shown = 0, target = -20f, pageCount = 400))
    }

    @Test
    fun `a sentence is named by its first six words`() {
        assertEquals("Call me Ishmael.", sentencePreview("  Call me\nIshmael. "))
        assertEquals(
            "Some years ago, never mind how…",
            sentencePreview("Some years ago, never mind how long precisely."),
        )
        assertEquals(
            "सभी मनुष्यों को गौरव और अधिकारों…",
            sentencePreview("सभी मनुष्यों को गौरव और अधिकारों के मामले में"),
        )
    }

    @Test
    fun `the sleep timer is said in whole minutes, rounded up, never zero`() {
        assertEquals(15, minutesLeft(15 * 60_000L))
        assertEquals(15, minutesLeft(14 * 60_000L + 1))
        assertEquals(1, minutesLeft(59_000L))
        assertEquals(1, minutesLeft(0L))
        assertEquals(1, minutesLeft(-5_000L))
        assertEquals(75, minutesLeft(75 * 60_000L))
    }
}
