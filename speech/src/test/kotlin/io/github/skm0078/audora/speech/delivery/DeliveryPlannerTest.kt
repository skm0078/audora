// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/delivery/DeliveryPlannerTest.kt
// Purpose: A sentence's delivery follows the book's intent, with a feeling only where the intent allows (ADR-056).

package io.github.skm0078.audora.speech.delivery

import io.github.skm0078.audora.core.config.DeliveryConfig
import io.github.skm0078.audora.core.model.ContentIntent
import io.github.skm0078.audora.core.model.Feeling
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.UnitKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryPlannerTest {
    private val config = DeliveryConfig()
    private val planner = DeliveryPlanner(config)

    private fun unit(text: String, kind: UnitKind = UnitKind.BODY) =
        ReadingUnit(0, 0, 0, text.length, text, Lang.EN, 0, null, kind)

    @Test
    fun `a whispered line in a story is softer and slower than narration`() {
        val narration = planner.plan(unit("The rain kept falling."), ContentIntent.STORY, quoted = false)
        val whisper = planner.plan(unit("“Run,” she whispered."), ContentIntent.STORY, quoted = true)
        assertEquals(Feeling.SOFT, whisper.feeling)
        assertTrue(whisper.gainDb < 0f)
        assertTrue(whisper.rate < config.profile(ContentIntent.STORY).dialogueRate)
        assertEquals(config.profile(ContentIntent.STORY).rate, narration.rate, DELTA)
        assertEquals(0f, narration.gainDb, DELTA)
    }

    @Test
    fun `factual and sacred texts are never acted`() {
        listOf(ContentIntent.NEWS, ContentIntent.REPORT, ContentIntent.STUDY, ContentIntent.SCRIPTURE).forEach {
            val shouted = planner.plan(unit("“Fire!” he shouted."), it, quoted = true)
            assertEquals("$it", Feeling.NEUTRAL, shouted.feeling)
            assertEquals("$it", 0f, shouted.gainDb, DELTA)
            assertEquals("$it", config.profile(it).dialogueRate, shouted.rate, DELTA)
        }
    }

    @Test
    fun `headings and captions keep the intent's pace without feeling, headings get their room`() {
        val heading = planner.plan(unit("Chapter 3: Stop!", UnitKind.HEADING), ContentIntent.STUDY, quoted = false)
        val profile = config.profile(ContentIntent.STUDY)
        assertEquals(Feeling.NEUTRAL, heading.feeling)
        assertEquals(profile.pauseScale * profile.headingPauseScale, heading.pauseScale, DELTA)
        val caption = planner.plan(unit("Figure 2. Run!", UnitKind.CAPTION), ContentIntent.STORY, quoted = false)
        assertEquals(Feeling.NEUTRAL, caption.feeling)
    }

    @Test
    fun `a storybook feels more than a story, general text less`() {
        val text = unit("“We won!” cried Tom.")
        val story = planner.plan(text, ContentIntent.STORY, quoted = true).gainDb
        val children = planner.plan(text, ContentIntent.CHILDREN, quoted = true).gainDb
        val general = planner.plan(text, ContentIntent.GENERAL, quoted = true).gainDb
        assertTrue("$general < $story < $children", general < story && story < children)
    }

    @Test
    fun `no delivery is slower than the floor or louder than the ceiling`() {
        val slow = DeliveryConfig(minRate = 0.95f, maxGainDb = 1f)
        val delivery = DeliveryPlanner(slow).plan(unit("He roared!"), ContentIntent.CHILDREN, quoted = false)
        assertTrue(delivery.rate >= 0.95f)
        assertTrue(delivery.gainDb <= 1f)
        val sad = DeliveryPlanner(slow).plan(unit("She wept."), ContentIntent.CHILDREN, quoted = false)
        assertEquals(0.95f, sad.rate, DELTA)
    }

    private companion object {
        const val DELTA = 1e-4f
    }
}
