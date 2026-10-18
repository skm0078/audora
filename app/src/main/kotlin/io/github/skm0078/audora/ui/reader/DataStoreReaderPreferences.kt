// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/DataStoreReaderPreferences.kt
// Purpose: Keeps the listener's reading speed, page/text view and the reader's switches in a Preferences DataStore.

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

    override suspend fun load(): Float? = store.read("speed") { it[SPEED] }

    override suspend fun save(speed: Float) = store.write("speed $speed") { it[SPEED] = speed }

    override suspend fun loadView(): ReaderView? = store.read("view") { prefs ->
        prefs[VIEW]?.let { name -> ReaderView.entries.firstOrNull { it.name == name } }
    }

    override suspend fun saveView(view: ReaderView) = store.write("view $view") { it[VIEW] = view.name }

    override fun keepScreenOn(): Flow<Boolean> = store.setting("keep screen on", false) { it[KEEP_SCREEN_ON] }

    override suspend fun saveKeepScreenOn(on: Boolean) = store.write("keep screen on $on") { it[KEEP_SCREEN_ON] = on }

    override fun volumeKeysSkip(): Flow<Boolean> = store.setting("volume keys setting", false) { it[VOLUME_KEYS_SKIP] }

    override suspend fun saveVolumeKeysSkip(on: Boolean) =
        store.write("volume keys skip $on") { it[VOLUME_KEYS_SKIP] = on }

    override fun voiceCommands(): Flow<VoiceCommandsMode> =
        store.setting("voice commands setting", VoiceCommandsMode.OFF) { prefs ->
            VoiceCommandsMode.entries.firstOrNull { it.name == prefs[VOICE_COMMANDS] }
        }

    override suspend fun saveVoiceCommands(mode: VoiceCommandsMode) =
        store.write("voice commands $mode") { it[VOICE_COMMANDS] = mode.name }

    private companion object {
        val SPEED = floatPreferencesKey("speed")
        val VIEW = stringPreferencesKey("view")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keepScreenOn")
        val VOLUME_KEYS_SKIP = booleanPreferencesKey("volumeKeysSkip")
        val VOICE_COMMANDS = stringPreferencesKey("voiceCommandsMode")
    }
}

private const val TAG = "Audora.ReaderPrefs"

/** A setting that is [default] until saved; an unreadable file reads as [default] (logged). */
private fun <T> DataStore<Preferences>.setting(what: String, default: T, get: (Preferences) -> T?): Flow<T> = data
    .map { get(it) ?: default }
    .catch { e ->
        if (e !is IOException) throw e
        AudoraLog.w(TAG, "Could not read the saved $what", e)
        emit(default)
    }
    .distinctUntilChanged()

private suspend fun <T> DataStore<Preferences>.read(what: String, get: (Preferences) -> T?): T? = try {
    get(data.first())
} catch (e: IOException) {
    AudoraLog.w(TAG, "Could not read the saved $what", e)
    null
}

private suspend fun DataStore<Preferences>.write(what: String, set: (MutablePreferences) -> Unit) {
    try {
        edit { set(it) }
    } catch (e: IOException) {
        AudoraLog.w(TAG, "Could not save the $what", e)
    }
}
