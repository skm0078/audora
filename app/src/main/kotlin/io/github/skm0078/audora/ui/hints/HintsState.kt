// File: app/src/main/kotlin/io/github/skm0078/audora/ui/hints/HintsState.kt
// Purpose: The first-use hints' saved state (which were seen, whether they are muted, how often the library opened)
//          and this app start's one hint (ADR 2026-10-30-first-use-hints).

package io.github.skm0078.audora.ui.hints

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import io.github.skm0078.audora.core.hints.FirstUseHint
import io.github.skm0078.audora.core.hints.HintHistory
import io.github.skm0078.audora.core.hints.pickHint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One for the whole app, so "one hint an app start" holds across the reader and the library; [file] is where it is
 * saved (a test gives its own). Muted until the saved flags are read (off the main thread), so a hint already seen
 * never flashes.
 */
@Stable
class HintsState internal constructor(context: Context, file: String = FILE) {
    private val prefs = context.getSharedPreferences(file, Context.MODE_PRIVATE)
    private val loading = Mutex()
    private var loaded = false

    var history by mutableStateOf(HintHistory(muted = true))
        private set

    /** How many app starts opened the library, counted once per start; read with the rest. */
    var libraryVisits by mutableStateOf(0)
        private set
    private var visitCounted = false

    /** The muting switch's value; false while not read yet. */
    var muted by mutableStateOf(false)
        private set

    /** Reads the saved flags once; the reader, the library and Settings may all ask at the same moment. */
    suspend fun load() = loading.withLock {
        if (loaded) return@withLock
        val saved = withContext(Dispatchers.IO) {
            Saved(
                seen = prefs.getStringSet(KEY_SEEN, emptySet()).orEmpty().mapNotNull(FirstUseHint::fromKey).toSet(),
                muted = prefs.getBoolean(KEY_MUTED, false),
                visits = prefs.getInt(KEY_LIBRARY_VISITS, 0),
            )
        }
        loaded = true
        muted = saved.muted
        libraryVisits = saved.visits
        history = history.copy(seen = history.seen + saved.seen, muted = saved.muted)
    }

    /** The hint to show among [candidates], if any (see [pickHint]). */
    fun pick(candidates: List<FirstUseHint>): FirstUseHint? = pickHint(candidates, history)

    /** [hint] is on screen: it uses this app start's turn and stays until answered or no longer apt. */
    fun onShown(hint: FirstUseHint) {
        if (history.showing != hint) history = history.copy(shownThisStart = true, showing = hint)
    }

    /**
     * Got it, or the hint's own button: never again. Saved with apply(), which writes in the background, so the save
     * cannot be lost with the card's scope as the card leaves the screen.
     */
    fun answer(hint: FirstUseHint) {
        val seen = history.seen + hint
        history = history.copy(seen = seen, showing = null)
        prefs.edit { putStringSet(KEY_SEEN, seen.mapTo(mutableSetOf()) { it.key }) }
    }

    fun setMuted(value: Boolean) {
        muted = value
        history = history.copy(muted = value)
        prefs.edit { putBoolean(KEY_MUTED, value) }
    }

    /** The library showed: counted once an app start, after the saved count is read. */
    suspend fun countLibraryVisit() {
        load()
        if (visitCounted) return
        visitCounted = true
        libraryVisits += 1
        prefs.edit { putInt(KEY_LIBRARY_VISITS, libraryVisits) }
    }

    private class Saved(val seen: Set<FirstUseHint>, val muted: Boolean, val visits: Int)

    internal companion object {
        const val FILE = "first_use_hints"
        const val KEY_SEEN = "seen"
        const val KEY_MUTED = "muted"
        const val KEY_LIBRARY_VISITS = "library_visits"

        @Volatile
        private var instance: HintsState? = null

        fun of(context: Context): HintsState = instance ?: synchronized(this) {
            instance ?: HintsState(context.applicationContext).also { instance = it }
        }
    }
}

/** The hints in use: the app's one, unless a test provides its own. */
val LocalHints = staticCompositionLocalOf<HintsState?> { null }

/** The app's hints, read once. */
@Composable
fun rememberHints(): HintsState {
    val context = LocalContext.current
    val provided = LocalHints.current
    val hints = remember(provided) { provided ?: HintsState.of(context) }
    LaunchedEffect(hints) { hints.load() }
    return hints
}
