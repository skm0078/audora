// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/reading/BookIntentTest.kt
// Purpose: The open book's intent is told from its first pages, counts a page once and settles (ADR-056).

package io.github.skm0078.audora.playback.reading

import io.github.skm0078.audora.core.config.DeliveryConfig
import io.github.skm0078.audora.core.model.ContentIntent
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookIntentTest {
    private fun page(page: Int, text: String, sentences: Int = 10) = (0 until sentences).map { i ->
        ReadingUnit(page, i, i * 100, i * 100 + text.length, text, Lang.EN, 0, null)
    }

    private val story = "“Where are you going?” asked Mira, pulling her coat tighter against the cold wind."
    private val plain = "The valley lies between two ranges of low hills and has been farmed for centuries."

    @Test
    fun `plain until enough words, then told, and each change is heard once`() {
        val changes = mutableListOf<ContentIntent>()
        val intent = BookIntent(DeliveryConfig(minWords = 100, settleWords = 1_000), readableFrom = 0f, changes::add)
        intent.page(page(0, story, sentences = 3))
        assertFalse(intent.told)
        assertEquals(ContentIntent.GENERAL, intent.current)
        intent.page(page(1, story))
        assertTrue(intent.told)
        assertEquals(ContentIntent.STORY, intent.current)
        intent.page(page(2, story))
        assertEquals(listOf(ContentIntent.STORY), changes)
    }

    @Test
    fun `a page read again counts once, and a settled intent is kept`() {
        val intent = BookIntent(DeliveryConfig(minWords = 100, settleWords = 200), readableFrom = 0f)
        intent.page(page(0, story))
        intent.page(page(0, plain))
        assertEquals(ContentIntent.STORY, intent.current)
        repeat(20) { intent.page(page(it + 1, plain)) }
        assertEquals(ContentIntent.STORY, intent.current)
    }

    @Test
    fun `a new book starts plain again`() {
        val intent = BookIntent(DeliveryConfig(minWords = 100), readableFrom = 0f)
        intent.page(page(0, story))
        intent.clear()
        assertFalse(intent.told)
        assertEquals(ContentIntent.GENERAL, intent.current)
        intent.page(page(0, plain))
        assertEquals(ContentIntent.GENERAL, intent.current)
        assertTrue(intent.told)
    }
}
