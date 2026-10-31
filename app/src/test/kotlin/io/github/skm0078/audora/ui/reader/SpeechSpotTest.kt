// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/SpeechSpotTest.kt
// Purpose: The Voice and speed sheet's groups open in place in a fixed order, the player's shortcuts land on groups
//          that exist, and each header names what is on (ADR 2026-10-29-voice-and-speed-one-level).

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.model.VoiceShaping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechSpotTest {
    @Test
    fun `the groups that open in place keep the sheet's order`() {
        assertEquals(
            listOf(SpeechSpot.READING, SpeechSpot.SOUND, SpeechSpot.CAST, SpeechSpot.STUDY, SpeechSpot.FOCUS),
            SpeechSpot.groups,
        )
    }

    @Test
    fun `the top and the voice list are places to scroll to, not groups`() {
        assertTrue(SpeechSpot.TOP !in SpeechSpot.groups && SpeechSpot.VOICE !in SpeechSpot.groups)
    }

    @Test
    fun `the player's shortcuts open Voices, What to read and Focus sound, in that order`() {
        assertEquals(
            listOf(SpeechSpot.VOICE, SpeechSpot.READING, SpeechSpot.FOCUS),
            SpeechShortcut.entries.map { it.spot },
        )
    }

    @Test
    fun `the Sound header names only the switches that are on`() {
        val shaping = VoiceShaping(evenLoudness = false, trimPauses = true, smartSpeed = true)
        assertEquals(
            listOf(R.string.speech_clause_pauses, R.string.speech_trim_pauses, R.string.speech_smart_speed),
            soundSummary(shaping, clausePauses = true),
        )
    }

    @Test
    fun `with every Sound switch off the header has nothing to name`() {
        val shaping = VoiceShaping(evenLoudness = false, trimPauses = false, smartSpeed = false)
        assertEquals(emptyList<Int>(), soundSummary(shaping, clausePauses = false))
    }

    @Test
    fun `the Cast header names the roles with a voice of their own`() {
        val voice = VoiceId(EngineId.SHERPA, "loona")
        assertEquals(
            listOf(R.string.speech_cast_dialogue, R.string.speech_cast_characters),
            castSummary(VoiceShaping(dialogueVoices = mapOf(Lang.EN to voice), characterVoices = true)),
        )
        assertEquals(emptyList<Int>(), castSummary(VoiceShaping()))
    }
}
