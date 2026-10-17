// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/search/SearchIndexes.kt
// Purpose: Where books' search indexes live (noBackupFilesDir/search, one file per book) and their clean-up.

package io.github.skm0078.audora.playback.search

import android.content.Context
import android.database.SQLException
import io.github.skm0078.audora.core.config.SearchConfig
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import java.io.File
import java.security.MessageDigest

/** Files are named by a hash of the book id, so a file name never carries a title or a path. */
class SearchIndexes(context: Context, private val config: SearchConfig = SearchConfig()) {
    private val folder = File(context.noBackupFilesDir, config.folder)

    /** Opens (or creates) [book]'s index; the caller closes it. */
    fun open(book: BookId): Outcome<SearchIndex, SearchError> = try {
        if (!folder.isDirectory) folder.mkdirs()
        Outcome.Success(SqliteSearchIndex(File(folder, "${stem(book)}$EXTENSION")))
    } catch (e: SQLException) {
        AudoraLog.e(TAG, "Search index not opened, book=${book.logLabel}", e)
        Outcome.Failure(SearchError.Storage(e.message ?: "open failed"))
    }

    /** Deletes the indexes (and SQLite's side files) of every book not in [library]. */
    fun dropBooksNotIn(library: Set<BookId>) {
        val keep = library.mapTo(HashSet(), ::stem)
        val stale = folder.listFiles().orEmpty().filter { it.name.substringBefore('.') !in keep }
        stale.forEach { it.delete() }
        if (stale.isNotEmpty()) AudoraLog.i(TAG, "Search indexes dropped: ${stale.size} files")
    }

    private fun stem(book: BookId): String = MessageDigest.getInstance("SHA-256")
        .digest(book.value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(STEM_CHARS)

    private companion object {
        const val TAG = "Search"
        const val EXTENSION = ".db"
        const val STEM_CHARS = 32
    }
}
