// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/outputs/MadeSleepTimer.kt
// Purpose: The saved-audio player's sleep timer (ADR 2026-10-28-made-tab): pause after N minutes or at the end of
//          the current chapter or the Nth from here, kept in the service so it works with the screen off.

package io.github.skm0078.audora.playback.outputs

import android.os.Bundle
import androidx.media3.session.SessionCommand

/**
 * Pure state; the service schedules the minutes and tells it when a chapter (one file of the set) ends by itself.
 * [clock] is a monotonic millisecond clock that keeps counting in deep sleep (`SystemClock.elapsedRealtime`).
 */
class MadeSleepTimer(private val clock: () -> Long) {
    /** [clock] time a minutes timer is due; null when none is set. */
    var dueAt: Long? = null
        private set

    /** Chapter ends still to come before the pause, this one included; 0 when no chapter timer is set. */
    var chaptersLeft: Int = 0
        private set

    val isSet: Boolean get() = dueAt != null || chaptersLeft > 0

    /** True while the playing chapter is the last one to play: the player pauses at its end. */
    val pausesAtThisChaptersEnd: Boolean get() = chaptersLeft == 1

    fun minutes(minutes: Int) {
        require(minutes > 0) { "minutes must be > 0, was $minutes" }
        dueAt = clock() + minutes * MS_PER_MINUTE
        chaptersLeft = 0
    }

    /** [count] 1 pauses at the end of the playing chapter, 2 at the end of the next, and so on. */
    fun chapters(count: Int) {
        require(count > 0) { "count must be > 0, was $count" }
        dueAt = null
        chaptersLeft = count
    }

    fun clear() {
        dueAt = null
        chaptersLeft = 0
    }

    /** Milliseconds until a minutes timer is due (0 when overdue); null when none is set. */
    fun remainingMs(): Long? = dueAt?.let { (it - clock()).coerceAtLeast(0) }

    /** A chapter played to its end and the next began: one fewer to go. The last one pauses instead of moving on. */
    fun chapterEnded() {
        if (chaptersLeft > 1) chaptersLeft--
    }

    private companion object {
        const val MS_PER_MINUTE = 60_000L
    }
}

/** How the app's player screen sets the timer and reads it back; apart from the timer, so its tests need no Android. */
object MadeSleepContract {
    const val ACTION_SLEEP = "io.github.skm0078.audora.made.SLEEP"
    val SLEEP = SessionCommand(ACTION_SLEEP, Bundle.EMPTY)

    /** Int argument of [SLEEP]: minutes until the pause. */
    const val ARG_MINUTES = "minutes"

    /** Int argument of [SLEEP]: chapter ends until the pause. Neither argument above 0 turns the timer off. */
    const val ARG_CHAPTERS = "chapters"

    /** Long session extra: wall-clock time (`System.currentTimeMillis()`) a minutes timer pauses at. */
    const val EXTRA_ENDS_AT = "sleep_ends_at"

    /** Int session extra: chapter ends left before the pause. */
    const val EXTRA_CHAPTERS = "sleep_chapters"

    /** [timer] as the session publishes it, [nowWallMillis] being `System.currentTimeMillis()`. */
    fun extras(timer: MadeSleepTimer, nowWallMillis: Long): Bundle = Bundle().apply {
        timer.remainingMs()?.let { putLong(EXTRA_ENDS_AT, nowWallMillis + it) }
        if (timer.chaptersLeft > 0) putInt(EXTRA_CHAPTERS, timer.chaptersLeft)
    }

    fun endsAtIn(extras: Bundle): Long? = extras.getLong(EXTRA_ENDS_AT, 0).takeIf { it > 0 }

    fun chaptersIn(extras: Bundle): Int = extras.getInt(EXTRA_CHAPTERS, 0)
}
