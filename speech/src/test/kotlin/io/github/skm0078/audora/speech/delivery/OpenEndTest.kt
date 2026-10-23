// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/delivery/OpenEndTest.kt
// Purpose: A sentence a page break cut is read as unfinished, with almost no pause; whole sentences are not (ADR-056).

package io.github.skm0078.audora.speech.delivery

import io.github.skm0078.audora.core.config.DeliveryConfig
import io.github.skm0078.audora.core.model.ContentIntent
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.UnitKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenEndTest {
    private val config = DeliveryConfig()
    private val planner = DeliveryPlanner(config)

    private fun plan(text: String, kind: UnitKind = UnitKind.BODY, intent: ContentIntent = ContentIntent.STORY) =
        planner.plan(ReadingUnit(0, 0, 0, text.length, text, Lang.EN, 0, null, kind), intent, quoted = false)

    @Test
    fun `a sentence cut by the page goes on, with a short pause`() {
        val cut = plan("the yon low-arched way, cut through what in old times must ")
        val whole = plan("It had been a great central chimney.")
        assertTrue(cut.openEnd)
        assertEquals(whole.pauseScale * config.openEndPauseScale, cut.pauseScale, DELTA)
        assertTrue(plan("He walked on past the mill, the church,").openEnd)
    }

    @Test
    fun `whole sentences, short pieces, headings and verse keep their fall`() {
        listOf("It rained.", "Where now?", "Is it so!", "see also", "He said: “Go”").forEach {
            assertFalse(it, plan(it).openEnd)
        }
        assertFalse(plan("the history of the old town hall", UnitKind.HEADING).openEnd)
        assertFalse(plan("the history of the old town hall", UnitKind.LIST_ITEM).openEnd)
        assertFalse(plan("and the woods are lovely dark and deep", intent = ContentIntent.POETRY).openEnd)
        assertFalse(plan("and the earth was without form and void", intent = ContentIntent.SCRIPTURE).openEnd)
    }

    @Test
    fun `hindi and bengali only go on after a comma`() {
        assertFalse(plan("राजा ने अपने मंत्री को बुलाया और कहा").openEnd)
        assertTrue(plan("राजा ने अपने मंत्री को बुलाया और कहा,").openEnd)
        assertFalse(plan("রাজা তার মন্ত্রীকে ডেকে বললেন যে").openEnd)
    }

    private companion object {
        const val DELTA = 1e-4f
    }
}
