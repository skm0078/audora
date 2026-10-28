// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/LibraryLayout.kt
// Purpose: Grid or list for the library, picked in the sort sheet and kept in its own Preferences DataStore
//          (ADR 2026-10-28-book-menu-sort-sheet).

package io.github.skm0078.audora.ui.library

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** How the library lays out its books. Until the listener picks one, [AUTO]: covers, and a list at large text. */
enum class LibraryLayout {
    AUTO,
    GRID,
    LIST,
    ;

    /** Whether the books show as a list at this text size (item 39: two title lines no longer fit under a cover). */
    fun showsList(fontScale: Float): Boolean = when (this) {
        AUTO -> fontScale >= LARGE_TEXT_SCALE
        GRID -> false
        LIST -> true
    }

    companion object {
        /** Font scale from which [AUTO] shows a list (item 39). */
        const val LARGE_TEXT_SCALE = 1.5f

        /** An unknown name (a later version's, then a downgrade) is [AUTO]. */
        fun named(name: String?): LibraryLayout = entries.firstOrNull { it.name == name } ?: AUTO
    }
}

private val Context.libraryViewStore: DataStore<Preferences> by preferencesDataStore(name = "library_view")

/** A failed read shows [LibraryLayout.AUTO], a failed write keeps the old layout; both are logged, neither blocks. */
class LibraryLayoutPreferences(context: Context) {
    private val store = context.applicationContext.libraryViewStore

    val layout: Flow<LibraryLayout> = store.data
        .catch { e ->
            if (e !is IOException) throw e
            AudoraLog.w(TAG, "Could not read the library layout; showing the automatic one", e)
            emit(emptyPreferences())
        }
        .map { LibraryLayout.named(it[LAYOUT]) }

    suspend fun save(layout: LibraryLayout) {
        try {
            store.edit { it[LAYOUT] = layout.name }
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Could not save the library layout", e)
        }
    }

    private companion object {
        const val TAG = "Audora.LibraryLayout"
        val LAYOUT = stringPreferencesKey("layout")
    }
}

/** The saved layout ([layout] is null for the few milliseconds until it is read) and how to change it. */
class LibraryLayoutState(val layout: LibraryLayout?, val onLayout: (LibraryLayout) -> Unit)

@Composable
fun rememberLibraryLayout(): LibraryLayoutState {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { LibraryLayoutPreferences(context) }
    val scope = rememberCoroutineScope()
    val layout by preferences.layout.collectAsStateWithLifecycle(initialValue = null)
    return LibraryLayoutState(layout) { picked -> scope.launch { preferences.save(picked) } }
}
