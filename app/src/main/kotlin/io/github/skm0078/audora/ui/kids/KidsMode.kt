// File: app/src/main/kotlin/io/github/skm0078/audora/ui/kids/KidsMode.kt
// Purpose: Kids mode (plan item 71): a simpler Audora for a child — the library and a big-button player only,
//          settings locked behind a long press, a slower voice — saved across launches.

package io.github.skm0078.audora.ui.kids

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.ui.AppDestination
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * @property on kids mode is on.
 * @property speedBefore the listener's own speed when kids mode was turned on, given back when it is turned off;
 *   null when there was none saved.
 */
data class KidsMode(val on: Boolean = false, val speedBefore: Float? = null)

/** Whether the screens below draw their kids-mode form; provided once at the app's root. */
val LocalKidsMode = compositionLocalOf { false }

/** The voice speed kids mode reads at, at most: a little slower than normal, for young listeners. */
const val KIDS_SPEED = 0.85f

/** The speed to read at in kids mode: the listener's own if already slower, else [KIDS_SPEED]. */
fun kidsSpeed(current: Float?): Float = minOf(current ?: KIDS_SPEED, KIDS_SPEED)

/**
 * Where kids mode lets [next] go: books open in the big-button player (the page view, Notes, Settings and their
 * sub-pages are for grown-ups), so everything else lands on the library.
 */
fun kidsDestination(next: AppDestination): AppDestination = when (next) {
    AppDestination.Library, is AppDestination.Player -> next

    is AppDestination.Reader -> AppDestination.Player(next.id, next.from)

    AppDestination.Notes, AppDestination.Settings, is AppDestination.Voices, AppDestination.Licences,
    AppDestination.Storage, AppDestination.Jobs,
    is AppDestination.BatterySetup,
    -> AppDestination.Library
}

private val Context.kidsModeStore: DataStore<Preferences> by preferencesDataStore(name = "kids_mode")

/** Its own small file. A failed read is logged and means "off", so a fault never locks a grown-up out. */
class KidsModePreferences(context: Context) {
    private val store = context.applicationContext.kidsModeStore

    val mode: Flow<KidsMode> = store.data
        .catch { e ->
            if (e !is IOException) throw e
            AudoraLog.w(TAG, "Could not read kids mode; treating it as off", e)
            emit(emptyPreferences())
        }
        .map { prefs -> KidsMode(on = prefs[ON] ?: false, speedBefore = prefs[SPEED_BEFORE]) }

    /** Turns kids mode on, remembering [speedBefore] to give back later. False when it could not be saved. */
    suspend fun turnOn(speedBefore: Float?): Boolean = write {
        it[ON] = true
        if (speedBefore != null) it[SPEED_BEFORE] = speedBefore else it.remove(SPEED_BEFORE)
    }

    /** Turns kids mode off. False when it could not be saved. */
    suspend fun turnOff(): Boolean = write {
        it[ON] = false
        it.remove(SPEED_BEFORE)
    }

    private suspend fun write(change: (MutablePreferences) -> Unit): Boolean = try {
        store.edit(change)
        true
    } catch (e: IOException) {
        AudoraLog.w(TAG, "Could not save kids mode", e)
        false
    }

    private companion object {
        const val TAG = "Audora.KidsMode"
        val ON = booleanPreferencesKey("on")
        val SPEED_BEFORE = floatPreferencesKey("speed_before")
    }
}
