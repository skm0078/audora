// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/contract/SpeechStyleChangeTest.kt
// Purpose: A speech style change keeps every field it doesn't name as saved (cold-start reset, PR #22 review).

package io.github.skm0078.audora.playback.contract

import io.github.skm0078.audora.core.model.Pace
import io.github.skm0078.audora.core.model.ReadingModes
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.model.TableMode
import io.github.skm0078.audora.core.model.VoiceShaping
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechStyleChangeTest {
    private val saved = SpeechStyle(
        rate = 1.25f,
        pace = Pace.RELAXED,
        clausePauses = false,
        modes = ReadingModes(tables = TableMode.ROWS, skipUnreadable = false),
        shaping = VoiceShaping(boostDb = 6),
        pitch = 1.1f,
    )

    @Test
    fun `a speed change keeps pace, clause pauses, reading modes, sound and pitch`() {
        assertEquals(saved.copy(rate = 1.5f), SpeechStyleChange(rate = 1.5f).over(saved))
    }

    @Test
    fun `each field changes alone`() {
        assertEquals(saved.copy(pace = Pace.TIGHT), SpeechStyleChange(pace = Pace.TIGHT).over(saved))
        assertEquals(saved.copy(clausePauses = true), SpeechStyleChange(clausePauses = true).over(saved))
        assertEquals(saved.copy(modes = ReadingModes()), SpeechStyleChange(modes = ReadingModes()).over(saved))
        assertEquals(saved.copy(shaping = VoiceShaping()), SpeechStyleChange(shaping = VoiceShaping()).over(saved))
        assertEquals(saved.copy(pitch = 0.9f), SpeechStyleChange(pitch = 0.9f).over(saved))
    }

    @Test
    fun `two quick changes to different fields both stay`() {
        val afterPace = SpeechStyleChange(pace = Pace.TIGHT).over(saved)
        val afterSpeed = SpeechStyleChange(rate = 2f).over(afterPace)
        assertEquals(saved.copy(pace = Pace.TIGHT, rate = 2f), afterSpeed)
    }
}
