// File: app/src/main/kotlin/io/github/skm0078/audora/ui/stats/ListeningGoal.kt
// Purpose: The listener's weekly listening goal for the stats screen's ring and the home's "This week", kept on the
//          phone.

package io.github.skm0078.audora.ui.stats

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Hours a week; 0 is no goal. One number: SharedPreferences is enough. Read and written on a background thread. Not in
 * the backup, which carries the listening itself: a new phone starts at the default goal.
 */
internal class ListeningGoal(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var weeklyHours: Int
        get() = prefs.getInt(KEY_WEEKLY_HOURS, DEFAULT_HOURS).coerceIn(0, OPTIONS.last())
        set(value) = prefs.edit { putInt(KEY_WEEKLY_HOURS, value.coerceIn(0, OPTIONS.last())) }

    /** [weeklyHours] now and again on each change, so the home's ring follows a goal set on the stats screen. */
    val changes: Flow<Int> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_WEEKLY_HOURS) trySend(weeklyHours)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        send(weeklyHours)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    companion object {
        /** What the goal dialog offers, in hours a week: about 10 minutes a day up to 2 hours a day. 0 is no goal. */
        val OPTIONS = listOf(0, 1, 2, 3, 5, 8, 10, 14)

        /** A gentle start: about 40 minutes a day, a podcast-sized habit. */
        const val DEFAULT_HOURS = 5
        private const val FILE = "listening_goal"
        private const val KEY_WEEKLY_HOURS = "weekly_hours"
    }
}
