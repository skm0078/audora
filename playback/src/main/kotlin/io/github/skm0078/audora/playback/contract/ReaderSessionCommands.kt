// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/contract/ReaderSessionCommands.kt
// Purpose: The one custom session command the reader sends (sleep timer) and the session extras it reads back.

package io.github.skm0078.audora.playback.contract

import android.os.Bundle
import androidx.media3.session.SessionCommand

/**
 * Everything else the reader needs is a standard `Player` call (play, pause, skip, speed). The sleep timer is
 * a command because it must run in the service: the screen is off, the activity may be gone, and "end of page"
 * has to stop exactly at a sentence boundary, which only the service's player can do.
 */
object ReaderSessionCommands {
    const val ACTION_SET_SLEEP_TIMER = "io.github.skm0078.audora.SET_SLEEP_TIMER"

    val SET_SLEEP_TIMER = SessionCommand(ACTION_SET_SLEEP_TIMER, Bundle.EMPTY)

    private const val KEY_SETTING = "io.github.skm0078.audora.sleep.SETTING"
    private const val KEY_STATE = "io.github.skm0078.audora.sleep.STATE"
    private const val KEY_MINUTES = "io.github.skm0078.audora.sleep.MINUTES"
    private const val KEY_DEADLINE = "io.github.skm0078.audora.sleep.DEADLINE"

    private const val OFF = "off"
    private const val MINUTES = "minutes"
    private const val END_OF_PAGE = "end_of_page"
    private const val FINISHING = "finishing"

    fun sleepTimerArgs(setting: SleepTimerSetting): Bundle = Bundle().apply {
        when (setting) {
            SleepTimerSetting.Off -> putString(KEY_SETTING, OFF)

            is SleepTimerSetting.AfterMinutes -> {
                putString(KEY_SETTING, MINUTES)
                putInt(KEY_MINUTES, setting.minutes)
            }

            SleepTimerSetting.EndOfPage -> putString(KEY_SETTING, END_OF_PAGE)
        }
    }

    /** Null for a malformed request, which the service answers with RESULT_ERROR_BAD_VALUE. */
    fun sleepTimerSettingFrom(args: Bundle): SleepTimerSetting? = when (args.getString(KEY_SETTING)) {
        OFF -> SleepTimerSetting.Off
        MINUTES -> args.getInt(KEY_MINUTES).takeIf { it > 0 }?.let(SleepTimerSetting::AfterMinutes)
        END_OF_PAGE -> SleepTimerSetting.EndOfPage
        else -> null
    }

    /** Writes the timer into the session extras, leaving any other keys the service keeps there. */
    fun putSleepTimerState(extras: Bundle, state: SleepTimerState) {
        extras.remove(KEY_MINUTES)
        extras.remove(KEY_DEADLINE)
        when (state) {
            SleepTimerState.Off -> extras.putString(KEY_STATE, OFF)

            is SleepTimerState.Counting -> {
                extras.putString(KEY_STATE, MINUTES)
                extras.putInt(KEY_MINUTES, state.minutes)
                extras.putLong(KEY_DEADLINE, state.deadlineElapsedMs)
            }

            SleepTimerState.EndOfPage -> extras.putString(KEY_STATE, END_OF_PAGE)

            SleepTimerState.FinishingSentence -> extras.putString(KEY_STATE, FINISHING)
        }
    }

    /** A service that never ran a timer has no key: that reads as Off. */
    fun sleepTimerStateFrom(extras: Bundle): SleepTimerState = when (extras.getString(KEY_STATE)) {
        MINUTES -> SleepTimerState.Counting(extras.getInt(KEY_MINUTES), extras.getLong(KEY_DEADLINE))
        END_OF_PAGE -> SleepTimerState.EndOfPage
        FINISHING -> SleepTimerState.FinishingSentence
        else -> SleepTimerState.Off
    }
}
