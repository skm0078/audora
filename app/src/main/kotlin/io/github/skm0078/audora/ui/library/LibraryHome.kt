// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/LibraryHome.kt
// Purpose: What makes the library the listener's own: the "Make it yours" choices kept in their own Preferences
//          DataStore, the Continue card's glances and the line of the day (ADR 2026-10-28-make-it-yours).

package io.github.skm0078.audora.ui.library

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
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
import io.github.skm0078.audora.core.home.BookGlance
import io.github.skm0078.audora.core.home.HomeLayout
import io.github.skm0078.audora.core.home.LineOfTheDay
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.notes.Highlight
import io.github.skm0078.audora.core.notes.HighlightStore
import io.github.skm0078.audora.core.resume.ResumeAnchor
import io.github.skm0078.audora.di.HomeEntryPoint
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One of the listener's highlights for today, with the book it is from (personal touch 15). */
@Immutable
data class DayLine(val quote: String, val book: Book, val page: Int, val anchor: ResumeAnchor)

/**
 * The library's personal parts. [layout] is null for the few milliseconds until it is read, so the home never jumps
 * from the default sections to the listener's. [glances] by book; [line] null without a highlight.
 */
@Immutable
class LibraryHome(
    val layout: HomeLayout?,
    val onLayout: (HomeLayout) -> Unit = {},
    val glances: Map<BookId, BookGlance> = emptyMap(),
    val line: DayLine? = null,
    val onOpenLine: (ResumeAnchor) -> Unit = {},
) {
    companion object {
        /** The default home, for previews and screens without the listener's choices. */
        val Default = LibraryHome(HomeLayout())
    }
}

private val Context.libraryHomeStore: DataStore<Preferences> by preferencesDataStore(name = "library_home")

/** A failed read shows the default home, a failed write keeps the old one; both are logged, neither blocks. */
class HomePreferences(context: Context) {
    private val store = context.applicationContext.libraryHomeStore

    val layout: Flow<HomeLayout> = store.data
        .catch { e ->
            if (e !is IOException) throw e
            AudoraLog.w(TAG, "Could not read the home choices; showing the default home", e)
            emit(emptyPreferences())
        }
        .map { HomeLayout.decode(it[LAYOUT]) }

    suspend fun save(layout: HomeLayout) {
        try {
            store.edit { it[LAYOUT] = layout.encode() }
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Could not save the home choices", e)
        }
    }

    private companion object {
        const val TAG = "Audora.LibraryHome"
        val LAYOUT = stringPreferencesKey("home")
    }
}

/**
 * The listener's home for [books]: their saved choices, the glances (loaded off the main thread; books no longer in
 * the library are forgotten) and today's line from [highlights], which [onOpenLine] opens at its sentence.
 */
@Composable
fun rememberLibraryHome(
    highlights: HighlightStore,
    books: List<Book>?,
    onOpenLine: (ResumeAnchor) -> Unit,
): LibraryHome {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { HomePreferences(context) }
    val glances = remember(context) { HomeEntryPoint.of(context).bookGlances() }
    val scope = rememberCoroutineScope()
    val layout by preferences.layout.collectAsStateWithLifecycle(initialValue = null)
    val shown by glances.glances.collectAsStateWithLifecycle()
    val all by remember(highlights) { highlights.observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList())
    // Pruning also reads the file the first time, which fills [shown] in a process the service has not started.
    LaunchedEffect(books) {
        val kept = books?.mapTo(HashSet()) { it.id } ?: return@LaunchedEffect
        withContext(Dispatchers.IO) { glances.prune(kept) }
    }
    val line = remember(all, books) { dayLine(all, books.orEmpty(), LocalDate.now().toEpochDay()) }
    return LibraryHome(layout, { picked -> scope.launch { preferences.save(picked) } }, shown, line, onOpenLine)
}

/** Only a highlight whose book is still in the library: the card names it and opens it. */
internal fun dayLine(highlights: List<Highlight>, books: List<Book>, epochDay: Long): DayLine? {
    val byId = books.associateBy { it.id }
    val picked = LineOfTheDay.pick(highlights.filter { it.bookId in byId }, epochDay) ?: return null
    val book = byId.getValue(picked.bookId)
    return DayLine(LineOfTheDay.clip(picked.quote), book, picked.anchor.page + 1, picked.anchor)
}
