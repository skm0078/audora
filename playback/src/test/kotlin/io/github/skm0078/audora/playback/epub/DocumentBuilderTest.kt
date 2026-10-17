// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/epub/DocumentBuilderTest.kt
// Purpose: Chapters, paragraphs across pages, lists, tables, notes, junk and page breaks of a copy.

package io.github.skm0078.audora.playback.epub

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.UnitKind
import io.github.skm0078.audora.playback.export.AudioChapter
import io.github.skm0078.audora.playback.queue.UnitKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentBuilderTest {
    private val builder = DocumentBuilder { "Pages ${it.firstPage + 1}-${it.lastPage + 1}" }
    private val oneChapter = listOf(AudioChapter(1, 0, 1))

    @Test
    fun `a paragraph goes on across a page turn and ends at a long pause`() {
        val units = mapOf(
            0 to listOf(sentence(0, 0, "Title", UnitKind.HEADING, PARAGRAPH_END), sentence(0, 1, "One.")),
            1 to listOf(sentence(1, 0, "Two.", pause = PARAGRAPH_END), sentence(1, 1, "Three.")),
        )
        val chapter = builder.build("Book", oneChapter, units)!!.chapters.single()

        assertEquals("Title", chapter.title)
        val blocks = chapter.blocks
        assertEquals(3, blocks.size)
        assertEquals(listOf("One.", "Two."), blocks[1].sentences.map { it.unit.text })
        assertEquals(listOf(true, false, true, false), chapter.sentences.map { it.pageBreak })
        assertEquals(listOf(1, 2), chapter.pageStarts)
    }

    @Test
    fun `list items split at paragraph pauses and tables group their rows`() {
        val units = mapOf(
            0 to listOf(
                sentence(0, 0, "• First.", UnitKind.LIST_ITEM),
                sentence(0, 1, "More.", UnitKind.LIST_ITEM, PARAGRAPH_END),
                sentence(0, 2, "• Second.", UnitKind.LIST_ITEM, PARAGRAPH_END),
                tableUnit(0, 3, null, "Name", "Age"),
                tableUnit(0, 4, 0, "Ram", "34"),
                tableUnit(0, 5, 1, "Sita", "30"),
            ),
        )
        val blocks = builder.build("Book", oneChapter, units)!!.chapters.single().blocks

        val list = blocks[0] as DocBlock.ListItems
        assertEquals(listOf(2, 1), list.items.map { it.size })
        val table = blocks[1] as DocBlock.Table
        assertEquals("Name  Age", table.header?.unit?.text)
        assertEquals(2, table.rows.size)
    }

    @Test
    fun `notes go to the end of the chapter and junk is left out unless it was read`() {
        val junk = sentence(0, 2, "~#%&@").copy(speakScore = 0.1f)
        val units = mapOf(
            0 to listOf(
                sentence(0, 0, "Body.", pause = PARAGRAPH_END),
                note(0, 1, "1 A note.", 1),
                junk,
                sentence(0, 3, "More body."),
            ),
        )
        val plain = builder.build("Book", oneChapter, units)!!.chapters.single()
        assertEquals(listOf("Body.", "More body."), plain.blocks.flatMap { b -> b.sentences.map { it.unit.text } })
        assertEquals("1 A note.", plain.notes.single().sentences.single().unit.text)

        val voiced = builder.build("Book", oneChapter, units, voiced = setOf(UnitKey.of(junk)))!!.chapters.single()
        assertTrue(voiced.sentences.any { it.unit == junk })
    }

    @Test
    fun `chapters without a heading get the fallback title and the main language wins`() {
        val chapters = listOf(AudioChapter(1, 0, 0), AudioChapter(2, 1, 1), AudioChapter(3, 2, 2))
        val units = mapOf(
            0 to listOf(sentence(0, 0, "हिंदी में एक लंबा वाक्य।").copy(lang = Lang.HI)),
            1 to listOf(sentence(1, 0, "Short.")),
        )
        val doc = builder.build("Book", chapters, units)!!

        assertEquals(2, doc.chapters.size)
        assertEquals("Pages 2-2", doc.chapters[1].title)
        assertEquals(Lang.HI, doc.lang)
        assertEquals(setOf(Lang.HI, Lang.EN), doc.langs)
    }

    @Test
    fun `a book with no text has no copy`() {
        assertNull(builder.build("Book", oneChapter, mapOf(0 to listOf(sentence(0, 0, " ")))))
    }
}
