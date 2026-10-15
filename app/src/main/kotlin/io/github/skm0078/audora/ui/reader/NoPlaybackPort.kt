// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/NoPlaybackPort.kt
// Purpose: Stand-in PlaybackPort until the playback service (P1-04) lands: the reader shows pages, no audio.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.logging.AudoraLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// TODO(P1-04): replace with a MediaController-backed port over playback.contract.PlaybackContract.
class NoPlaybackPort : PlaybackPort {
    override val state: StateFlow<PlaybackSnapshot> =
        MutableStateFlow(PlaybackSnapshot(PlaybackSnapshot.Connection.UNAVAILABLE)).asStateFlow()

    override fun start(position: ReadingPosition, password: String?) = ignored("start")

    override fun play() = ignored("play")

    override fun pause() = ignored("pause")

    override fun skipToNextSentence() = ignored("skip forward")

    override fun skipToPreviousSentence() = ignored("skip back")

    override fun setSpeed(speed: Float) = ignored("speed")

    override fun setSleepTimer(setting: SleepTimerSetting) = ignored("sleep timer")

    private fun ignored(action: String) {
        AudoraLog.w(TAG, "Ignored $action: no playback service in this build")
    }

    private companion object {
        const val TAG = "Audora.ReaderPlayer"
    }
}
