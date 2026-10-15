// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/recovery/PlaybackRecoveryTest.kt
// Purpose: Recovery skips one bad sentence, restarts a hung render, tolerates long calls, and stops a broken voice.

package io.github.skm0078.audora.playback.recovery

import io.github.skm0078.audora.core.config.QueueConfig
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.playback.contract.PlaybackErrorCode
import io.github.skm0078.audora.playback.queue.UnitKey
import io.github.skm0078.audora.speech.SpeechError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRecoveryTest {
    private val config = QueueConfig(renderRetryLimit = 2, stuckRetryLimit = 1)
    private val recovery = PlaybackRecovery(config)
    private val voice = VoiceId(EngineId.SHERPA, "test")
    private val synthesisFailed = SpeechError.SynthesisFailed(voice, "boom")

    private fun renderFailed(index: Int, error: SpeechError = synthesisFailed) =
        PlayerFault.RenderFailed(UnitKey(0, index), error)

    @Test
    fun oneBadSentenceIsSkipped() {
        assertEquals(RecoveryAction.SkipSentence, recovery.decide(renderFailed(0)))
    }

    @Test
    fun aVoiceFailingOnEverySentenceStopsAfterTheLimit() {
        assertEquals(RecoveryAction.SkipSentence, recovery.decide(renderFailed(0)))
        assertEquals(RecoveryAction.SkipSentence, recovery.decide(renderFailed(1)))
        val stop = recovery.decide(renderFailed(2))
        assertTrue(stop is RecoveryAction.Stop)
        assertEquals(PlaybackErrorCode.PLAYBACK_FAILED, (stop as RecoveryAction.Stop).code)
    }

    @Test
    fun aSentencePlayingToTheEndResetsTheCount() {
        recovery.decide(renderFailed(0))
        recovery.decide(renderFailed(1))
        recovery.onSentenceCompleted()
        assertEquals(RecoveryAction.SkipSentence, recovery.decide(renderFailed(2)))
    }

    @Test
    fun aMissingVoiceStopsWithVoiceUnavailable() {
        val missing = SpeechError.VoiceUnavailable(
            EngineId.SYSTEM,
            SpeechError.VoiceUnavailable.Reason.NOT_INSTALLED,
            lang = Lang.HI,
        )
        repeat(config.renderRetryLimit) { recovery.decide(renderFailed(it, missing)) }
        val stop = recovery.decide(renderFailed(9, missing)) as RecoveryAction.Stop
        assertEquals(PlaybackErrorCode.VOICE_UNAVAILABLE, stop.code)
    }

    @Test
    fun aReleasedEngineStopsAtOnce() {
        val action = recovery.decide(renderFailed(0, SpeechError.EngineUnavailable(EngineId.SHERPA, "released")))
        assertEquals(PlaybackErrorCode.VOICE_UNAVAILABLE, (action as RecoveryAction.Stop).code)
    }

    @Test
    fun aStuckSentenceFirstRestartsRenderingThenIsSkipped() {
        val stuck = PlayerFault.Stuck(UnitKey(3, 1), suppressed = false)
        assertEquals(RecoveryAction.RestartRendering, recovery.decide(stuck))
        assertEquals(RecoveryAction.SkipSentence, recovery.decide(stuck))
        assertEquals(
            "the next stuck sentence starts over",
            RecoveryAction.RestartRendering,
            recovery.decide(stuck.copy(key = UnitKey(3, 2))),
        )
    }

    @Test
    fun aLongPhoneCallIsRetriedEveryTime() {
        val suppressed = PlayerFault.Stuck(UnitKey(0, 0), suppressed = true)
        repeat(10) { assertEquals(RecoveryAction.Retry, recovery.decide(suppressed)) }
    }

    @Test
    fun anAudioOutputErrorIsRetriedOnceThenStops() {
        val other = PlayerFault.Other("ERROR_CODE_AUDIO_TRACK_INIT_FAILED")
        assertEquals(RecoveryAction.Retry, recovery.decide(other))
        assertEquals(PlaybackErrorCode.PLAYBACK_FAILED, (recovery.decide(other) as RecoveryAction.Stop).code)
    }
}
