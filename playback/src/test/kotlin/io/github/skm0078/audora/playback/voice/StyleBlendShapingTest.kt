// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/voice/StyleBlendShapingTest.kt
// Purpose: The saved narrator blend (item 75) maps to the engine's blend: speaker kept, percent as a fraction.

package io.github.skm0078.audora.playback.voice

import io.github.skm0078.audora.core.model.VoiceShaping
import io.github.skm0078.audora.speech.sherpa.StyleBlend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StyleBlendShapingTest {
    @Test
    fun `the percent becomes a fraction of the other speaker, and no speaker means no blend`() {
        listOf(1 to 0.01f, 30 to 0.3f, 99 to 0.99f).forEach { (percent, amount) ->
            val blend = VoiceShaping(blendWith = 12, blendPercent = percent).styleBlend()
            assertEquals(StyleBlend(12, amount), blend)
        }
        assertNull(VoiceShaping(blendPercent = 40).styleBlend())
    }
}
