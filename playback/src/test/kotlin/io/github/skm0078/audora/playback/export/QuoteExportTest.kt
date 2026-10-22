// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/export/QuoteExportTest.kt
// Purpose: Highlights, bookmarks and voice notes exported as Markdown and plain text.

package io.github.skm0078.audora.playback.export

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.notes.Bookmark
import io.github.skm0078.audora.core.notes.Highlight
import io.github.skm0078.audora.core.notes.VoiceNote
import io.github.skm0078.audora.core.resume.ResumeAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuoteExportTest {
    private fun anchor(page: Int) = ResumeAnchor(BookId("b"), page, 0, 1L, 2L, 1, 0L)

    private val marks = BookMarks(
        title = "Gitanjali",
        highlights = listOf(Highlight(1, anchor(4), 4, 30, "Where the mind is without fear", note = "Favourite")),
        bookmarks = listOf(Bookmark(2, anchor(9), "Come back"), Bookmark(3, anchor(11))),
        voiceNotes = listOf(VoiceNote(4, anchor(12), "4.m4a", 75_000)),
    )

    @Test
    fun `markdown quotes, cites pages and lists the rest`() {
        val expected = """
            # Gitanjali

            ## Highlights

            > Where the mind is without fear
            >
            > — Page 5

            **Note:** Favourite

            ## Bookmarks

            - Page 10: Come back
            - Page 12

            ## Voice notes

            - Page 13: 1:15
        """.trimIndent() + "\n"
        assertEquals(expected, QuoteExport().markdown(marks))
    }

    @Test
    fun `plain text keeps the same content without markup`() {
        val text = QuoteExport().text(marks)
        val opening = "Gitanjali\n=========\n\nHighlights\n\n“Where the mind is without fear” (Page 5)\n"
        assertTrue(text.startsWith(opening))
        assertTrue(text.contains("• Page 10: Come back\n• Page 12\n"))
        assertTrue(text.endsWith("• Page 13: 1:15\n"))
    }
}
