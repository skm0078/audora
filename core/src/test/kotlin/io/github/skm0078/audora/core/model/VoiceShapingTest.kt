// File: core/src/test/kotlin/io/github/skm0078/audora/core/model/VoiceShapingTest.kt
// Purpose: The voice shaping line round-trips and a bad line falls back to the defaults.

package io.github.skm0078.audora.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceShapingTest {
    @Test
    fun `round trips`() {
        val shaping = VoiceShaping(evenLoudness = false, boostDb = 6, trimPauses = true, expressiveness = 4)
        assertEquals(shaping, VoiceShaping.decode(shaping.encode()))
    }

    @Test
    fun `missing or bad values take the defaults`() {
        assertEquals(VoiceShaping(), VoiceShaping.decode(null))
        assertEquals(VoiceShaping(), VoiceShaping.decode("even=maybe;boost=-3;trim"))
        assertEquals(VoiceShaping(boostDb = 3), VoiceShaping.decode("boost=3"))
        assertEquals(VoiceShaping(), VoiceShaping.decode("expression=-1"))
    }

    @Test
    fun `a line saved before expressiveness existed keeps the voices' own delivery`() {
        val old = VoiceShaping.decode("even=true;boost=0;trim=false")
        assertEquals(VoiceShaping.DEFAULT_EXPRESSION, old.expressiveness)
    }
}
