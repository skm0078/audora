// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/delivery/NarrationArcTest.kt
// Purpose: The narrator's rise and fall across a paragraph (Q-3): pitch steps down, sections start slower, long
//          sentences breathe, speech and headings are left alone, and each sentence's own variation is steady.

package io.github.skm0078.audora.speech.delivery

import io.github.skm0078.audora.core.config.NarrationConfig
import io.github.skm0078.audora.core.model.Feeling
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ParagraphPlace
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.UnitKind
import kotlin.math.ln
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrationArcTest {
    private val steady = NarrationArc(NarrationConfig(jitterSemitones = 0f, jitterRate = 0f))

    private fun unit(text: String = "The rain went on.", kind: UnitKind = UnitKind.BODY) =
        ReadingUnit(0, 0, 0, text.length, text, Lang.EN, 0, null, kind = kind)

    private fun semitonesOf(delivery: Delivery) = (12 * ln(delivery.pitch.toDouble()) / ln(2.0)).toFloat()

    @Test
    fun `a paragraph starts higher and settles lower, in even steps`() {
        val pitches = (0 until 4).map { i ->
            semitonesOf(steady.shape(Delivery.PLAIN, unit(), ParagraphPlace(i, 4), quoted = false))
        }

        assertEquals(NarrationConfig.DEFAULT_START_SEMITONES, pitches.first(), EPS)
        assertEquals(NarrationConfig.DEFAULT_END_SEMITONES, pitches.last(), EPS)
        assertEquals(pitches.sortedDescending(), pitches)
        val step = pitches[0] - pitches[1]
        pitches.zipWithNext().forEach { (a, b) -> assertEquals(step, a - b, EPS) }
    }

    @Test
    fun `a lone sentence takes the middle of the arc`() {
        val alone = steady.shape(Delivery.PLAIN, unit(), ParagraphPlace(0, 1), quoted = false)
        val middle = (NarrationConfig.DEFAULT_START_SEMITONES + NarrationConfig.DEFAULT_END_SEMITONES) / 2
        assertEquals(middle, semitonesOf(alone), EPS)
    }

    @Test
    fun `a section's first sentence is slowest, then a paragraph's first and last, the rest unchanged`() {
        fun rate(place: ParagraphPlace) = steady.shape(Delivery.PLAIN, unit(), place, quoted = false).rate
        val cases = listOf(
            ParagraphPlace(0, 3, afterHeading = true) to NarrationConfig.DEFAULT_SECTION_START_RATE,
            ParagraphPlace(0, 3) to NarrationConfig.DEFAULT_PARAGRAPH_START_RATE,
            ParagraphPlace(1, 3) to 1f,
            ParagraphPlace(2, 3) to NarrationConfig.DEFAULT_PARAGRAPH_END_RATE,
        )
        cases.forEach { (place, expected) -> assertEquals("$place", expected, rate(place), EPS) }
    }

    @Test
    fun `a long sentence gets more breath at its commas, a short one does not`() {
        val long = unit(List(NarrationConfig.DEFAULT_LONG_SENTENCE_WORDS) { "word" }.joinToString(", "))
        val place = ParagraphPlace(1, 3)

        assertEquals(
            NarrationConfig.DEFAULT_LONG_CLAUSE_SCALE,
            steady.shape(Delivery.PLAIN, long, place, quoted = false).clauseScale,
            EPS,
        )
        assertEquals(1f, steady.shape(Delivery.PLAIN, unit(), place, quoted = false).clauseScale, EPS)
    }

    @Test
    fun `quoted speech, headings, equations and footnotes are left as they were planned`() {
        val planned = Delivery(rate = 1.1f, pitch = 1.05f, feeling = Feeling.LIVELY)
        val place = ParagraphPlace(0, 3, afterHeading = true)
        val untouched = listOf(
            unit() to true,
            unit(kind = UnitKind.HEADING) to false,
            unit(kind = UnitKind.EQUATION) to false,
            unit(kind = UnitKind.FOOTNOTE) to false,
            unit(kind = UnitKind.CAPTION) to false,
        )
        untouched.forEach { (u, quoted) ->
            assertEquals("${u.kind} quoted=$quoted", planned, NarrationArc().shape(planned, u, place, quoted))
        }
    }

    @Test
    fun `the arc goes on top of the sentence's own feeling`() {
        val sad = Delivery(rate = 0.9f, pitch = 0.92f, feeling = Feeling.SAD, pauseScale = 1.4f)

        val shaped = steady.shape(sad, unit(), ParagraphPlace(0, 2), quoted = false)

        assertEquals(0.9f * NarrationConfig.DEFAULT_PARAGRAPH_START_RATE, shaped.rate, EPS)
        assertEquals(0.92f * 2f.pow(NarrationConfig.DEFAULT_START_SEMITONES / 12), shaped.pitch, EPS)
        assertEquals(Feeling.SAD, shaped.feeling)
        assertEquals(1.4f, shaped.pauseScale, EPS)
    }

    @Test
    fun `each sentence varies a little, the same way every time, and an unknown place gets only that`() {
        val arc = NarrationArc()
        val texts = (1..200).map { "Sentence number $it went by." }
        val shaped = texts.map { arc.shape(Delivery.PLAIN, unit(it), null, quoted = false) }

        assertEquals(shaped, texts.map { arc.shape(Delivery.PLAIN, unit(it), null, quoted = false) })
        shaped.forEach {
            assertTrue(
                "${it.pitch}",
                kotlin.math.abs(semitonesOf(it)) <= NarrationConfig.DEFAULT_JITTER_SEMITONES + EPS,
            )
            assertTrue("${it.rate}", kotlin.math.abs(it.rate - 1f) <= NarrationConfig.DEFAULT_JITTER_RATE + EPS)
        }
        // Spread both ways, not all the same.
        assertTrue(shaped.any { it.pitch > 1f } && shaped.any { it.pitch < 1f })
        assertTrue(shaped.map { it.rate }.distinct().size > 50)
        assertEquals(Delivery.PLAIN, steady.shape(Delivery.PLAIN, unit(), null, quoted = false))
    }

    private companion object {
        const val EPS = 1e-4f
    }
}
