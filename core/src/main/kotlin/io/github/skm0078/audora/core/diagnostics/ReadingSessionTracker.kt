// File: core/src/main/kotlin/io/github/skm0078/audora/core/diagnostics/ReadingSessionTracker.kt
// Purpose: Unfinished-session detection: marks that audio is playing and spots a process that died mid-read.

package io.github.skm0078.audora.core.diagnostics

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome

/**
 * The playback service (P1-04) calls [onPlaybackActive] when audio starts or moves to another page, and
 * [onPlaybackInactive] on pause, stop, end of book and sleep timer. A marker still on disk when a new
 * process starts therefore means the old process died while reading: that is the [unfinishedSession].
 *
 * A paused session is deliberately not "reading": Android may reclaim a paused app at any time, and that
 * is not a stop the user needs to be told about.
 *
 * Thread-safe. Every method may do blocking file I/O, so call from a background thread.
 */
class ReadingSessionTracker(private val store: DiagnosticsStateStore, private val clock: () -> Long) {
    private val lock = Any()
    private var loaded = false

    /** The marker left by a dead process; null once this process starts its own session or it is resolved. */
    private var orphan: ReadingSession? = null

    /** This process's own session, while audio plays. */
    private var current: ReadingSession? = null

    /** A page change within the same book keeps the session's start time. */
    fun onPlaybackActive(bookId: String, page: Int): Outcome<Unit, StateWriteError> = synchronized(lock) {
        ensureLoaded()
        val startedAt = current?.takeIf { it.bookId == bookId }?.startedAtMillis ?: clock()
        val session = ReadingSession(bookId, page, startedAt)
        orphan = null
        current = session
        store.writeSession(session)
    }

    fun onPlaybackInactive(): Outcome<Unit, StateWriteError> = synchronized(lock) {
        ensureLoaded()
        orphan = null
        current = null
        store.writeSession(null)
    }

    /** The session a previous process was reading when it died, until [resolveUnfinished] or new playback. */
    fun unfinishedSession(): ReadingSession? = synchronized(lock) {
        ensureLoaded()
        orphan
    }

    /**
     * The user answered the "Reading stopped unexpectedly" prompt. Clears the marker either way: a resume
     * starts a new session (and a new marker) through [onPlaybackActive].
     */
    fun resolveUnfinished(resumed: Boolean) = synchronized(lock) {
        ensureLoaded()
        val session = orphan ?: return@synchronized
        val answer = if (resumed) "resumed" else "dismissed"
        AudoraLog.i(TAG, "Unfinished session $answer: ${DiagnosticsText.describe(session)}")
        orphan = null
        store.writeSession(null)
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        orphan = store.readSession()
    }

    private companion object {
        const val TAG = "ReadingSession"
    }
}
