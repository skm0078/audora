// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/outputs/EarlierAudiobooks.kt
// Purpose: Audiobooks saved before the library listed what it made (ADR-068) are found once, from the export records.

package io.github.skm0078.audora.playback.outputs

import android.content.Context
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.BookOutputStore
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.playback.export.ExportRecords

/**
 * Each book's export record already lists its finished chapters and where they are. Only complete audiobooks are
 * listed (a stopped one is the export's to finish); a file deleted since shows as gone in the list, never playable.
 * Runs once per install: a flag in the app's preferences, set after the pass.
 */
class EarlierAudiobooks(context: Context, private val store: BookOutputStore) {
    private val records = ExportRecords.of(context)
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Blocking file reads; call on an IO thread. */
    suspend fun findOnce(books: List<BookId>) {
        if (prefs.getBoolean(KEY_DONE, false)) return
        var found = 0
        books.forEach { id ->
            val record = records.load(id.value)?.takeIf { it.complete && it.chapters.isNotEmpty() } ?: return@forEach
            MadeFiles(store).audiobook(id, record, style = null)
            found++
        }
        prefs.edit().putBoolean(KEY_DONE, true).apply()
        AudoraLog.i(TAG, "Earlier audiobooks listed: $found of ${books.size} books")
    }

    private companion object {
        const val TAG = "Library.Earlier"
        const val PREFS = "made_files"
        const val KEY_DONE = "earlier_audiobooks_found"
    }
}
