// File: core/src/test/kotlin/io/github/skm0078/audora/core/config/VoiceShapingConfigTest.kt
// Purpose: Expressiveness steps map to Piper's noise values, the default step is the voices' own, out of range clamps;
//          a model with styles takes calmer steps.

package io.github.skm0078.audora.core.config

import io.github.skm0078.audora.core.model.VoiceShaping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceShapingConfigTest {
    private val config = VoiceShapingConfig()

    @Test
    fun `the default step is the voices' own noise values`() {
        assertEquals(PIPER_NOISE_SCALE to PIPER_NOISE_W, config.expressionNoise(VoiceShaping.DEFAULT_EXPRESSION))
    }

    @Test
    fun `steps grow livelier from newsreader to storyteller`() {
        val noise = (0..config.maxExpression).map(config::expressionNoise)
        assertTrue(noise.zipWithNext().all { (a, b) -> b.first > a.first && b.second > a.second })
    }

    @Test
    fun `a step past either end clamps`() {
        assertEquals(config.expressionNoise(0), config.expressionNoise(-3))
        assertEquals(config.expressionNoise(config.maxExpression), config.expressionNoise(config.maxExpression + 5))
    }

    @Test
    fun `a model with styles reads calmer at every step, at the listener's values by default`() {
        val default = config.expressionNoise(VoiceShaping.DEFAULT_EXPRESSION, styled = true)
        assertEquals(STYLED_NOISE_SCALE to STYLED_NOISE_W, default)
        (0..config.maxExpression).forEach { step ->
            val styled = config.expressionNoise(step, styled = true)
            val plain = config.expressionNoise(step)
            assertTrue("step $step", styled.first < plain.first && styled.second < plain.second)
        }
    }

    @Test
    fun `styled steps grow livelier too, one per plain step`() {
        val noise = (0..config.maxExpression).map { config.expressionNoise(it, styled = true) }
        assertTrue(noise.zipWithNext().all { (a, b) -> b.first > a.first && b.second > a.second })
        assertThrows(IllegalArgumentException::class.java) { VoiceShapingConfig(styledNoiseScales = listOf(0.3f)) }
    }

    @Test
    fun `scales and widths must pair up`() {
        assertThrows(IllegalArgumentException::class.java) {
            VoiceShapingConfig(expressionNoiseScales = listOf(0.5f, 0.6f, 0.7f), expressionNoiseWidths = listOf(0.8f))
        }
    }

    private companion object {
        const val PIPER_NOISE_SCALE = 0.667f
        const val PIPER_NOISE_W = 0.8f

        /** What a native listener found natural for Rasa (ADR 2026-10-30-bengali-spoken-spelling). */
        const val STYLED_NOISE_SCALE = 0.35f
        const val STYLED_NOISE_W = 0.4f
    }
}
