// File: core/src/test/kotlin/io/github/skm0078/audora/core/progress/TimeLeftTest.kt
// Purpose: Time left (plan item 24): speaking rate per language, speed, pace silences, unseen pages as the average.

package io.github.skm0078.audora.core.progress

import io.github.skm0078.audora.core.config.TimeLeftConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.Pace
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.SpeechStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeLeftTest {
    // 10 characters a second in every language keeps the arithmetic readable.
    private val estimate = TimeLeft(TimeLeftConfig(charsPerSecond = Lang.entries.associateWith { 10f }))

    /** Two 20-character sentences: 2 s each at 1×, plus the pace's silence after each. */
    private fun page(page: Int, lang: Lang = Lang.EN) = List(2) { i ->
        ReadingUnit(page, i, i * 20, i * 20 + 20, "x".repeat(20), lang, 0, null)
    }

    private val tight = SpeechStyle(rate = 1f, pace = Pace.TIGHT)

    @Test
    fun `known pages are summed from the reading position`() {
        val pages = mapOf(0 to page(0), 1 to page(1))
        assertEquals(8_000L, estimate.msUntil(0, 0, 2, pages, tight))
        assertEquals("the sentence being read still counts", 8_000L, estimate.msUntil(0, 5, 2, pages, tight))
        assertEquals(6_000L, estimate.msUntil(0, 20, 2, pages, tight))
        assertEquals(0L, estimate.msUntil(1, 0, 1, pages, tight))
    }

    @Test
    fun `unseen pages count as the average, and nothing seen means no estimate`() {
        assertEquals(40_000L, estimate.msUntil(0, 0, 10, mapOf(3 to page(3)), tight))
        assertNull(estimate.msUntil(0, 0, 10, emptyMap(), tight))
        assertNull(estimate.msUntil(0, 0, 10, mapOf(2 to emptyList()), tight))
    }

    @Test
    fun `speed shortens speech and pace adds silence`() {
        val pages = mapOf(0 to page(0))
        assertEquals(2_000L, estimate.msUntil(0, 0, 1, pages, tight.copy(rate = 2f)))
        val normal = requireNotNull(estimate.msUntil(0, 0, 1, pages, tight.copy(pace = Pace.NORMAL)))
        assertEquals("150 ms after each sentence at the normal pace", 4_300L, normal)
    }

    @Test
    fun `a rough figure for unseen books scales with pages and speed`() {
        val rough =
            TimeLeft(TimeLeftConfig(charsPerSecond = Lang.entries.associateWith { 10f }, typicalCharsPerPage = 100))
        assertEquals(30_000L, rough.roughMs(3, Lang.EN, 1f))
        assertEquals(15_000L, rough.roughMs(3, Lang.EN, 2f))
        assertNull(rough.roughMs(0, Lang.EN, 1f))
    }
}
