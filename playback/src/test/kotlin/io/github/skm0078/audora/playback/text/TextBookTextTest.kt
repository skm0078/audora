// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/text/TextBookTextTest.kt
// Purpose: Text books are paged by chapter and length, and cut into units with offsets, languages and pauses.

package io.github.skm0078.audora.playback.text

import io.github.skm0078.audora.core.config.ReadingUnitConfig
import io.github.skm0078.audora.core.config.TextBookConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.UnitKind
import io.github.skm0078.audora.core.result.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextBookTextTest {
    private val unitsConfig = ReadingUnitConfig()

    private fun book(vararg chapters: List<TextBlock>, language: String? = null) =
        TextDocument(null, language, chapters.map { TextChapter(null, it) })

    @Test
    fun `units carry offsets into the page text, languages and pauses`() {
        val text = TextBookText(
            book(
                listOf(
                    TextBlock(BlockKind.HEADING, "Gitanjali"),
                    TextBlock(BlockKind.BODY, "Where the mind is without fear. मन भय से मुक्त हो।"),
                    TextBlock(BlockKind.LIST_ITEM, "One item"),
                ),
            ),
        )
        val units = (text.units(0) as Outcome.Success).value
        val page = TextPages.of(book(listOf(TextBlock(BlockKind.HEADING, "Gitanjali")))).single()
        assertEquals("Gitanjali", page.text)
        assertEquals(
            listOf("Gitanjali", "Where the mind is without fear.", "मन भय से मुक्त हो।", "One item"),
            units.map {
                it.text
            },
        )
        assertEquals(listOf(Lang.EN, Lang.EN, Lang.HI, Lang.EN), units.map { it.lang })
        assertEquals(listOf(UnitKind.HEADING, UnitKind.BODY, UnitKind.BODY, UnitKind.LIST_ITEM), units.map { it.kind })
        assertEquals(
            listOf(
                unitsConfig.headingPauseMs,
                unitsConfig.sentencePauseMs,
                unitsConfig.paragraphPauseMs,
                unitsConfig.listItemPauseMs,
            ),
            units.map { it.pauseAfterMs },
        )
        val pageText = "Gitanjali\n\nWhere the mind is without fear. मन भय से मुक्त हो।\n\nOne item"
        units.forEach { assertEquals(it.text, pageText.substring(it.charStart, it.charEnd)) }
        assertEquals((0 until units.size).toList(), units.map { it.index })
    }

    @Test
    fun `chapters start pages and long text is cut at sentence ends`() {
        val config = TextBookConfig(pageChars = 40)
        val long = TextBlock(BlockKind.BODY, "First sentence is here. Second sentence is here. Third one.")
        val text = TextBookText(book(listOf(long), listOf(TextBlock(BlockKind.BODY, "Next chapter."))), config)
        assertEquals(3, text.pageCount)
        assertEquals(listOf(0, 2), text.chapterStarts())
        assertEquals(emptyList<Pair<String, Int>>(), text.contents)
        assertEquals(listOf("First sentence is here."), (text.units(0) as Outcome.Success).value.map { it.text })
        assertTrue(text.units(3) is Outcome.Failure)
    }

    @Test
    fun `the book's language is the fallback for text without letters`() {
        val text = TextBookText(book(listOf(TextBlock(BlockKind.BODY, "1910 — 1913.")), language = "bn"))
        assertEquals(Lang.BN, (text.units(0) as Outcome.Success).value.single().lang)
    }
}
