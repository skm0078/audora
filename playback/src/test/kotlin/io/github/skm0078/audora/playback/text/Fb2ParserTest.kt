// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/text/Fb2ParserTest.kt
// Purpose: An FB2's sections, titles, verse, tables and notes come out in reading order; pictures and note numbers
//          are left out.

package io.github.skm0078.audora.playback.text

import io.github.skm0078.audora.core.result.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Fb2ParserTest {
    private val book = """<?xml version="1.0" encoding="UTF-8"?>
        <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0" xmlns:l="http://www.w3.org/1999/xlink">
        <description><title-info><genre>prose</genre>
          <author><first-name>Rabindranath</first-name><last-name>Tagore</last-name></author>
          <author><nickname>Guru</nickname></author>
          <book-title>গল্পগুচ্ছ</book-title><lang>bn</lang>
          <annotation><p>Not read: the blurb.</p></annotation>
        </title-info></description>
        <body>
          <title><p>Stories</p></title>
          <epigraph><p>Where the mind is without fear.</p><text-author>R. T.</text-author></epigraph>
          <section><title><p>Chapter 1</p><p>The Postmaster</p></title>
            <p>The postmaster first took up his duties<a l:href="#n1" type="note">[1]</a> in Ulapur.</p>
            <image l:href="#pic.jpg"/>
            <section><title><p>A part</p></title><p>Deeper text.</p><subtitle>* * *</subtitle><p>After the stars.</p>
            </section>
          </section>
          <section><title><p>Chapter 2</p></title>
            <poem><title><p>A song</p></title><stanza><v>First line,</v><v>second line.</v></stanza></poem>
            <empty-line/>
            <table><tr><th>Name</th><th>Age</th></tr><tr><td>Ratan</td><td>12</td></tr></table>
          </section>
        </body>
        <body name="notes"><section id="n1"><title><p>1</p></title><p>A village in Bengal.</p></section></body>
        <binary id="pic.jpg" content-type="image/jpeg">/9j/4AAQSkZJRgABAQ==</binary>
        </FictionBook>"""

    private fun parse(bytes: ByteArray = book.toByteArray()) = (Fb2Parser.parse(bytes) as Outcome.Success).value

    @Test
    fun `reads title, language and authors from the description`() {
        assertEquals("গল্পগুচ্ছ", parse().title)
        assertEquals("bn", parse().language)
        val info = Fb2Info.of(MarkupScanner.scan(book))
        assertEquals("Rabindranath Tagore, Guru", info.author)
    }

    @Test
    fun `top-level sections are chapters, with titles, verse, rows and notes in order`() {
        val document = parse()
        assertEquals(listOf(null, "Chapter 1 The Postmaster", "Chapter 2"), document.chapters.map { it.title })
        assertEquals(
            listOf(
                TextBlock(BlockKind.HEADING, "Stories"),
                TextBlock(BlockKind.BODY, "Where the mind is without fear."),
                TextBlock(BlockKind.BODY, "R. T."),
            ),
            document.chapters[0].blocks,
        )
        assertEquals(
            listOf(
                TextBlock(BlockKind.HEADING, "Chapter 1 The Postmaster"),
                TextBlock(BlockKind.BODY, "The postmaster first took up his duties in Ulapur."),
                TextBlock(BlockKind.HEADING, "A part"),
                TextBlock(BlockKind.BODY, "Deeper text."),
                TextBlock(BlockKind.BODY, "After the stars."),
            ),
            document.chapters[1].blocks,
        )
        assertEquals(
            listOf(
                TextBlock(BlockKind.HEADING, "Chapter 2"),
                TextBlock(BlockKind.HEADING, "A song"),
                TextBlock(BlockKind.BODY, "First line,"),
                TextBlock(BlockKind.BODY, "second line."),
                TextBlock(BlockKind.BODY, "Name; Age"),
                TextBlock(BlockKind.BODY, "Ratan; 12"),
                TextBlock(BlockKind.BODY, "A village in Bengal."),
            ),
            document.chapters[2].blocks,
        )
    }

    @Test
    fun `reads the encoding the XML declaration names`() {
        val cyrillic = """<?xml version="1.0" encoding="windows-1251"?><FictionBook><body><section>
            <title><p>Глава</p></title><p>Привет, мир.</p></section></body></FictionBook>"""
        val document = parse(cyrillic.toByteArray(charset("windows-1251")))
        assertEquals("Привет, мир.", document.chapters[0].blocks[1].text)
    }

    @Test
    fun `reads a zipped FB2, and refuses a zip without one or a book without text`() {
        val entries = mapOf("story.fb2" to book.toByteArray())
        assertEquals(parse(), (Fb2Parser.parseZip(entries) as Outcome.Success).value)
        val noBook = Fb2Parser.parseZip(mapOf("a.txt" to ByteArray(1))) as Outcome.Failure
        assertTrue(noBook.error is TextBookError.Damaged)
        val pictureOnly = "<FictionBook><body><image/></body></FictionBook>".toByteArray()
        assertEquals(TextBookError.NoText, (Fb2Parser.parse(pictureOnly) as Outcome.Failure).error)
    }
}
