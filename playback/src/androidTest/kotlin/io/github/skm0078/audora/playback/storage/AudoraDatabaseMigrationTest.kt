// File: playback/src/androidTest/kotlin/io/github/skm0078/audora/playback/storage/AudoraDatabaseMigrationTest.kt
// Purpose: A version 1 database (P1-01) upgrades to version 2 keeping every book, and anchors work afterwards.

package io.github.skm0078.audora.playback.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.resume.ResumeAnchor
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudoraDatabaseMigrationTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        context.deleteDatabase(NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(NAME)
    }

    @Test
    fun version1LibrarySurvivesTheUpgrade() = runBlocking {
        writeVersion1Database()

        AudoraStorage.open(context, NAME).use { storage ->
            val book = storage.books.byId(BookId(BOOK_ID))
            assertEquals("Moby Dick", book?.title)
            assertEquals(574, book?.pageCount)

            val anchor = ResumeAnchor(BookId(BOOK_ID), 12, 345, 1L, 2L, 1, 3L)
            assertEquals(Outcome.Success(Unit), storage.resumeAnchors.put(anchor))
            assertEquals(anchor, storage.resumeAnchors.get(BookId(BOOK_ID)))
        }
    }

    /** Exactly what P1-01's Room wrote (schemas/…/1.json), with one book in it. */
    private fun writeVersion1Database() {
        val file = context.getDatabasePath(NAME).also { it.parentFile?.mkdirs() }
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        db.use {
            V1_SQL.forEach(it::execSQL)
            it.execSQL(
                "INSERT INTO books VALUES ('$BOOK_ID', 'content://test/moby', 'Moby Dick', 1234, 'v1:moby', " +
                    "574, 10, 20)",
            )
            it.version = 1
        }
    }

    private companion object {
        const val NAME = "migration-test.db"
        const val BOOK_ID = "3f2a9c01-book"
        const val V1_HASH = "ebac7fab0df2390df4e5a769185fe3f7"

        val V1_SQL = listOf(
            "CREATE TABLE IF NOT EXISTS `books` (`id` TEXT NOT NULL, `document_uri` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, `size_bytes` INTEGER NOT NULL, `fingerprint` TEXT NOT NULL, " +
                "`page_count` INTEGER, `added_at` INTEGER NOT NULL, `last_opened_at` INTEGER, PRIMARY KEY(`id`))",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_books_document_uri` ON `books` (`document_uri`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_books_fingerprint` ON `books` (`fingerprint`)",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '$V1_HASH')",
        )
    }
}
