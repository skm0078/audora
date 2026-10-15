// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/sleep/PlayerSleepTimer.kt
// Purpose: Runs a SleepTimer on the session's Media3 player and answers the reader's sleep-timer command.

package io.github.skm0078.audora.playback.sleep

import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.SessionResult
import io.github.skm0078.audora.playback.contract.PlaybackAddress
import io.github.skm0078.audora.playback.contract.ReaderSessionCommands
import io.github.skm0078.audora.playback.contract.SleepTimerState

/**
 * The playback service creates one per player and calls [handle] for [ReaderSessionCommands.SET_SLEEP_TIMER]:
 *
 * ```
 * val sleepTimer = PlayerSleepTimer(exoPlayer, exoPlayer::setPauseAtEndOfMediaItems) { state ->
 *     session.sessionExtras = Bundle(session.sessionExtras).apply {
 *         ReaderSessionCommands.putSleepTimerState(this, state)
 *     }
 * }
 * ```
 *
 * If the service swaps the session's player, release this one and create a new one for the new player.
 * Media ids must be [PlaybackAddress]es for "end of page" to see page boundaries.
 */
class PlayerSleepTimer(
    private val player: Player,
    private val setPauseAtEndOfItems: (Boolean) -> Unit,
    private val publish: (SleepTimerState) -> Unit,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : Player.Listener {
    private val handler = Handler(player.applicationLooper)
    private val timer = SleepTimer(Host())

    val state: SleepTimerState get() = timer.state

    init {
        player.addListener(this)
    }

    fun handle(args: Bundle): SessionResult {
        val setting = ReaderSessionCommands.sleepTimerSettingFrom(args)
            ?: return SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE)
        timer.set(setting)
        return SessionResult(SessionResult.RESULT_SUCCESS)
    }

    fun release() {
        player.removeListener(this)
        timer.release()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = timer.onItemOrPlaylistChanged()

    override fun onTimelineChanged(timeline: Timeline, reason: Int) = timer.onItemOrPlaylistChanged()

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (playWhenReady) {
            timer.onPlaybackResumed()
        } else if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
            timer.onPausedAtEndOfItem()
        } else {
            timer.onPausedByUser()
        }
    }

    private inner class Host : SleepTimerHost {
        override fun nowElapsedMs(): Long = clock()

        override fun isPlaying(): Boolean = player.playWhenReady

        override fun isLastUnitOfPage(): Boolean {
            val current = PlaybackAddress.parse(player.currentMediaItem?.mediaId) ?: return false
            val next = if (player.hasNextMediaItem()) {
                PlaybackAddress.parse(player.getMediaItemAt(player.nextMediaItemIndex).mediaId)
            } else {
                null
            }
            return next == null || !next.isOnPage(current.bookId, current.page)
        }

        override fun setPauseAtEndOfItems(enabled: Boolean) = setPauseAtEndOfItems.invoke(enabled)

        override fun schedule(delayMs: Long, action: () -> Unit): Cancellable {
            val runnable = Runnable(action)
            handler.postDelayed(runnable, delayMs)
            return Cancellable { handler.removeCallbacks(runnable) }
        }

        override fun publish(state: SleepTimerState) = publish.invoke(state)
    }
}
