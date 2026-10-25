// File: core/src/test/kotlin/io/github/skm0078/audora/core/library/BookTitlesTest.kt
// Purpose: Table-driven checks of how a file's display name becomes a library title.

package io.github.skm0078.audora.core.library

import org.junit.Assert.assertEquals
import org.junit.Test

class BookTitlesTest {
    @Test
    fun `display names become titles`() {
        val cases = listOf(
            "Moby Dick.pdf" to "Moby Dick",
            "REPORT.PDF" to "REPORT",
            "  spaced name .pdf  " to "spaced name",
            "no extension" to "no extension",
            "archive.pdf.zip" to "archive.pdf.zip",
            "Gitanjali.epub" to "Gitanjali",
            "notes.TXT" to "notes",
            "Thesis final.docx" to "Thesis final",
            "Letter.odt" to "Letter",
            "Story.rtf" to "Story",
            "Saved page.html" to "Saved page",
            "Saved page.htm" to "Saved page",
            "README.md" to "README",
            "गीता.pdf" to "गीता",
            "রবীন্দ্র_রচনা.pdf" to "রবীন্দ্র_রচনা",
            ".pdf" to BookTitles.UNTITLED,
            "   " to BookTitles.UNTITLED,
            null to BookTitles.UNTITLED,
        )
        for ((input, expected) in cases) {
            assertEquals("input=$input", expected, BookTitles.fromDisplayName(input))
        }
    }
}
