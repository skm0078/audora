// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/acted/ActedTakesTest.kt
// Purpose: The acted voice's take checks and text pieces (ADR-059).

package io.github.skm0078.audora.speech.acted

import io.github.skm0078.audora.core.config.ActedConfig
import io.github.skm0078.audora.core.model.Feeling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActedTakesTest {
    private val takes = ActedTakes(ActedConfig())

    @Test
    fun `a feeling is acted more strongly, and the retry is plainer either way`() {
        val plain = takes.takesFor(Feeling.NEUTRAL)
        val sad = takes.takesFor(Feeling.SAD)

        assertEquals(listOf(0.5f, 0.4f), plain.map { it.strength })
        assertEquals(listOf(0.65f, 0.4f), sad.map { it.strength })
        assertTrue(sad[1].repetitionPenalty > sad[0].repetitionPenalty)
    }

    @Test
    fun `the token cap allows a slow reading plus a breath`() {
        // 80 characters at 8 a second is 10 s, plus 1.5 s, at 25 tokens a second.
        assertEquals(287, takes.maxTokens(80))
    }

    @Test
    fun `a take that ran to the cap or lost words fails, a normal one passes`() {
        val chars = 50 // at most 25 characters a second: 2 s, 50 tokens
        assertEquals(ActedTakes.Verdict.GOOD, takes.verdict(chars, speech(tokens = 100, stopped = true)))
        assertEquals(ActedTakes.Verdict.RAN_ON, takes.verdict(chars, speech(tokens = 100, stopped = false)))
        assertEquals(ActedTakes.Verdict.CUT_SHORT, takes.verdict(chars, speech(tokens = 40, stopped = true)))
        assertEquals(ActedTakes.Verdict.GOOD, takes.verdict(chars, speech(tokens = 50, stopped = true)))
    }

    @Test
    fun `short text is one piece`() {
        assertEquals(listOf("मीना ने पूछा, कौन है?"), ActedPieces.of("  मीना ने पूछा, कौन है?  ", maxChars = 60))
    }

    @Test
    fun `long text is cut at the last clause mark that fits, else at a space`() {
        val text = "aaaa bbbb, cccc dddd eeee; ffff gggg hhhh iiii jjjj kkkk"
        val pieces = ActedPieces.of(text, maxChars = 40)

        assertEquals(listOf("aaaa bbbb, cccc dddd eeee;", "ffff gggg hhhh iiii jjjj kkkk"), pieces)
        assertEquals(listOf("aaaa bbbb", "cccc"), ActedPieces.of("aaaa bbbb cccc", maxChars = 10))
        assertTrue(ActedPieces.of("   ", maxChars = 40).isEmpty())
    }

    private fun speech(tokens: Int, stopped: Boolean) = ActedSpeech(LongArray(tokens) { 100L }, stopped)
}
