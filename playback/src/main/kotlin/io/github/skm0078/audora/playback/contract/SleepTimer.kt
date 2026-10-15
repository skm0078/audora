// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/contract/SleepTimer.kt
// Purpose: What the reader can ask of the sleep timer, and what the service reports back.

package io.github.skm0078.audora.playback.contract

/** A sleep timer the listener picks (Build Spec §2). End of chapter needs headings or the PDF outline. */
sealed interface SleepTimerSetting {
    data object Off : SleepTimerSetting

    /** Wall-clock minutes, counted while paused too; reading stops at the end of the sentence then playing. */
    data class AfterMinutes(val minutes: Int) : SleepTimerSetting {
        init {
            require(minutes > 0) { "minutes must be > 0, was $minutes" }
        }
    }

    /** Stops after the last sentence of the page being read, whichever page that is when it ends. */
    data object EndOfPage : SleepTimerSetting
}

/** The timer as the service runs it. Deadlines are `SystemClock.elapsedRealtime()` values, same on both sides. */
sealed interface SleepTimerState {
    data object Off : SleepTimerState

    data class Counting(val minutes: Int, val deadlineElapsedMs: Long) : SleepTimerState

    data object EndOfPage : SleepTimerState

    /** Time is up; reading stops when the current sentence ends. */
    data object FinishingSentence : SleepTimerState
}
