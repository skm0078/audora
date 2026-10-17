// File: core/src/test/kotlin/io/github/skm0078/audora/core/model/VoiceShapingTest.kt
// Purpose: The voice shaping line round-trips and a bad line falls back to the defaults.

package io.github.skm0078.audora.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceShapingTest {
    @Test
    fun `round trips`() {
        val shaping = VoiceShaping(evenLoudness = false, boostDb = 6, trimPauses = true)
        assertEquals(shaping, VoiceShaping.decode(shaping.encode()))
    }

    @Test
    fun `missing or bad values take the defaults`() {
        assertEquals(VoiceShaping(), VoiceShaping.decode(null))
        assertEquals(VoiceShaping(), VoiceShaping.decode("even=maybe;boost=-3;trim"))
        assertEquals(VoiceShaping(boostDb = 3), VoiceShaping.decode("boost=3"))
    }
}
