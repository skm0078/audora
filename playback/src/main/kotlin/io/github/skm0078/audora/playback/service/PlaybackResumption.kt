// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/service/PlaybackResumption.kt
// Purpose: Media3 playback resumption: a headset's play button or the system's media card resumes the last book.

package io.github.skm0078.audora.playback.service

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.skm0078.audora.core.library.Library
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.resume.ResumeAnchorStore
import io.github.skm0078.audora.core.resume.ResumeLocator
import io.github.skm0078.audora.playback.async.launchedFuture
import io.github.skm0078.audora.playback.session.BookSession
import kotlinx.coroutines.CoroutineScope

/**
 * Called by Media3 only when a controller asks to play while nothing is loaded: a Bluetooth or wired headset's
 * play button after Audora was closed, or the system's resumption card (Build Spec §6 "Playback resumption").
 * Both are user actions, so starting audio here keeps C7. Resumes the book with the newest anchor, at that
 * anchor; encrypted books cannot resume this way (their password is never stored) and are logged.
 *
 * Main thread.
 */
@OptIn(UnstableApi::class)
class PlaybackResumption(
    private val anchors: ResumeAnchorStore,
    private val library: Library,
    private val locator: ResumeLocator,
    private val scope: CoroutineScope,
    private val player: Player,
    private val bookSession: () -> BookSession,
) {
    /**
     * Always answers Media3: a store, library or book that throws (a damaged database, a file gone mid-open) fails
     * the future and is logged, instead of ending the service before its notification is up.
     */
    fun resume(isForPlayback: Boolean): ListenableFuture<MediaItemsWithStartPosition> =
        scope.launchedFuture(TAG, "Resumption") { result ->
            val anchor = anchors.latest()
            when {
                anchor == null -> result.setException(UnsupportedOperationException("Nothing read yet"))

                // System UI only wants something to show on its resumption card; nothing is opened or played.
                !isForPlayback -> result.set(preview(anchor.bookId.let { library.book(it)?.title }))

                else -> when (val resumed = bookSession().resume(anchor.bookId, locator)) {
                    // The session already set the playlist and plays; hand Media3 the same items so it keeps them.
                    is Outcome.Success -> result.set(currentPlaylist())

                    is Outcome.Failure -> {
                        AudoraLog.w(TAG, "Resumption failed, ${anchor.logLabel}: ${resumed.error.code}")
                        result.setException(UnsupportedOperationException(resumed.error.code.name))
                    }
                }
            }
            AudoraLog.i(TAG, "Resumption requested, forPlayback=$isForPlayback book=${anchor?.bookId?.logLabel}")
        }

    private fun preview(title: String?): MediaItemsWithStartPosition {
        val item = MediaItem.Builder()
            .setMediaId(PREVIEW_MEDIA_ID)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .build()
        return MediaItemsWithStartPosition(listOf(item), 0, C.TIME_UNSET)
    }

    private fun currentPlaylist(): MediaItemsWithStartPosition = playlistOf(player)

    companion object {
        private const val TAG = "Playback.Resume"
        private const val PREVIEW_MEDIA_ID = "audora-resume-preview"

        /** What [player] holds now, for Media3 to keep after the book session already set and started it. */
        fun playlistOf(player: Player): MediaItemsWithStartPosition {
            val items = (0 until player.mediaItemCount).map(player::getMediaItemAt)
            return MediaItemsWithStartPosition(items, player.currentMediaItemIndex, C.TIME_UNSET)
        }

        /** For callers that cannot resume at all (no session yet). */
        fun unsupported(): ListenableFuture<MediaItemsWithStartPosition> =
            Futures.immediateFailedFuture(UnsupportedOperationException())
    }
}
