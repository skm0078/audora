// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/text/OdtParserTest.kt
// Purpose: An OpenDocument text's headings, lists, tables and notes come out in reading order, markup left out.

package io.github.skm0078.audora.playback.text

import io.github.skm0078.audora.core.result.Outcome
import org.junit.Assert.assertEquals
import org.junit.Test

class OdtParserTest {
    private val content = """<?xml version="1.0" encoding="UTF-8"?>
        <office:document-content xmlns:office="o" xmlns:text="t" xmlns:table="tb"><office:body><office:text>
        <text:sequence-decls><text:sequence-decl text:name="Figure"/></text:sequence-decls>
        <text:p text:style-name="Title">গীতাঞ্জলি</text:p>
        <text:h text:outline-level="1">First song</text:h>
        <text:p>Where the mind<text:s text:c="2"/>is without fear<text:note text:note-class="footnote">
          <text:note-citation>1</text:note-citation><text:note-body><text:p>A note.</text:p></text:note-body>
          </text:note>.</text:p>
        <text:p><office:annotation><text:p>Fix this</text:p></office:annotation>Kept.</text:p>
        <text:h text:outline-level="2">Lines</text:h>
        <text:list><text:list-item><text:p>One</text:p></text:list-item><text:list-item><text:p>Two</text:p>
          </text:list-item></text:list>
        <table:table><table:table-row><table:table-cell><text:p>A</text:p></table:table-cell>
          <table:table-cell><text:p>B</text:p></table:table-cell></table:table-row></table:table>
        </office:text></office:body></office:document-content>"""

    @Test
    fun `reads headings, lists, rows and notes, not citations or comments`() {
        val result = OdtParser.parse(
            mapOf(
                "content.xml" to content.toByteArray(),
                "meta.xml" to "<office:meta><dc:title>Songs</dc:title></office:meta>".toByteArray(),
            ),
        )
        val doc = (result as Outcome.Success).value
        assertEquals("Songs", doc.title)
        assertEquals(listOf("গীতাঞ্জলি", "First song"), doc.chapters.map { it.title })
        assertEquals(
            listOf(
                BlockKind.HEADING to "গীতাঞ্জলি",
                BlockKind.HEADING to "First song",
                BlockKind.BODY to "Where the mind is without fear .",
                BlockKind.BODY to "Kept.",
                BlockKind.HEADING to "Lines",
                BlockKind.LIST_ITEM to "One",
                BlockKind.LIST_ITEM to "Two",
                BlockKind.BODY to "A; B",
                BlockKind.BODY to "A note.",
            ),
            doc.chapters.flatMap { it.blocks }.map { it.kind to it.text },
        )
    }

    @Test
    fun `a file without content is refused`() {
        assertEquals(true, OdtParser.parse(emptyMap()) is Outcome.Failure)
    }
}
