// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/delivery/DeliveryPlannerTest.kt
// Purpose: A sentence's delivery follows the book's intent, with a feeling only where the intent allows (ADR-056).

package io.github.skm0078.audora.speech.delivery

import io.github.skm0078.audora.core.config.DeliveryConfig
import io.github.skm0078.audora.core.model.ContentIntent
import io.github.skm0078.audora.core.model.Feeling
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.UnitKind
import kotlin.math.pow
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
    fun `sad lines are lower, lively ones higher, plain and factual ones keep the voice's pitch (ADR-076)`() {
        val sad = planner.plan(unit("“Don't go,” she sobbed."), ContentIntent.STORY, quoted = true)
        val lively = planner.plan(unit("“We did it,” he laughed."), ContentIntent.STORY, quoted = true)
        val plain = planner.plan(unit("The rain kept falling."), ContentIntent.STORY, quoted = false)
        val news = planner.plan(unit("“Fire!” he shouted."), ContentIntent.NEWS, quoted = true)
        assertTrue("${sad.pitch}", sad.pitch < 1f)
        assertTrue("${lively.pitch}", lively.pitch > 1f)
        assertEquals(1f, plain.pitch, DELTA)
        assertEquals(1f, news.pitch, DELTA)
    }

    @Test
    fun `a storybook's stronger feelings stay within the pitch limit`() {
        val surprised = planner.plan(unit("“Oh!” she gasped."), ContentIntent.CHILDREN, quoted = true)
        val limit = 2.0.pow(config.maxSemitones / 12.0).toFloat()
        assertEquals(Feeling.SURPRISED, surprised.feeling)
        assertTrue("${surprised.pitch} <= $limit", surprised.pitch in 1f..limit + DELTA)
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

    @Test
    fun `the story brain's feeling wins over the rules', and anger is read lower and louder (ADR-078)`() {
        val line = unit("“You could have called!”")
        val ruled = planner.plan(line, ContentIntent.STORY, quoted = true)
        val angry = planner.plan(line, ContentIntent.STORY, quoted = true, judged = Feeling.ANGRY)
        assertEquals(Feeling.EMPHATIC, ruled.feeling)
        assertEquals(Feeling.ANGRY, angry.feeling)
        assertTrue(angry.gainDb > 0f)
        assertTrue("${angry.pitch}", angry.pitch < 1f)
        val whisper = unit("“Run,” she whispered.")
        assertEquals(Feeling.SCARED, planner.plan(whisper, ContentIntent.STORY, true, judged = Feeling.SCARED).feeling)
    }

    @Test
    fun `when the story brain heard no feeling, a line keeps its own cues but not a carried mood`() {
        val question = unit("“Where were you?”")
        val plain = unit("“At the station.”")
        assertEquals(
            Feeling.ASKING,
            planner.plan(question, ContentIntent.STORY, true, judged = Feeling.NEUTRAL).feeling,
        )
        assertEquals(Feeling.SAD, planner.plan(plain, ContentIntent.STORY, true, carried = Feeling.SAD).feeling)
        assertEquals(
            Feeling.NEUTRAL,
            planner.plan(plain, ContentIntent.STORY, true, carried = Feeling.SAD, judged = Feeling.NEUTRAL).feeling,
        )
    }

    @Test
    fun `no brain call reads exactly as before, and an intent without feelings ignores the brain`() {
        listOf("“Run,” she whispered.", "“Where were you?”", "The rain kept falling.").forEach { text ->
            assertEquals(
                planner.plan(unit(text), ContentIntent.STORY, quoted = true, carried = Feeling.SAD),
                planner.plan(unit(text), ContentIntent.STORY, quoted = true, carried = Feeling.SAD, judged = null),
            )
        }
        assertEquals(
            Feeling.NEUTRAL,
            planner.plan(unit("“Fire!”"), ContentIntent.NEWS, quoted = true, judged = Feeling.ANGRY).feeling,
        )
    }

    private companion object {
        const val DELTA = 1e-4f
    }

    @Test
    fun `a carried mood colours a plain story sentence, never a fact book or one with its own feeling`() {
        val plain = unit("The house was dark.")

        assertEquals(Feeling.SAD, planner.plan(plain, ContentIntent.STORY, false, carried = Feeling.SAD).feeling)
        assertEquals(Feeling.NEUTRAL, planner.plan(plain, ContentIntent.STUDY, false, carried = Feeling.SAD).feeling)
        val laugh = planner.plan(unit("He laughed."), ContentIntent.STORY, false, carried = Feeling.SAD)
        assertEquals(Feeling.LIVELY, laugh.feeling)
    }
}
