// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/text/PlainTextParserTest.kt
// Purpose: Text files become paragraphs, headings, list items and chapters; encodings are guessed safely.

package io.github.skm0078.audora.playback.text

import org.junit.Assert.assertEquals
import org.junit.Test

class PlainTextParserTest {
    private val parser = PlainTextParser()

    @Test
    fun `paragraphs, headings, lists and chapters`() {
        val text = """
            Chapter 1

            It was a dark
            and stormy night.

            What we need
            • bread
            • milk

            - tea
            - sugar

            CHAPTER 2: The Return

            He came back.

            अध्याय २

            मन भय से मुक्त हो।
        """.trimIndent()
        val book = parser.parse(text)
        assertEquals(listOf("Chapter 1", "CHAPTER 2: The Return", "अध्याय २"), book.chapters.map { it.title })
        assertEquals(
            listOf(
                TextBlock(BlockKind.HEADING, "Chapter 1"),
                TextBlock(BlockKind.BODY, "It was a dark and stormy night."),
                TextBlock(BlockKind.BODY, "What we need • bread • milk"),
                TextBlock(BlockKind.LIST_ITEM, "tea"),
                TextBlock(BlockKind.LIST_ITEM, "sugar"),
            ),
            book.chapters[0].blocks,
        )
        assertEquals(TextBlock(BlockKind.BODY, "मन भय से मुक्त हो।"), book.chapters[2].blocks.last())
    }

    @Test
    fun `text without chapter headings is one chapter`() {
        val book = parser.parse("Notes\r\n\r\nPart of the plan is here.\r\n\r\nCivil\r\n\r\nThe end.")
        assertEquals(1, book.chapters.size)
        assertEquals(null, book.chapters[0].title)
        assertEquals(BlockKind.HEADING, book.chapters[0].blocks[0].kind)
        assertEquals(BlockKind.HEADING, book.chapters[0].blocks[2].kind)
    }

    @Test
    fun `decodes UTF-8, byte-order marks and Windows-1252`() {
        assertEquals("मन", parser.decode("मन".toByteArray()))
        assertEquals("A", parser.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte(), 0x41)))
        assertEquals("A", parser.decode(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x41, 0x00)))
        assertEquals(
            "“café”",
            parser.decode(byteArrayOf(0x93.toByte(), 0x63, 0x61, 0x66, 0xE9.toByte(), 0x94.toByte())),
        )
    }
}
