// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/controls/ResumeOnReconnect.kt
// Purpose: The listener's "Resume when headphones reconnect" choice (CF-4): Settings writes it, the service reads it.

package io.github.skm0078.audora.playback.controls

import android.content.Context
import androidx.core.content.edit

/**
 * SharedPreferences, not DataStore: the service reads it synchronously inside Android's device callback, and it is one
 * boolean. Off unless the listener turns it on, since reading that starts by itself must be asked for (Build Spec C7).
 */
class ResumeOnReconnect(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    val on: Boolean get() = prefs.getBoolean(KEY_ON, false)

    fun set(on: Boolean) = prefs.edit { putBoolean(KEY_ON, on) }

    private companion object {
        const val FILE = "player_controls"
        const val KEY_ON = "resume_on_reconnect"
    }
}
