// File: app/src/main/kotlin/io/github/skm0078/audora/ui/theme/AppearancePreferences.kt
// Purpose: Keeps the listener's Appearance in its own Preferences DataStore and publishes it to the theme.

package io.github.skm0078.audora.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.appearanceStore: DataStore<Preferences> by preferencesDataStore(name = "appearance")

/** A failed read shows the default look and a failed write keeps the old one; both are logged, neither blocks. */
class AppearancePreferences(context: Context) {
    private val store = context.applicationContext.appearanceStore

    val appearance: Flow<Appearance> = store.data
        .catch { e ->
            if (e !is IOException) throw e
            AudoraLog.w(TAG, "Could not read the appearance settings; showing the default look", e)
            emit(emptyPreferences())
        }
        .map(::appearanceOf)

    suspend fun save(appearance: Appearance) {
        try {
            store.edit {
                it[WALLPAPER] = appearance.wallpaperColors
                it[DARK_MODE] = appearance.darkMode.name
                it[TRUE_BLACK] = appearance.trueBlack
            }
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Could not save the appearance settings", e)
        }
    }

    private companion object {
        const val TAG = "Audora.Appearance"
        val WALLPAPER = booleanPreferencesKey("wallpaper_colors")
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val TRUE_BLACK = booleanPreferencesKey("true_black")

        // An unknown mode name (a later version's, then a downgrade) falls back to following the system.
        fun appearanceOf(prefs: Preferences): Appearance {
            val defaults = Appearance()
            return Appearance(
                wallpaperColors = prefs[WALLPAPER] ?: defaults.wallpaperColors,
                darkMode = prefs[DARK_MODE]?.let { name -> DarkMode.entries.firstOrNull { it.name == name } }
                    ?: defaults.darkMode,
                trueBlack = prefs[TRUE_BLACK] ?: defaults.trueBlack,
            )
        }
    }
}
