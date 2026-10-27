// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/djvu/DjvuPageTextTest.kt
// Purpose: A DjVu page's hidden text becomes headings and paragraphs: by its paragraph zones, or by line spacing
//          when it has none; page numbers dropped, broken words joined, flat text split at DjVu's separators.

package io.github.skm0078.audora.playback.djvu

import io.github.skm0078.audora.playback.text.BlockKind
import io.github.skm0078.audora.playback.text.TextBlock
import org.junit.Assert.assertEquals
import org.junit.Test

class DjvuPageTextTest {
    private fun blocks(name: String) = DjvuPageText.blocks(DjvuExpressions.parse(DjvuFixtures.text(name)))

    @Test
    fun `paragraph zones become blocks, a large single line a heading, and a broken word is joined`() {
        assertEquals(
            listOf(
                TextBlock(BlockKind.HEADING, "Chapter One"),
                TextBlock(
                    BlockKind.BODY,
                    "The first page of a small DjVu book has a remarkable sentence that is split by a hyphen at the " +
                        "line end.",
                ),
                TextBlock(BlockKind.BODY, "A second paragraph follows the first one."),
            ),
            blocks("text-page1.sexp"),
        )
    }

    @Test
    fun `without paragraph zones, lines are joined by their spacing and the page number is dropped`() {
        assertEquals(
            listOf(
                TextBlock(BlockKind.BODY, "Chapter Two starts on the second page."),
                TextBlock(
                    BlockKind.BODY,
                    "Its lines carry no paragraph marks, so the reader joins them by their spacing.",
                ),
            ),
            blocks("text-page2.sexp"),
        )
    }

    @Test
    fun `Hindi text keeps its letters`() {
        assertEquals(listOf(TextBlock(BlockKind.BODY, "मन जहाँ डर से परे है।")), blocks("text-page3.sexp"))
    }

    @Test
    fun `character zones run together into words`() {
        val page = DjvuExpressions.parse(
            """(page 0 0 100 100 (line 0 50 90 60 (word 0 50 40 60 (char 0 50 20 60 "H") (char 20 50 40 60 "i"))
              (word 50 50 90 60 "there")))""",
        )
        assertEquals(listOf(TextBlock(BlockKind.BODY, "Hi there")), DjvuPageText.blocks(page))
    }

    @Test
    fun `a flat page string is split at blank lines and DjVu's separators`() {
        val page = DjvuExpressions.parse("""(page 0 0 100 100 "First line\nwraps here.\n\nNext one.\037Last.\n12")""")
        assertEquals(
            listOf("First line wraps here.", "Next one.", "Last."),
            DjvuPageText.blocks(page).map { it.text },
        )
    }

    @Test
    fun `a page with no text layer has no blocks`() {
        assertEquals(emptyList<TextBlock>(), DjvuPageText.blocks(DjvuExpressions.parse("")))
        assertEquals(emptyList<TextBlock>(), DjvuPageText.blocks(DjvuExpressions.parse("(page 0 0 850 1100)")))
        assertEquals(emptyList<TextBlock>(), DjvuPageText.blocks(null))
    }
}
