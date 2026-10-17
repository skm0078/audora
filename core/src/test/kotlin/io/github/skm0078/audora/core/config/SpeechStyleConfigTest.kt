// File: core/src/test/kotlin/io/github/skm0078/audora/core/config/SpeechStyleConfigTest.kt
// Purpose: Rates snap to the slider's steps, speed splits between model and player, paces size the silences.

package io.github.skm0078.audora.core.config

import io.github.skm0078.audora.core.model.Pace
import io.github.skm0078.audora.core.model.SpeechStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SpeechStyleConfigTest {
    private val config = SpeechStyleConfig()

    @Test
    fun `rates are clamped and snapped to the step`() {
        val cases = listOf(
            1f to 1f,
            1.25f to 1.25f,
            1.33f to 1.35f,
            0.1f to 0.5f,
            4f to 3f,
            Float.NaN to 1f,
            Float.POSITIVE_INFINITY to 1f,
        )
        cases.forEach { (rate, expected) -> assertEquals("rate $rate", expected, config.clampRate(rate), 0f) }
    }

    @Test
    fun `the model speaks up to its cap and the player stretches the rest`() {
        val cases = listOf(
            0.5f to (0.5f to 1f),
            1f to (1f to 1f),
            1.5f to (1.5f to 1f),
            2.25f to (1.5f to 1.5f),
            3f to (1.5f to 2f),
        )
        cases.forEach { (rate, split) ->
            assertEquals("model at $rate", split.first, config.modelRate(rate), EPS)
            assertEquals("player at $rate", split.second, config.playerSpeed(rate), EPS)
        }
    }

    @Test
    fun `each pace sizes the silences at normal speed`() {
        val cases = listOf(
            Triple(Pace.TIGHT, 400, 240),
            Triple(Pace.TIGHT, 0, 0),
            Triple(Pace.NORMAL, 0, 150),
            Triple(Pace.NORMAL, 400, 650),
            Triple(Pace.RELAXED, 0, 400),
            Triple(Pace.RELAXED, 600, 1480),
        )
        cases.forEach { (pace, pause, expected) ->
            assertEquals("$pace after $pause", expected, config.silenceAfterMs(pause, SpeechStyle(pace = pace)))
        }
    }

    @Test
    fun `pauses shrink with the square root of the speed`() {
        // Heard silence = clip silence / player speed = 150 ms / sqrt(rate).
        val cases = listOf(0.5f to 212, 2f to 141, 3f to 173)
        cases.forEach { (rate, expected) ->
            assertEquals("rate $rate", expected, config.silenceAfterMs(0, SpeechStyle(rate, Pace.NORMAL)))
        }
    }

    @Test
    fun `bad ranges are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { SpeechStyleConfig(minRate = 0f) }
        assertThrows(IllegalArgumentException::class.java) { SpeechStyleConfig(minRate = 2f, maxRate = 1f) }
        assertThrows(IllegalArgumentException::class.java) { SpeechStyleConfig(rateStep = 0f) }
        assertThrows(IllegalArgumentException::class.java) { SpeechStyleConfig(paces = emptyMap()) }
        assertThrows(IllegalArgumentException::class.java) { SpeechStyleConfig(maxModelRate = 0.8f) }
    }

    private companion object {
        const val EPS = 1e-6f
    }
}
