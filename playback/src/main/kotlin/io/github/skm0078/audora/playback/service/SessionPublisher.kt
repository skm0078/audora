// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/service/SessionPublisher.kt
// Purpose: Publishes the book session's state as session extras and forwards reading/not-reading to diagnostics.

package io.github.skm0078.audora.playback.service

import android.os.Bundle
import androidx.media3.session.MediaSession
import io.github.skm0078.audora.core.diagnostics.ReadingSessionTracker
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.contract.PlaybackContract
import io.github.skm0078.audora.playback.contract.SleepTimerMode
import io.github.skm0078.audora.playback.session.SessionEvents
import io.github.skm0078.audora.playback.session.SessionState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SessionPublisher(private val session: MediaSession, private val diagnostics: DiagnosticsBridge) :
    SessionEvents {
    override fun onReading(bookId: String, page: Int) = diagnostics.reading(bookId, page)

    override fun onNotReading() = diagnostics.notReading()

    override fun onStateChanged(state: SessionState) {
        session.setSessionExtras(
            Bundle().apply {
                state.bookId?.let { putString(PlaybackContract.SESSION_BOOK_ID, it) }
                if (state.sleepMode !=
                    SleepTimerMode.OFF
                ) {
                    putString(PlaybackContract.SESSION_SLEEP_MODE, state.sleepMode.name)
                }
                state.sleepEndsAtWallMillis?.let { putLong(PlaybackContract.SESSION_SLEEP_ENDS_AT, it) }
                if (state.hasChapters) putBoolean(PlaybackContract.SESSION_HAS_CHAPTERS, true)
                state.error?.let {
                    putString(PlaybackContract.SESSION_ERROR, it.code.name)
                    putString(PlaybackContract.SESSION_ERROR_MESSAGE, it.message)
                }
            },
        )
    }
}

/**
 * P1-07's unfinished-session marker: "reading" while audio plays, "not reading" on pause, stop, end, error and
 * sleep timer. The tracker does blocking file I/O (atomic write + fsync), so calls run in order on one
 * background thread, never on the main thread.
 */
class DiagnosticsBridge(
    private val tracker: ReadingSessionTracker,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
) {
    fun reading(bookId: String, page: Int) {
        scope.launch(io) { report(tracker.onPlaybackActive(bookId, page), "active page=$page") }
    }

    fun notReading() {
        scope.launch(io) { report(tracker.onPlaybackInactive(), "inactive") }
    }

    private fun report(result: Outcome<Unit, *>, what: String) {
        if (result is Outcome.Failure) AudoraLog.w(TAG, "Session marker not written ($what): ${result.error}")
    }

    private companion object {
        const val TAG = "Playback.Diagnostics"
    }
}
