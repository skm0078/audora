// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/recovery/PlaybackRecovery.kt
// Purpose: Decides how playback recovers from a player error: retry, restart rendering, skip a sentence or stop.

package io.github.skm0078.audora.playback.recovery

import io.github.skm0078.audora.core.config.QueueConfig
import io.github.skm0078.audora.playback.contract.PlaybackErrorCode
import io.github.skm0078.audora.playback.queue.UnitKey
import io.github.skm0078.audora.speech.SpeechError

/** A player error, classified. */
sealed interface PlayerFault {
    /** The engine failed on the sentence at [key]. */
    data class RenderFailed(val key: UnitKey, val error: SpeechError) : PlayerFault

    /**
     * Media3's stuck-player detector fired (S3 finding 3). [suppressed]: playback was held by a transient
     * audio-focus loss (e.g. a long phone call) for 10 minutes, which is not our fault and not a hang.
     */
    data class Stuck(val key: UnitKey?, val suppressed: Boolean) : PlayerFault

    /** Anything else: audio output, extractor, an unexpected source error. */
    data class Other(val errorCodeName: String) : PlayerFault
}

sealed interface RecoveryAction {
    /** Prepare the player again where it is. */
    data object Retry : RecoveryAction

    /** Rebuild the playlist and render queue from the current sentence (cancels a hung render). */
    data object RestartRendering : RecoveryAction

    /** Move on to the next sentence. */
    data object SkipSentence : RecoveryAction

    /** Give up: stop reading and tell the user why. */
    data class Stop(val code: PlaybackErrorCode, val message: String) : RecoveryAction
}

/**
 * Bounded recovery: one bad sentence is skipped, a voice that fails on every sentence stops reading after
 * [QueueConfig.renderRetryLimit] skips in a row instead of silently skipping a whole book. A sentence that
 * plays to the end ([onSentenceCompleted]) resets every counter. Main thread only.
 */
class PlaybackRecovery(private val config: QueueConfig) {
    private var skipsInARow = 0
    private var stuckKey: UnitKey? = null
    private var stuckCount = 0
    private var otherRetries = 0

    fun onSentenceCompleted() {
        skipsInARow = 0
        stuckKey = null
        stuckCount = 0
        otherRetries = 0
    }

    fun decide(fault: PlayerFault): RecoveryAction = when (fault) {
        is PlayerFault.RenderFailed -> renderFailed(fault)
        is PlayerFault.Stuck -> stuck(fault)
        is PlayerFault.Other -> other(fault)
    }

    private fun renderFailed(fault: PlayerFault.RenderFailed): RecoveryAction {
        if (fault.error is SpeechError.EngineUnavailable) {
            return RecoveryAction.Stop(PlaybackErrorCode.VOICE_UNAVAILABLE, fault.error.message)
        }
        val code = if (fault.error is SpeechError.VoiceUnavailable) {
            PlaybackErrorCode.VOICE_UNAVAILABLE
        } else {
            PlaybackErrorCode.PLAYBACK_FAILED
        }
        return skip(code, fault.error.message)
    }

    private fun stuck(fault: PlayerFault.Stuck): RecoveryAction {
        if (fault.suppressed) return RecoveryAction.Retry
        if (fault.key != stuckKey) {
            stuckKey = fault.key
            stuckCount = 0
        }
        stuckCount++
        return if (stuckCount <= config.stuckRetryLimit) {
            RecoveryAction.RestartRendering
        } else {
            stuckKey = null
            stuckCount = 0
            skip(PlaybackErrorCode.PLAYBACK_FAILED, "The voice stopped responding on ${fault.key ?: "the next page"}")
        }
    }

    private fun other(fault: PlayerFault.Other): RecoveryAction {
        otherRetries++
        return if (otherRetries <= OTHER_RETRY_LIMIT) {
            RecoveryAction.Retry
        } else {
            RecoveryAction.Stop(PlaybackErrorCode.PLAYBACK_FAILED, "Playback failed: ${fault.errorCodeName}")
        }
    }

    private fun skip(code: PlaybackErrorCode, message: String): RecoveryAction {
        skipsInARow++
        return if (skipsInARow <=
            config.renderRetryLimit
        ) {
            RecoveryAction.SkipSentence
        } else {
            RecoveryAction.Stop(code, message)
        }
    }

    private companion object {
        /** An audio-output error (e.g. a Bluetooth device switching) usually clears on one re-prepare. */
        const val OTHER_RETRY_LIMIT = 1
    }
}
