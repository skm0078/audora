// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/focus/FocusSoundCues.kt
// Purpose: Tells the focus sound (plan item 72) when the book was sought, stalled or reached its last sentence.

package io.github.skm0078.audora.playback.focus

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener

/**
 * Main thread (the player's listeners). A seek or a new playlist (a jump, a voice or speed change) makes the sound
 * come back from silence, and so does an underrun (a render that fell behind: the voice and the sound stopped).
 * Whether the item playing is the last one is kept up to date as items play and the playlist grows, so the sound
 * fades out only after the book's last sentence ([FocusSoundProcessor.endsAfterCurrent]).
 */
@OptIn(UnstableApi::class)
class FocusSoundCues(private val player: Player, private val processor: FocusSoundProcessor) :
    Player.Listener,
    AnalyticsListener {

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) processor.fadeIn()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) processor.fadeIn()
        markLast()
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) = markLast()

    override fun onAudioUnderrun(
        eventTime: AnalyticsListener.EventTime,
        bufferSize: Int,
        bufferSizeMs: Long,
        elapsedSinceLastFeedMs: Long,
    ) = processor.fadeIn()

    private fun markLast() = processor.endsAfterCurrent(!player.hasNextMediaItem())

    companion object {
        /** Listens to [player] for [processor] until the player is released. */
        fun attach(player: ExoPlayer, processor: FocusSoundProcessor) {
            val cues = FocusSoundCues(player, processor)
            player.addListener(cues)
            player.addAnalyticsListener(cues)
        }
    }
}
