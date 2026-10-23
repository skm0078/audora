// File: app/src/main/kotlin/io/github/skm0078/audora/privacy/PrivacyPreferences.kt
// Purpose: Keeps the app lock and "hide app content" switches (ADR-061) in their own Preferences DataStore.

package io.github.skm0078.audora.privacy

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.privacyStore: DataStore<Preferences> by preferencesDataStore(name = "privacy")

/** Both off unless the person turns them on. */
data class PrivacySettings(val appLock: Boolean = false, val hideContent: Boolean = false)

/**
 * An unreadable file reads as "lock on" (logged): it must not open a library that was locked. Someone who never
 * turned the lock on then unlocks once with the phone's own screen lock (or not at all on a phone without one) and
 * can switch it off again. A failed write keeps the old value, also logged.
 */
class PrivacyPreferences(context: Context) {
    private val store = context.applicationContext.privacyStore

    val settings: Flow<PrivacySettings> = store.data
        .map { prefs ->
            PrivacySettings(appLock = prefs[APP_LOCK] ?: false, hideContent = prefs[HIDE_CONTENT] ?: false)
        }
        .catch { e ->
            if (e !is IOException) throw e
            AudoraLog.w(TAG, "Could not read the privacy settings; keeping the app locked", e)
            emit(PrivacySettings(appLock = true))
        }

    /** False when it could not be saved (logged), so the switch can show what is really in force. */
    suspend fun saveAppLock(on: Boolean): Boolean = save { it[APP_LOCK] = on }

    suspend fun saveHideContent(on: Boolean): Boolean = save { it[HIDE_CONTENT] = on }

    private suspend fun save(change: (MutablePreferences) -> Unit): Boolean = try {
        store.edit { change(it) }
        true
    } catch (e: IOException) {
        AudoraLog.w(TAG, "Could not save the privacy settings", e)
        false
    }

    private companion object {
        const val TAG = "Audora.Privacy"
        val APP_LOCK = booleanPreferencesKey("app_lock")
        val HIDE_CONTENT = booleanPreferencesKey("hide_content")
    }
}
