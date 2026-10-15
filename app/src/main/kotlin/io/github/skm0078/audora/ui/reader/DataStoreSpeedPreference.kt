// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/DataStoreSpeedPreference.kt
// Purpose: Keeps the listener's reading speed in a Preferences DataStore (Build Spec §4 stack).

package io.github.skm0078.audora.ui.reader

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.IOException
import kotlinx.coroutines.flow.first

private val Context.readerPreferences: DataStore<Preferences> by preferencesDataStore(name = "reader")

/** A failed read or write is logged and the speed falls back to the default; it never blocks reading. */
class DataStoreSpeedPreference(context: Context) : SpeedPreference {
    private val store = context.applicationContext.readerPreferences

    override suspend fun load(): Float? = try {
        store.data.first()[SPEED]
    } catch (e: IOException) {
        AudoraLog.w(TAG, "Could not read the saved speed", e)
        null
    }

    override suspend fun save(speed: Float) {
        try {
            store.edit { it[SPEED] = speed }
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Could not save the speed $speed", e)
        }
    }

    private companion object {
        const val TAG = "Audora.ReaderPrefs"
        val SPEED = floatPreferencesKey("speed")
    }
}
