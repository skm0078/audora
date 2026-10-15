// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/session/SleepTimer.kt
// Purpose: Sleep-timer rules: pause after N minutes or at the end of the current page.

package io.github.skm0078.audora.playback.session

import io.github.skm0078.audora.playback.contract.SleepTimerMode
import io.github.skm0078.audora.playback.playlist.PlaybackItem

/**
 * Pure state; the session schedules the checks. [clock] is a monotonic millisecond clock that keeps counting
 * in deep sleep (`SystemClock.elapsedRealtime`), so a timer set before the screen turned off stays accurate.
 */
class SleepTimer(private val clock: () -> Long) {
    var mode: SleepTimerMode = SleepTimerMode.OFF
        private set

    /** [clock] time when a [SleepTimerMode.MINUTES] timer is due; null otherwise. */
    var dueAt: Long? = null
        private set

    fun set(mode: SleepTimerMode, minutes: Int) {
        require(mode != SleepTimerMode.MINUTES || minutes > 0) { "A minutes timer needs minutes > 0, was $minutes" }
        this.mode = mode
        dueAt = if (mode == SleepTimerMode.MINUTES) clock() + minutes * MS_PER_MINUTE else null
    }

    fun clear() = set(SleepTimerMode.OFF, 0)

    /** Milliseconds until a minutes timer is due (0 when overdue); null when no minutes timer is set. */
    fun remainingMs(): Long? = dueAt?.let { (it - clock()).coerceAtLeast(0) }

    /** End-of-page mode: pause after [current] when [next] does not continue the same page. */
    fun pauseAfter(current: PlaybackItem?, next: PlaybackItem?): Boolean {
        if (mode != SleepTimerMode.END_OF_PAGE) return false
        val page = (current as? PlaybackItem.Sentence)?.unit?.page
        val nextPage = (next as? PlaybackItem.Sentence)?.unit?.page
        return page != null && nextPage != page
    }

    private companion object {
        const val MS_PER_MINUTE = 60_000L
    }
}
