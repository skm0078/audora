// File: core/src/test/kotlin/io/github/skm0078/audora/core/config/SpeechStyleConfigTest.kt
// Purpose: Rates and pitches snap to the sliders' steps, speed splits between model and player, paces size the
//          silences.

package io.github.skm0078.audora.core.config

import io.github.skm0078.audora.core.model.ClauseMark
import io.github.skm0078.audora.core.model.Pace
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.model.VoiceShaping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
            assertEquals("player at $rate", split.second, config.playerSpeed(SpeechStyle(rate = rate)), EPS)
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
    fun `clause pauses follow the pace and the speed`() {
        val cases = listOf(
            SpeechStyle(pace = Pace.NORMAL) to (ClauseMark.DASH to 250),
            SpeechStyle(pace = Pace.TIGHT) to (ClauseMark.COLON to 144),
            SpeechStyle(pace = Pace.RELAXED) to (ClauseMark.ELLIPSIS to 720),
            // 250 ms / sqrt(2), lengthened by the player's 2 / 1.5 stretch.
            SpeechStyle(rate = 2f) to (ClauseMark.DASH to 236),
        )
        cases.forEach { (style, expected) ->
            val (mark, ms) = expected
            assertEquals("$mark at $style", ms, config.clauseSilenceMs(mark, style))
        }
    }

    @Test
    fun `smart speed moves the player's stretch to the voice and stops lengthening pauses for it`() {
        val cases = listOf(1f to 1f, 1.5f to 1f, 2.25f to 1.5f, 3f to 2f)
        cases.forEach { (rate, stretch) ->
            val smart = SpeechStyle(rate = rate, shaping = VoiceShaping(smartSpeed = true))
            val plain = SpeechStyle(rate = rate)
            assertEquals("player at $rate", 1f, config.playerSpeed(smart), EPS)
            assertEquals("voice at $rate", stretch, config.voiceStretch(smart), EPS)
            assertEquals("plain player at $rate", stretch, config.playerSpeed(plain), EPS)
            assertEquals("plain voice at $rate", 1f, config.voiceStretch(plain), EPS)
        }
        // Never stacked: the model, the voice and the player together make exactly the asked speed.
        listOf(0.75f, 1.5f, 2.25f, 3f).forEach { rate ->
            listOf(true, false).forEach { on ->
                val style = SpeechStyle(rate = rate, shaping = VoiceShaping(smartSpeed = on))
                val heard = config.modelRate(rate) * config.voiceStretch(style) * config.playerSpeed(style)
                assertEquals("heard at $rate, smart $on", rate, heard, EPS)
            }
        }
        // At or below the model's cap smart speed stretches nothing, so it is heard as off.
        val slow = SpeechStyle(rate = 1.5f, shaping = VoiceShaping(smartSpeed = true))
        assertEquals(config.asHeard(slow.copy(shaping = VoiceShaping())), config.asHeard(slow))
        val fast = slow.copy(rate = 2f)
        assertEquals(fast, config.asHeard(fast))
        // Heard silence is the same either way: 150 ms / sqrt(3), with no player stretch to survive.
        val smart = SpeechStyle(rate = 3f, pace = Pace.NORMAL, shaping = VoiceShaping(smartSpeed = true))
        assertEquals(87, config.silenceAfterMs(0, smart))
        assertEquals(177, config.clauseSilenceMs(ClauseMark.DASH, smart.copy(rate = 2f)))
    }

    @Test
    fun `a replay re-renders only when smart speed is stretching the voice`() {
        val smart = SpeechStyle(rate = 3f, shaping = VoiceShaping(smartSpeed = true))
        // Half of 3x: the model speaks 1.5x itself, nothing is stretched twice.
        val replay = requireNotNull(config.replayStyle(smart, 0.5f))
        assertEquals(1.5f, replay.rate, EPS)
        assertEquals(1f, config.voiceStretch(replay), EPS)
        assertEquals(smart.shaping, replay.shaping)
        assertEquals(null, config.replayStyle(smart.copy(shaping = VoiceShaping()), 0.5f))
        assertEquals(null, config.replayStyle(smart.copy(rate = 1.25f), 0.5f))
        // Never below the slowest rate offered.
        assertEquals(config.minRate, requireNotNull(config.replayStyle(smart.copy(rate = 2f), 0.1f)).rate, EPS)
    }

    @Test
    fun `pitches are clamped and snapped to the step`() {
        val cases = listOf(
            1f to 1f,
            1.12f to 1.1f,
            0.83f to 0.85f,
            0.1f to 0.8f,
            2f to 1.25f,
            Float.NaN to 1f,
            Float.NEGATIVE_INFINITY to 1f,
        )
        cases.forEach { (pitch, expected) -> assertEquals("pitch $pitch", expected, config.clampPitch(pitch), 0f) }
    }

    @Test
    fun `a pitch change renders nothing again, a rate change does`() {
        val style = SpeechStyle(rate = 1.25f)

        assertEquals(config.asRendered(style), config.asRendered(style.copy(pitch = 1.2f)))
        assertNotEquals(config.asRendered(style), config.asRendered(style.copy(rate = 1.3f)))
        assertEquals(SpeechStyle.NORMAL_PITCH, config.asRendered(style.copy(pitch = 0.9f)).pitch)
    }

    @Test
    fun `bad ranges are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { SpeechStyleConfig(minRate = 0f) }
        assertThrows(IllegalArgumentException::class.java) { SpeechStyleConfig(minRate = 2f, maxRate = 1f) }
        assertThrows(IllegalArgumentException::class.java) { SpeechStyleConfig(rateStep = 0f) }
        assertThrows(IllegalArgumentException::class.java) { SpeechStyleConfig(paces = emptyMap()) }
        assertThrows(IllegalArgumentException::class.java) { SpeechStyleConfig(maxModelRate = 0.8f) }
        assertThrows(IllegalArgumentException::class.java) { SpeechStyleConfig(clausePausesMs = emptyMap()) }
        assertThrows(IllegalArgumentException::class.java) { SpeechStyleConfig(asideGainDb = 3f) }
        assertThrows(IllegalArgumentException::class.java) { SpeechStyleConfig(minPitch = 0f) }
        assertThrows(IllegalArgumentException::class.java) { SpeechStyleConfig(minPitch = 1.1f) }
        assertThrows(IllegalArgumentException::class.java) { SpeechStyleConfig(pitchStep = 0f) }
    }

    private companion object {
        const val EPS = 1e-6f
    }
}
