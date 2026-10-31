// File: app/src/main/kotlin/io/github/skm0078/audora/ui/share/CardSpanPreferences.kt
// Purpose: Keeps the card screen's last "Card shows" pick (selection or whole page) in its own Preferences DataStore
//          (ADR 2026-10-30-whole-page-cards).

package io.github.skm0078.audora.ui.share

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.shareCardStore: DataStore<Preferences> by preferencesDataStore(name = "share_card")

/** A failed read shows the selection (the default), a failed write keeps the old pick; both are logged. */
class CardSpanPreferences(context: Context) {
    private val store = context.applicationContext.shareCardStore

    val wholePage: Flow<Boolean> = store.data
        .catch { e ->
            if (e !is IOException) throw e
            AudoraLog.w(TAG, "Could not read the card's last pick; showing the selection", e)
            emit(emptyPreferences())
        }
        .map { it[WHOLE_PAGE] ?: false }

    suspend fun save(wholePage: Boolean) {
        try {
            store.edit { it[WHOLE_PAGE] = wholePage }
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Could not save the card's pick", e)
        }
    }

    private companion object {
        const val TAG = "ShareCard"
        val WHOLE_PAGE = booleanPreferencesKey("whole_page")
    }
}
