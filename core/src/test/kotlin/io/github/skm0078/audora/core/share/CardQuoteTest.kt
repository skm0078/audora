// File: core/src/test/kotlin/io/github/skm0078/audora/core/share/CardQuoteTest.kt
// Purpose: A quote card keeps at most 60 words, cut at a sentence end when one is close, tidied, in its script.

package io.github.skm0078.audora.core.share

import io.github.skm0078.audora.core.config.ShareCardConfig
import io.github.skm0078.audora.core.model.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardQuoteTest {
    private data class Case(val name: String, val selection: String, val expected: String, val cut: Boolean)

    private val small = ShareCardConfig(maxWords = 6)

    @Test
    fun `short quotes are kept whole and tidied`() {
        listOf(
            Case("one line", "All are equal.", "All are equal.", false),
            Case("line breaks and spaces", "All  are\nequal\n\n before law.", "All are equal before law.", false),
            Case("the book's own marks around it", "“All are equal.”", "All are equal.", false),
            Case("speech inside keeps its marks", "\"Yes,\" she said, \"go.\"", "\"Yes,\" she said, \"go.\"", false),
            Case("an apostrophe inside", "‘Don’t go.’", "Don’t go.", false),
        ).forEach { case ->
            val quote = CardQuote.of(case.selection, small)
            assertEquals(case.name, case.expected, quote.text)
            assertEquals(case.name, case.cut, quote.cut)
        }
    }

    @Test
    fun `a long selection is cut at a close sentence end, else after the last word with an ellipsis`() {
        listOf(
            Case("sentence end in reach", "One two three four. Five six seven eight.", "One two three four.", true),
            Case("Hindi danda", "सभी मनुष्य स्वतंत्र हैं। उन्हें समान अधिकार हैं।", "सभी मनुष्य स्वतंत्र हैं।", true),
            Case("Bengali danda", "সকল মানুষ স্বাধীন। তারা সমান মর্যাদা ও অধিকার পায়।", "সকল মানুষ স্বাধীন।", true),
            Case("closing quote after it", "He said “stop now.” Then they all left.", "He said “stop now.”", true),
            Case("no close end", "One. Two three four five six seven", "One. Two three four five six…", true),
            Case("no comma before …", "One two three four five six, seven", "One two three four five six…", true),
        ).forEach { case ->
            val quote = CardQuote.of(case.selection, small)
            assertEquals(case.name, case.expected, quote.text)
            assertEquals(case.name, case.cut, quote.cut)
        }
    }

    @Test
    fun `the default keeps sixty words`() {
        val words = (1..80).joinToString(" ") { "w$it" }
        val quote = CardQuote.of(words)
        assertTrue(quote.cut)
        assertEquals(60, quote.text.removeSuffix("…").split(' ').size)
        assertFalse(CardQuote.of((1..60).joinToString(" ") { "w$it" }).cut)
    }

    @Test
    fun `the script is the one most letters are in`() {
        assertEquals(Lang.EN, CardQuote.of("Plain words").script)
        assertEquals(Lang.HI, CardQuote.of("हर किताब सुनिए (Audora)").script)
        assertEquals(Lang.BN, CardQuote.of("প্রতিটি বই শুনুন").script)
        assertEquals(Lang.EN, CardQuote.of("1234 …").script)
    }

    @Test
    fun `a whole page keeps up to the page limit, not the quote's`() {
        val page = "One two three four five six seven eight."
        val roomy = ShareCardConfig(maxWords = 3, maxPageWords = 20)
        val whole = CardQuote.ofPage(page, roomy)
        assertEquals(page, whole.text)
        assertFalse(whole.cut)
        assertTrue(CardQuote.of(page, roomy).cut)

        val tight = ShareCardConfig(maxWords = 3, maxPageWords = 5)
        val cut = CardQuote.ofPage("One two three four. Five six seven eight.", tight)
        assertEquals("One two three four.", cut.text)
        assertTrue(cut.cut)
    }
}
