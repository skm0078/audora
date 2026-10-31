// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/ReaderNoticeTest.kt
// Purpose: The reader shows one notice at a time, most urgent first, so cards never bury the book (UI audit).

package io.github.skm0078.audora.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderNoticeTest {
    @Test
    fun `a page that cannot be read beats every other notice`() {
        listOf(PageOrigin.NEEDS_OCR_MODELS, PageOrigin.OCR_FAILED).forEach { origin ->
            assertEquals(
                origin.name,
                ReaderNotice.PAGE_PROBLEM,
                readerNotice(origin, voicePackOffered = true, tipsPending = true),
            )
        }
    }

    @Test
    fun `the voice pack offer waits for nothing but a page problem`() {
        assertEquals(
            ReaderNotice.VOICE_PACK,
            readerNotice(PageOrigin.TEXT, voicePackOffered = true, tipsPending = true),
        )
        assertEquals(
            ReaderNotice.VOICE_PACK,
            readerNotice(PageOrigin.OCR, voicePackOffered = true, tipsPending = false),
        )
    }

    @Test
    fun `the tips show once nothing more urgent is up, even while the page loads`() {
        assertEquals(
            ReaderNotice.GESTURE_TIPS,
            readerNotice(PageOrigin.TEXT, voicePackOffered = false, tipsPending = true),
        )
        assertEquals(ReaderNotice.GESTURE_TIPS, readerNotice(null, voicePackOffered = false, tipsPending = true))
    }

    @Test
    fun `a first-use hint waits for every other card`() {
        assertEquals(
            ReaderNotice.GESTURE_TIPS,
            readerNotice(PageOrigin.TEXT, voicePackOffered = false, tipsPending = true, hintPending = true),
        )
        assertEquals(
            ReaderNotice.VOICE_PACK,
            readerNotice(PageOrigin.TEXT, voicePackOffered = true, tipsPending = false, hintPending = true),
        )
        assertEquals(
            ReaderNotice.HINT,
            readerNotice(PageOrigin.TEXT, voicePackOffered = false, tipsPending = false, hintPending = true),
        )
    }

    @Test
    fun `nothing to say shows nothing`() {
        assertNull(readerNotice(PageOrigin.TEXT, voicePackOffered = false, tipsPending = false))
        assertNull(readerNotice(null, voicePackOffered = false, tipsPending = false))
    }
}
