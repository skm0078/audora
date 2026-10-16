// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/voices/VoiceSpeedsTest.kt
// Purpose: Measured voice speeds: averaging, the too-slow verdict, saving and reading back, and bad files (P4-02).

package io.github.skm0078.audora.speech.voices

import io.github.skm0078.audora.core.config.VoiceSpeedConfig
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.VoiceId
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSpeedsTest {
    private val dir: File = Files.createTempDirectory("speeds").toFile()
    private val file = File(dir, "voice-speeds.properties")
    private val config = VoiceSpeedConfig(horizonSeconds = 100.0, minMeasuredSeconds = 30.0, saveEveryRenders = 2)
    private val kokoro = VoiceId(EngineId.SHERPA, "kokoro-en-heart")

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun sample(rtf: Double, seconds: Double = 10.0, chars: Int = 150) =
        SpeedSample(chars = chars, renderSeconds = rtf * seconds, audioSeconds = seconds)

    @Test
    fun `the first render sets the speed, later ones move it by their share of the horizon`() {
        val first = VoiceSpeed.first(sample(rtf = 0.4))
        val next = first.plus(sample(rtf = 1.4), config) // 10 s of a 100 s horizon: 10 % of the way

        assertEquals(0.4, first.rtf, EPS)
        assertEquals(15.0, first.charsPerSecond, EPS)
        assertEquals(0.5, next.rtf, EPS)
        assertEquals(20.0, next.measuredSeconds, EPS)
    }

    @Test
    fun `a render longer than the horizon replaces the average`() {
        val speed = VoiceSpeed.first(sample(rtf = 0.2)).plus(sample(rtf = 0.9, seconds = 500.0), config)

        assertEquals(0.9, speed.rtf, EPS)
    }

    @Test
    fun `too slow only once enough audio was measured`() {
        val oneSentence = VoiceSpeed(rtf = 2.0, charsPerSecond = 15.0, measuredSeconds = 8.0)
        val measured = oneSentence.copy(measuredSeconds = 40.0)
        val fastEnough = measured.copy(rtf = 0.79)

        assertFalse(oneSentence.tooSlow(config))
        assertTrue(measured.tooSlow(config))
        assertFalse(fastEnough.tooSlow(config))
        assertTrue(measured.copy(rtf = config.maxUsableRtf).tooSlow(config))
    }

    @Test
    fun `speeds survive a restart and are saved every few renders`() {
        val speeds = VoiceSpeeds(file, config)
        speeds.record(kokoro, sample(rtf = 1.5))
        assertFalse("one render is not yet saved", file.exists())
        speeds.record(kokoro, sample(rtf = 1.5))

        val reread = VoiceSpeeds(file, config).get(kokoro)
        assertEquals(1.5, requireNotNull(reread).rtf, EPS)
        assertEquals(20.0, reread.measuredSeconds, EPS)
    }

    @Test
    fun `forgetting a deleted voice removes it on disk too`() {
        val speeds = VoiceSpeeds(file, config)
        speeds.record(kokoro, sample(rtf = 1.5))
        speeds.forget(kokoro)

        assertNull(speeds.get(kokoro))
        assertNull(VoiceSpeeds(file, config).get(kokoro))
    }

    @Test
    fun `an unreadable entry is skipped and the rest still load`() {
        file.writeText(
            """
            SHERPA\:good.rtf=0.3
            SHERPA\:good.charsPerSecond=14
            SHERPA\:good.measuredSeconds=60
            SHERPA\:bad.rtf=fast
            SHERPA\:bad.charsPerSecond=14
            SHERPA\:bad.measuredSeconds=60
            NOPE\:x.rtf=0.3
            """.trimIndent(),
        )
        val speeds = VoiceSpeeds(file, config).all.value

        assertEquals(setOf(VoiceId(EngineId.SHERPA, "good")), speeds.keys)
    }

    @Test
    fun `no file means nothing measured`() {
        assertTrue(VoiceSpeeds(file, config).all.value.isEmpty())
    }

    private companion object {
        const val EPS = 1e-9
    }
}
