// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/voice/ReadingSetupTest.kt
// Purpose: Only a scanned page's run-ons reach the punctuation model (RI-11).

package io.github.skm0078.audora.playback.voice

import io.github.skm0078.audora.core.config.PunctuationConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.unit
import io.github.skm0078.audora.speech.punct.Punctuator
import io.github.skm0078.audora.speech.punct.RunOnPunctuation
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingSetupTest {
    private val runOn = "one two three four five six"
    private val guard = RunOnPunctuation(PunctuationConfig(minRunWords = 3)) {
        Punctuator { Outcome.Success("one two three. four five six.") }
    }

    @Test
    fun `a scanned page's run-on gets the model's pauses`() {
        val setup = ReadingSetup(scanned = { it == 2 }, runOn = guard)

        assertEquals("one two three, four five six,", setup.punctuated(runOn, unit(2, 0)))
    }

    @Test
    fun `text pages, other languages and no model are left as written`() {
        assertEquals(runOn, ReadingSetup(scanned = { it == 2 }, runOn = guard).punctuated(runOn, unit(1, 0)))
        assertEquals(runOn, ReadingSetup(scanned = { true }, runOn = guard).punctuated(runOn, unit(2, 0, Lang.HI)))
        assertEquals(runOn, ReadingSetup(scanned = { true }).punctuated(runOn, unit(2, 0)))
    }
}
