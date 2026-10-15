// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/sleep/SleepTimer.kt
// Purpose: Sleep-timer rules (minutes, end of page), free of Media3 so they are unit-tested on the JVM.

package io.github.skm0078.audora.playback.sleep

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.playback.contract.SleepTimerSetting
import io.github.skm0078.audora.playback.contract.SleepTimerState

/** What the timer needs from the player; [PlayerSleepTimer] implements it over Media3. */
interface SleepTimerHost {
    fun nowElapsedMs(): Long

    /** The listener wants audio (playing or buffering); a paused player is not. */
    fun isPlaying(): Boolean

    /** True when the current sentence is the last of its page: the next item is on another page, or none. */
    fun isLastUnitOfPage(): Boolean

    /** While enabled the player pauses at the end of each item, so reading never stops mid-sentence. */
    fun setPauseAtEndOfItems(enabled: Boolean)

    fun schedule(delayMs: Long, action: () -> Unit): Cancellable

    fun publish(state: SleepTimerState)
}

fun interface Cancellable {
    fun cancel()
}

/**
 * Never stops mid-sentence: when time is up while reading, it arms "pause at end of item" and the sentence
 * finishes. Not thread-safe: call it from the player's application thread only.
 */
@Suppress("TooManyFunctions") // One entry point per player event the timer reacts to.
class SleepTimer(private val host: SleepTimerHost) {
    var state: SleepTimerState = SleepTimerState.Off
        private set

    private var pendingDeadline: Cancellable? = null
    private var armed = false

    fun set(setting: SleepTimerSetting) {
        cancelDeadline()
        when (setting) {
            SleepTimerSetting.Off -> stop("turned off")

            is SleepTimerSetting.AfterMinutes -> {
                arm(false)
                val delayMs = setting.minutes * MILLIS_PER_MINUTE
                pendingDeadline = host.schedule(delayMs, ::onDeadline)
                moveTo(SleepTimerState.Counting(setting.minutes, host.nowElapsedMs() + delayMs))
                AudoraLog.i(TAG, "Sleep timer set: ${setting.minutes} min")
            }

            SleepTimerSetting.EndOfPage -> {
                moveTo(SleepTimerState.EndOfPage)
                arm(host.isLastUnitOfPage())
                AudoraLog.i(TAG, "Sleep timer set: end of page")
            }
        }
    }

    /** The current item or the playlist changed (skip, seek, next page appended). */
    fun onItemOrPlaylistChanged() {
        if (state == SleepTimerState.EndOfPage) arm(host.isLastUnitOfPage())
    }

    /** The player paused itself at the end of an item: that was this timer. */
    fun onPausedAtEndOfItem() {
        if (state == SleepTimerState.EndOfPage || state == SleepTimerState.FinishingSentence) {
            stop("stopped reading at the end of a sentence")
        }
    }

    /**
     * Playback (re)started. Handler delays stop while the phone sleeps, so a deadline that passed while paused
     * with the screen off may not have fired yet: check the clock and reschedule from the real remaining time.
     */
    fun onPlaybackResumed() {
        val counting = state as? SleepTimerState.Counting ?: return
        cancelDeadline()
        val remainingMs = counting.deadlineElapsedMs - host.nowElapsedMs()
        if (remainingMs <= 0) {
            stop("time ran out while paused")
        } else {
            pendingDeadline = host.schedule(remainingMs, ::onDeadline)
        }
    }

    /** The listener paused. A running timer keeps counting; one waiting for the sentence end has done its job. */
    fun onPausedByUser() {
        if (state == SleepTimerState.FinishingSentence) stop("listener paused first")
    }

    fun release() {
        cancelDeadline()
        arm(false)
    }

    private fun onDeadline() {
        pendingDeadline = null
        if (host.isPlaying()) {
            moveTo(SleepTimerState.FinishingSentence)
            arm(true)
            AudoraLog.i(TAG, "Sleep timer: time is up, finishing the sentence")
        } else {
            stop("time is up while paused")
        }
    }

    private fun stop(reason: String) {
        cancelDeadline()
        arm(false)
        if (state != SleepTimerState.Off) AudoraLog.i(TAG, "Sleep timer ended: $reason")
        moveTo(SleepTimerState.Off)
    }

    private fun arm(enabled: Boolean) {
        if (armed == enabled) return
        armed = enabled
        host.setPauseAtEndOfItems(enabled)
    }

    private fun cancelDeadline() {
        pendingDeadline?.cancel()
        pendingDeadline = null
    }

    private fun moveTo(next: SleepTimerState) {
        if (next == state) return
        state = next
        host.publish(next)
    }

    private companion object {
        const val TAG = "Audora.SleepTimer"
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
