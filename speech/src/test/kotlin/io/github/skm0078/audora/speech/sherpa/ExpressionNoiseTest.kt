// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/sherpa/ExpressionNoiseTest.kt
// Purpose: The listener's expressiveness reaches each VITS voice, the calmer values a model with styles.

package io.github.skm0078.audora.speech.sherpa

import io.github.skm0078.audora.core.model.Feeling
import io.github.skm0078.audora.core.model.Lang
import org.junit.Assert.assertEquals
import org.junit.Test

class ExpressionNoiseTest {
    private val plain = VitsNoise(0.667f, 0.8f)
    private val calm = VitsNoise(0.35f, 0.4f)
    private val piper = spec("piper-en-cori")
    private val rasa = spec("rasa-bn", Lang.BN, speakerId = 2).copy(styles = mapOf(Feeling.NEUTRAL to 3))
    private val kokoro = spec("kokoro-en-heart").copy(family = SherpaFamily.KOKORO)

    @Test
    fun `a plain VITS voice takes the plain values and one with styles the calmer ones`() {
        val voices = listOf(piper, rasa, kokoro).withNoise { styled -> if (styled) calm else plain }

        assertEquals(listOf(plain, calm, null), voices.map { it.noise })
    }

    @Test
    fun `no values leave every voice as installed`() {
        val voices = listOf(piper, rasa, kokoro)

        assertEquals(voices, voices.withNoise { null })
    }
}
