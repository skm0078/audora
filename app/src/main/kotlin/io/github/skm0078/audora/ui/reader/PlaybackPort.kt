// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/PlaybackPort.kt
// Purpose: What the reader needs from the playback service, so the reader logic is tested without Media3.

package io.github.skm0078.audora.ui.reader

import kotlinx.coroutines.flow.StateFlow

/** Implemented over a Media3 `MediaController` by [MediaControllerPlaybackPort]; faked in tests. */
interface PlaybackPort {
    val state: StateFlow<PlaybackSnapshot>

    /** Replaces whatever is playing with this book from [position] and plays. Only ever on a user action (C7). */
    fun start(position: ReadingPosition, password: String?)

    fun play()

    fun pause()

    fun skipToNextSentence()

    fun skipToPreviousSentence()

    fun setSpeed(speed: Float)

    fun setSleepTimer(setting: SleepTimerSetting)
}

/**
 * The player as the reader sees it.
 *
 * @property position the sentence being read (or about to be, while buffering); null when nothing is loaded
 *   or the item is not an Audora position.
 * @property wantsToPlay play was pressed and not paused since: true while buffering the next sentence too.
 * @property errorCode Media3's error code name of the last playback error, for the log and a generic message.
 */
data class PlaybackSnapshot(
    val connection: Connection,
    val position: ReadingPosition? = null,
    val wantsToPlay: Boolean = false,
    val buffering: Boolean = false,
    val speed: Float = 1f,
    val canSkipBack: Boolean = false,
    val canSkipForward: Boolean = false,
    val sleepTimer: SleepTimerState = SleepTimerState.Off,
    val errorCode: String? = null,
) {
    enum class Connection { CONNECTING, CONNECTED, UNAVAILABLE }

    companion object {
        val Connecting = PlaybackSnapshot(Connection.CONNECTING)
    }
}
