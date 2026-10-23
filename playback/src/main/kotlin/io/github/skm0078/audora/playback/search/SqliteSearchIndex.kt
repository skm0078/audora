// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/search/SqliteSearchIndex.kt
// Purpose: A book's search index as an SQLite FTS4 table in its own file, outside Room and outside backups.

package io.github.skm0078.audora.playback.search

import android.content.ContentValues
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import java.io.File

/**
 * Not in the Room database: the index is a cache of the pipeline's output (megabytes for a long book) that backup
 * must leave out and a pipeline change throws away, so each book gets a file it can simply delete.
 *
 * Tables: `units` (FTS4; only `terms` is indexed, written by [SearchTerms.indexed]), `meta` (one row: pipeline
 * version, page count, pages done) and `missing` (pages that could not be read).
 */
internal class SqliteSearchIndex(file: File) : SearchIndex {
    private val db: SQLiteDatabase = SQLiteDatabase.openDatabase(
        file.path,
        null,
        SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY,
    ).apply {
        execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS units USING fts4(" +
                "page, char_start, text, terms, notindexed=page, notindexed=char_start, notindexed=text)",
        )
        execSQL(
            "CREATE TABLE IF NOT EXISTS meta (id INTEGER PRIMARY KEY CHECK (id = 0), " +
                "pipeline_version INTEGER NOT NULL, page_count INTEGER NOT NULL, pages_done INTEGER NOT NULL)",
        )
        execSQL("CREATE TABLE IF NOT EXISTS missing (page INTEGER PRIMARY KEY)")
    }

    override fun progress(): IndexProgress? = try {
        db.rawQuery("SELECT pipeline_version, page_count, pages_done FROM meta WHERE id = 0", null).use { row ->
            if (row.moveToFirst()) IndexProgress(row.getInt(0), row.getInt(1), row.getInt(2), missingPages()) else null
        }
    } catch (e: SQLException) {
        null.also { e.logged("read progress") }
    }

    override fun reset(pipelineVersion: Int, pageCount: Int): Outcome<Unit, SearchError> = write("reset") {
        execSQL("DELETE FROM units")
        execSQL("DELETE FROM missing")
        execSQL(
            "INSERT OR REPLACE INTO meta (id, pipeline_version, page_count, pages_done) VALUES (0, ?, ?, 0)",
            arrayOf<Any>(pipelineVersion, pageCount),
        )
    }

    override fun addPages(pages: List<IndexedPage>): Outcome<Unit, SearchError> = write("add pages") {
        // Only a page tried before (a retry) can have rows; skipping the delete otherwise saves a table scan each.
        val done = progress()?.pagesDone ?: 0
        for (page in pages) {
            if (page.page < done) execSQL("DELETE FROM units WHERE page = ?", arrayOf<Any>(page.page))
            val units = page.units
            if (units == null) {
                execSQL("INSERT OR IGNORE INTO missing (page) VALUES (?)", arrayOf<Any>(page.page))
                continue
            }
            execSQL("DELETE FROM missing WHERE page = ?", arrayOf<Any>(page.page))
            for (unit in units) {
                insert(
                    "units",
                    null,
                    ContentValues().apply {
                        put("page", unit.page)
                        put("char_start", unit.charStart)
                        put("text", unit.text)
                        put("terms", SearchTerms.indexed(unit.text))
                    },
                )
            }
        }
        pages.maxOfOrNull { it.page }?.let { last ->
            execSQL("UPDATE meta SET pages_done = MAX(pages_done, ?) WHERE id = 0", arrayOf<Any>(last + 1))
        }
    }

    override fun search(match: String, limit: Int): Outcome<List<SearchHit>, SearchError> = try {
        val query = "SELECT page, char_start, text FROM units WHERE terms MATCH ? " +
            "ORDER BY CAST(page AS INTEGER), CAST(char_start AS INTEGER) LIMIT ?"
        db.rawQuery(query, arrayOf(match, limit.toString())).use { rows ->
            val hits = ArrayList<SearchHit>(rows.count)
            while (rows.moveToNext()) hits += SearchHit(rows.getInt(0), rows.getInt(1), rows.getString(2))
            Outcome.Success(hits)
        }
    } catch (e: SQLException) {
        Outcome.Failure(e.logged("search"))
    }

    override fun close() = db.close()

    private fun missingPages(): Set<Int> = db.rawQuery("SELECT page FROM missing", null).use { rows ->
        buildSet { while (rows.moveToNext()) add(rows.getInt(0)) }
    }

    private inline fun write(what: String, block: SQLiteDatabase.() -> Unit): Outcome<Unit, SearchError> = try {
        db.beginTransaction()
        try {
            db.block()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        Outcome.Success(Unit)
    } catch (e: SQLException) {
        Outcome.Failure(e.logged(what))
    }

    private fun SQLException.logged(what: String): SearchError {
        AudoraLog.e(TAG, "Search index failed to $what", this)
        return SearchError.Storage(message ?: what)
    }

    private companion object {
        const val TAG = "Search.Index"
    }
}
