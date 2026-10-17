// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/DataStoreReaderPreferences.kt
// Purpose: Keeps the listener's reading speed, page/text view and keep-screen-on in a Preferences DataStore.

package io.github.skm0078.audora.ui.reader

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.readerPreferences: DataStore<Preferences> by preferencesDataStore(name = "reader")

/**
 * One class for every reader setting because a DataStore file may have only one instance per process. A failed read
 * or write is logged and the setting falls back to its default; it never blocks reading.
 */
class DataStoreReaderPreferences(context: Context) :
    SpeedPreference,
    ViewPreference,
    ScreenPreference {
    private val store = context.applicationContext.readerPreferences

    override suspend fun load(): Float? = read("speed") { it[SPEED] }

    override suspend fun save(speed: Float) = write("speed $speed") { it[SPEED] = speed }

    // An unknown name (a view a later version added, then a downgrade) falls back to the default view.
    override suspend fun loadView(): ReaderView? =
        read("view") { prefs -> prefs[VIEW]?.let { name -> ReaderView.entries.firstOrNull { it.name == name } } }

    override suspend fun saveView(view: ReaderView) = write("view $view") { it[VIEW] = view.name }

    override fun keepScreenOn(): Flow<Boolean> = store.data
        .map { it[KEEP_SCREEN_ON] ?: false }
        .catch { e ->
            if (e !is IOException) throw e
            AudoraLog.w(TAG, "Could not read the saved keep screen on", e)
            emit(false)
        }
        .distinctUntilChanged()

    override suspend fun saveKeepScreenOn(on: Boolean) = write("keep screen on $on") { it[KEEP_SCREEN_ON] = on }

    private suspend fun <T> read(what: String, get: (Preferences) -> T?): T? = try {
        get(store.data.first())
    } catch (e: IOException) {
        AudoraLog.w(TAG, "Could not read the saved $what", e)
        null
    }

    private suspend fun write(what: String, set: (MutablePreferences) -> Unit) {
        try {
            store.edit { set(it) }
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Could not save the $what", e)
        }
    }

    private companion object {
        const val TAG = "Audora.ReaderPrefs"
        val SPEED = floatPreferencesKey("speed")
        val VIEW = stringPreferencesKey("view")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keepScreenOn")
    }
}
