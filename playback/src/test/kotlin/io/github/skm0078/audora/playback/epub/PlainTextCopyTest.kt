// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/epub/PlainTextCopyTest.kt
// Purpose: The plain-text copy's layout: title, headings, page marks, bullets, table rows and notes.

package io.github.skm0078.audora.playback.epub

import io.github.skm0078.audora.core.model.UnitKind
import io.github.skm0078.audora.playback.export.AudioChapter
import org.junit.Assert.assertEquals
import org.junit.Test

class PlainTextCopyTest {
    @Test
    fun `a chapter reads as plain text`() {
        val doc = DocumentBuilder { "Pages" }.build(
            "Book",
            listOf(AudioChapter(1, 0, 0)),
            mapOf(
                0 to listOf(
                    sentence(0, 0, "Start", UnitKind.HEADING, PARAGRAPH_END),
                    sentence(0, 1, "One."),
                    sentence(0, 2, "Two.", pause = PARAGRAPH_END),
                    sentence(0, 3, "• Item", UnitKind.LIST_ITEM, PARAGRAPH_END),
                    tableUnit(0, 4, null, "Name", "Age"),
                    tableUnit(0, 5, 0, "Ram", "34"),
                    note(0, 6, "1 Note.", 1),
                ),
            ),
        )!!

        val expected = """
            Book
            ====

            [Page 1]

            Start
            -----

            One. Two.

            • Item

            Name | Age
            Ram | 34

            Notes
            -----

            1 Note.
        """.trimIndent() + "\n"
        assertEquals(expected, PlainTextCopy().write(doc))
    }
}
