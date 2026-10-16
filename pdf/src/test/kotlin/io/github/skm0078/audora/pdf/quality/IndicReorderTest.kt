// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/quality/IndicReorderTest.kt
// Purpose: Table-driven tests for the Indic repair and visual-to-logical reorder pass and its page-order decision.

package io.github.skm0078.audora.pdf.quality

import io.github.skm0078.audora.core.config.QualityConfig
import io.github.skm0078.audora.pdf.cleanup.MappedText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class IndicReorderTest {
    private val visual = IndicOrder(prebaseVisual = true, rephVisual = true)

    private data class Case(val name: String, val input: String, val expected: String)

    private fun check(order: IndicOrder, cases: List<Case>) = cases.forEach { case ->
        assertEquals(case.name, case.expected, IndicReorder.apply(MappedText.plain(case.input), order).text)
    }

    @Test
    fun `pre-base vowel signs move after their consonant cluster`() = check(
        visual,
        listOf(
            Case("Hindi i-matra at a word start", "िकताब", "किताब"),
            Case("i-matra after an independent vowel", "हाउिसंग", "हाउसिंग"),
            Case("i-matra mid-word", "मानिसक", "मानसिक"),
            Case("conjunct cluster", "िस्थत", "स्थित"),
            Case("two signs in one word", "िस्थित", "स्थिति"),
            Case("nukta stays with its consonant", "ि\u091C\u093C\u0902\u0926\u0917\u0940", "\u091C\u093Cिंदगी"),
            Case("Bengali i-kar", "ৈবষম", "বৈষম"),
            Case("Bengali e-kar", "ঋণদােনর", "ঋণদানের"),
            Case("Bengali o-kar composed", "েবাঝা", "বোঝা"),
            Case("Bengali au-kar composed", "েকৗশল", "কৌশল"),
            Case("sign before a non-consonant stays", "ि1 ি,", "ि1 ি,"),
            Case("sign at the end stays", "क ि", "क ि"),
        ),
    )

    @Test
    fun `rephs move before the syllable they sit on`() = check(
        visual,
        listOf(
            Case("Bengali reph at a word end", "ধমর্", "ধর্ম"),
            Case("Bengali reph before a vowel sign", "অথর্া", "অর্থা"),
            Case("reph after a vowel sign", "কতৃর্ক", "কর্তৃক"),
            Case("i-kar and reph in one word", "মািকর্ন", "মার্কিন"),
            Case("Hindi reph", "कायर्", "कार्य"),
            Case("reph over a conjunct", "वत्तर्मान", "वर्त्तमान"),
            Case("ra + virama at a word start is left", "र्क", "र्क"),
            Case("ra-phala after a virama is a conjunct, not a reph", "क्र्", "क्र्"),
        ),
    )

    @Test
    fun `logical text is left alone`() {
        val text = "किताब पढ़ो। ধর্ম ও বোঝা। मार्च"
        val input = MappedText.plain(text)
        assertSame(input, IndicReorder.apply(input, IndicOrder.LOGICAL))
        check(
            IndicOrder(prebaseVisual = true, rephVisual = false),
            listOf(Case("only signs move when rephs are logical", "िकताब धर्म", "किताब धर्म")),
        )
    }

    @Test
    fun `placeholder glyphs inside a cluster are dropped on every page`() = check(
        IndicOrder.LOGICAL,
        listOf(
            Case("before a vowel sign", "ক�োভিড", "কোভিড"),
            Case("before a virama", "क�्ष", "क्ष"),
            Case("between Latin letters it stays", "a�b", "a�b"),
            Case("before a consonant it stays", "क�ख", "क�ख"),
        ),
    )

    @Test
    fun `source offsets stay ascending and one per unit`() {
        val input = MappedText("েবাঝা ধমর্", IntArray(10) { it * 2 })
        val out = IndicReorder.apply(input, visual)
        assertEquals("বোঝা ধর্ম", out.text)
        assertEquals(out.text.length, out.sources.size)
        assertTrue(out.sources.toList().zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun `evidence counts word-initial signs and rephs at a syllable end`() {
        assertEquals(IndicOrderEvidence(2, 2, 0, 0), IndicOrderEvidence.of("िकताब िकसान"))
        assertEquals(IndicOrderEvidence(2, 0, 0, 0), IndicOrderEvidence.of("किताब किसान"))
        assertEquals(IndicOrderEvidence(0, 0, 2, 1), IndicOrderEvidence.of("ধমর্ কতৃর্ক"))
        assertEquals(IndicOrderEvidence(0, 0, 2, 0), IndicOrderEvidence.of("ধর্ম কর্তৃক"))
    }

    @Test
    fun `a page decides its order alone when it can, else with its neighbours`() {
        val config = QualityConfig(minVisualOrderSigns = 4, minRephs = 2)
        val visualPage = IndicOrderEvidence(10, 4, 4, 3)
        val logicalPage = IndicOrderEvidence(10, 0, 4, 0)
        val sparse = IndicOrderEvidence(1, 1, 0, 0)

        assertEquals(IndicOrder(true, true), indicOrderOf(visualPage, listOf(logicalPage), config))
        assertEquals(IndicOrder.LOGICAL, indicOrderOf(logicalPage, listOf(visualPage), config))
        assertEquals(IndicOrder(true, true), indicOrderOf(sparse, listOf(visualPage), config))
        assertEquals(IndicOrder.LOGICAL, indicOrderOf(sparse, listOf(logicalPage, logicalPage), config))
        assertEquals(IndicOrder.LOGICAL, indicOrderOf(sparse, emptyList(), config))
        // Rephs drawn after their syllable, but pre-base signs in logical order: mis-mapped, not visual.
        assertEquals(IndicOrder.LOGICAL, indicOrderOf(IndicOrderEvidence(10, 0, 4, 4), emptyList(), config))
        // Visual signs, too few rephs to tell anywhere: rephs are left.
        assertEquals(IndicOrder(true, false), indicOrderOf(IndicOrderEvidence(10, 5, 1, 1), emptyList(), config))
    }
}
