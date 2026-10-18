// File: core/src/test/kotlin/io/github/skm0078/audora/core/model/VoiceShapingTest.kt
// Purpose: The voice shaping line round-trips and a bad line falls back to the defaults.

package io.github.skm0078.audora.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceShapingTest {
    @Test
    fun `round trips`() {
        val shaping = VoiceShaping(
            evenLoudness = false,
            boostDb = 6,
            trimPauses = true,
            expressiveness = 4,
            characterVoices = true,
            blendWith = 11,
            blendPercent = 40,
            smartSpeed = true,
        )
        assertEquals(shaping, VoiceShaping.decode(shaping.encode()))
    }

    @Test
    fun `missing or bad values take the defaults`() {
        assertEquals(VoiceShaping(), VoiceShaping.decode(null))
        assertEquals(VoiceShaping(), VoiceShaping.decode("even=maybe;boost=-3;trim"))
        assertEquals(VoiceShaping(boostDb = 3), VoiceShaping.decode("boost=3"))
        assertEquals(VoiceShaping(), VoiceShaping.decode("expression=-1"))
        assertEquals(VoiceShaping(), VoiceShaping.decode("blendWith=-2;blendPercent=100"))
    }

    @Test
    fun `a line saved before smart speed existed keeps it off`() {
        assertEquals(false, VoiceShaping.decode("even=true;boost=3;trim=true").smartSpeed)
        assertEquals(false, VoiceShaping.decode("smart=sometimes").smartSpeed)
    }

    @Test
    fun `a line saved before expressiveness existed keeps the voices' own delivery`() {
        val old = VoiceShaping.decode("even=true;boost=0;trim=false")
        assertEquals(VoiceShaping.DEFAULT_EXPRESSION, old.expressiveness)
        assertEquals(false, old.characterVoices)
        assertEquals(null, old.blendWith)
    }

    @Test
    fun `cast voices round trip, names with equals signs and colons included`() {
        val shaping = VoiceShaping(
            dialogueVoices = mapOf(Lang.EN to VoiceId(EngineId.SHERPA, "kokoro=en:af"), Lang.HI to HINDI),
            headingVoices = mapOf(Lang.BN to VoiceId(EngineId.SYSTEM, "bn-in-x-bnm-local")),
        )
        assertEquals(shaping, VoiceShaping.decode(shaping.encode()))
    }

    @Test
    fun `a bad cast value means no cast voice, the rest kept`() {
        assertEquals(VoiceShaping(boostDb = 3), VoiceShaping.decode("boost=3;dialogue=%ZZ;heading=EN%3DNOPE%2Fx"))
    }

    private companion object {
        val HINDI = VoiceId(EngineId.SYSTEM, "hi-in-x-hia-local")
    }
}
