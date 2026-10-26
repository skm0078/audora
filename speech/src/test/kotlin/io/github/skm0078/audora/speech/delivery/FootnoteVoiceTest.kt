// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/delivery/FootnoteVoiceTest.kt
// Purpose: Footnotes read a little quicker and softer on top of their delivery; every other unit is left alone (Q-4).

package io.github.skm0078.audora.speech.delivery

import io.github.skm0078.audora.core.config.FootnoteVoiceConfig
import io.github.skm0078.audora.core.model.Feeling
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.UnitKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class FootnoteVoiceTest {
    private val voice = FootnoteVoice(FootnoteVoiceConfig(rate = 1.1f, gainDb = -3f))

    private fun unit(kind: UnitKind) = ReadingUnit(0, 0, 0, 4, "Text", Lang.EN, 0, null, kind)

    @Test
    fun `a footnote is quicker and softer than the delivery it was given`() {
        val planned = Delivery(rate = 0.9f, gainDb = 1f, feeling = Feeling.SOFT, pauseScale = 1.2f)
        val shaped = voice.shape(unit(UnitKind.FOOTNOTE), planned)
        assertEquals(0.99f, shaped.rate, 1e-4f)
        assertEquals(-2f, shaped.gainDb, 1e-4f)
        assertEquals(planned.copy(rate = shaped.rate, gainDb = shaped.gainDb), shaped)
    }

    @Test
    fun `every other kind of unit keeps its delivery`() {
        val planned = Delivery(rate = 1.05f)
        val others = UnitKind.entries.filter { it != UnitKind.FOOTNOTE && !it.isTable }
        for (kind in others) assertSame(kind.name, planned, voice.shape(unit(kind), planned))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a footnote never reads slower or louder than the text`() {
        FootnoteVoiceConfig(rate = 0.9f)
    }
}
