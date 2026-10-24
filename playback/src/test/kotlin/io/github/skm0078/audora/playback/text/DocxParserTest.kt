// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/text/DocxParserTest.kt
// Purpose: A Word document's chapters, headings, lists, tables, text boxes and notes come out in reading order.

package io.github.skm0078.audora.playback.text

import io.github.skm0078.audora.core.result.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxParserTest {
    private val styles = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
          <w:style w:type="paragraph" w:styleId="Titel"><w:name w:val="Title"/></w:style>
          <w:style w:type="paragraph" w:styleId="berschrift1"><w:name w:val="heading 1"/>
            <w:pPr><w:outlineLvl w:val="0"/></w:pPr></w:style>
          <w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/></w:style>
          <w:style w:type="paragraph" w:styleId="MyHeading"><w:name w:val="My heading"/>
            <w:basedOn w:val="Heading2"/></w:style>
          <w:style w:type="paragraph" w:styleId="ListParagraph"><w:name w:val="List Paragraph"/></w:style>
        </w:styles>"""

    private fun p(text: String, style: String? = null, extra: String = "") =
        "<w:p><w:pPr>${style?.let { "<w:pStyle w:val=\"$it\"/>" }.orEmpty()}$extra</w:pPr>" +
            "<w:r><w:t xml:space=\"preserve\">$text</w:t></w:r></w:p>"

    private val document = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <w:document xmlns:w="w" xmlns:mc="mc"><w:body>
        ${p("A Short Report", "Titel")}
        ${p("Written for the board.")}
        ${p("Chapter One", "berschrift1")}
        <w:p><w:r><w:t>Rain fell</w:t></w:r><w:r><w:tab/><w:t xml:space="preserve">all night</w:t></w:r>
          <w:del><w:r><w:delText>never</w:delText></w:r></w:del>
          <w:r><w:fldChar w:fldCharType="begin"/></w:r><w:r><w:instrText> PAGE </w:instrText></w:r>
          <w:r><w:t>.</w:t></w:r><w:r><w:footnoteReference w:id="2"/></w:r></w:p>
        ${p("Costs", "MyHeading")}
        ${p("Seeds", extra = "<w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"1\"/></w:numPr>")}
        ${p("Water", "ListParagraph")}
        <w:tbl><w:tr><w:tc>${p("Item")}</w:tc><w:tc>${p("₹500")}</w:tc></w:tr>
          <w:tr><w:tc>${p("Total")}</w:tc><w:tc>${p("")}</w:tc></w:tr></w:tbl>
        <w:p><w:r><mc:AlternateContent><mc:Choice><w:drawing><w:txbxContent>${p("In the box")}</w:txbxContent>
          </w:drawing></mc:Choice><mc:Fallback><w:pict><w:txbxContent>${p("In the box")}</w:txbxContent></w:pict>
          </mc:Fallback></mc:AlternateContent></w:r></w:p>
        ${p("मन भय से मुक्त हो।")}
        </w:body></w:document>"""

    private val footnotes = """<w:footnotes xmlns:w="w">
        <w:footnote w:type="separator" w:id="-1"><w:p><w:r><w:separator/></w:r></w:p></w:footnote>
        <w:footnote w:id="2">${p("Measured at the farm.")}</w:footnote></w:footnotes>"""

    private val core = """<cp:coreProperties xmlns:dc="dc"><dc:title>Board report</dc:title></cp:coreProperties>"""

    private fun parse(entries: Map<String, String>): TextDocument {
        val result = DocxParser.parse(entries.mapValues { it.value.toByteArray() })
        assertTrue(result.toString(), result is Outcome.Success)
        return (result as Outcome.Success).value
    }

    @Test
    fun `reads chapters, headings, lists, rows, a text box once and notes last`() {
        val doc = parse(
            mapOf(
                "word/document.xml" to document,
                "word/styles.xml" to styles,
                "word/footnotes.xml" to footnotes,
                "docProps/core.xml" to core,
            ),
        )
        assertEquals("Board report", doc.title)
        assertEquals(listOf("A Short Report", "Chapter One"), doc.chapters.map { it.title })
        val blocks = doc.chapters.flatMap { it.blocks }.map { it.kind to it.text }
        assertEquals(
            listOf(
                BlockKind.HEADING to "A Short Report",
                BlockKind.BODY to "Written for the board.",
                BlockKind.HEADING to "Chapter One",
                BlockKind.BODY to "Rain fell all night.",
                BlockKind.HEADING to "Costs",
                BlockKind.LIST_ITEM to "Seeds",
                BlockKind.LIST_ITEM to "Water",
                BlockKind.BODY to "Item; ₹500",
                BlockKind.BODY to "Total",
                BlockKind.BODY to "In the box",
                BlockKind.BODY to "मन भय से मुक्त हो।",
                BlockKind.BODY to "Measured at the farm.",
            ),
            blocks,
        )
    }

    @Test
    fun `without styles the built-in style ids still mark headings`() {
        val body = "<w:document>${p("Intro", "Heading1")}${p("Hello there.")}</w:document>"
        val doc = parse(mapOf("word/document.xml" to body))
        assertEquals(listOf("Intro"), doc.chapters.map { it.title })
    }

    @Test
    fun `a package without a Word document is refused`() {
        assertTrue(DocxParser.parse(mapOf("xl/workbook.xml" to ByteArray(1))) is Outcome.Failure)
        val empty = DocxParser.parse(mapOf("word/document.xml" to "<w:document/>".toByteArray()))
        assertEquals(Outcome.Failure(TextBookError.NoText), empty)
    }
}
