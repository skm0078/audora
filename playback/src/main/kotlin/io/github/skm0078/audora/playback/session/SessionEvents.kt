// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/session/SessionEvents.kt
// Purpose: What the book session reports outward: reading or not (diagnostics) and its state (session extras).

package io.github.skm0078.audora.playback.session

import androidx.media3.common.Player
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.playback.contract.PlaybackErrorCode
import io.github.skm0078.audora.playback.contract.SleepTimerMode

/** Main thread. */
interface SessionEvents {
    /** Audio is playing or buffering to play: P1-07's `onPlaybackActive(book, page)`. Sent on change only. */
    fun onReading(bookId: String, page: Int)

    /** Paused, stopped, ended, failed or slept: P1-07's `onPlaybackInactive()`. */
    fun onNotReading()

    fun onStateChanged(state: SessionState)

    /** A sentence became the current item (about to be read): P1-06 saves it as the book's resume anchor. */
    fun onSentenceStarted(bookId: BookId, unit: ReadingUnit) = Unit
}

data class SessionError(val code: PlaybackErrorCode, val message: String)

data class SessionState(
    val bookId: String?,
    val error: SessionError?,
    val sleepMode: SleepTimerMode,
    val sleepEndsAtWallMillis: Long?,
    val hasChapters: Boolean = false,
    val undoTo: Spot? = null,
)

/**
 * Turns player state into reading / not reading, sending only changes. "Reading" includes BUFFERING with
 * playWhenReady: the user is still listening while a sentence renders, and a process killed then did die
 * mid-read (P1-07).
 */
class ReadingReporter(private val events: SessionEvents) {
    private var reported: Pair<String, Int>? = null

    fun update(bookId: String?, player: Player, page: Int?) {
        val state = player.playbackState
        val reading = player.playWhenReady && (state == Player.STATE_READY || state == Player.STATE_BUFFERING)
        if (!reading || bookId == null || page == null) {
            notReading()
            return
        }
        val now = bookId to page
        if (now != reported) {
            reported = now
            events.onReading(bookId, page)
        }
    }

    fun notReading() {
        if (reported == null) return
        reported = null
        events.onNotReading()
    }
}
