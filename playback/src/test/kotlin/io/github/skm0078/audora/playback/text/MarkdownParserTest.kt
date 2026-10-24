// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/text/MarkdownParserTest.kt
// Purpose: Markdown is told from prose, and is read without its symbols: headings, lists, tables, links, code.

package io.github.skm0078.audora.playback.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {
    @Test
    fun `Markdown is told from prose`() {
        assertTrue(MarkdownParser.looksLikeMarkdown("# Notes\n\nSome text."))
        assertTrue(MarkdownParser.looksLikeMarkdown("Use **this** and **that** carefully."))
        assertTrue(MarkdownParser.looksLikeMarkdown("| a | b |\n|---|---|\n| 1 | 2 |"))
        assertFalse(MarkdownParser.looksLikeMarkdown("Chapter 1\n\nIt was the #1 song. 5 * 3 = 15.\n- a list"))
        assertFalse(MarkdownParser.looksLikeMarkdown("See [the note] below (page 4)."))
    }

    @Test
    fun `reads headings, lists, quotes, tables and code without markup`() {
        val text = """---
            |title: Ignored
            |---
            |# My *notes*
            |
            |Read [the guide](https://example.com) and ![a map](map.png) **today**, not `tomorrow`.
            |A second line_with_underscores.
            |
            |## Shopping
            |- [ ] Milk
            |- Bread with
            |  butter
            |1. First
            |
            |> Quoted words
            |
            || Item | Price |
            ||------|------:|
            || Tea  | ₹50   |
            |
            |```kotlin
            |val x = 1
            |```
            |
            |Part two
            |========
            |
            |[guide]: https://example.com
            |---
            |मन भय से मुक्त हो।
            |
        """.trimMargin()
        val doc = MarkdownParser.parse(text)
        assertEquals(listOf("My notes", "Part two"), doc.chapters.map { it.title })
        assertEquals(
            listOf(
                BlockKind.HEADING to "My notes",
                BlockKind.BODY to "Read the guide and a map today, not tomorrow. A second line_with_underscores.",
                BlockKind.HEADING to "Shopping",
                BlockKind.LIST_ITEM to "Milk",
                BlockKind.LIST_ITEM to "Bread with butter",
                BlockKind.LIST_ITEM to "First",
                BlockKind.BODY to "Quoted words",
                BlockKind.BODY to "Item; Price",
                BlockKind.BODY to "Tea; ₹50",
                BlockKind.BODY to "val x = 1",
                BlockKind.HEADING to "Part two",
                BlockKind.BODY to "मन भय से मुक्त हो।",
            ),
            doc.chapters.flatMap { it.blocks }.map { it.kind to it.text },
        )
    }
}
