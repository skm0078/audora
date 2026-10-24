// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/storage/AudoraStorage.kt
// Purpose: Opens the app's database and hands out its stores, so no other module sees Room types.

package io.github.skm0078.audora.playback.storage

import android.content.Context
import androidx.room.Room
import io.github.skm0078.audora.core.library.BookOutputStore
import io.github.skm0078.audora.core.library.BookStore
import io.github.skm0078.audora.core.notes.BookSettingsStore
import io.github.skm0078.audora.core.notes.BookmarkStore
import io.github.skm0078.audora.core.notes.HighlightStore
import io.github.skm0078.audora.core.notes.PronunciationStore
import io.github.skm0078.audora.core.notes.VoiceNoteStore
import io.github.skm0078.audora.core.resume.ResumeAnchorStore
import java.io.Closeable
import java.io.File

/** One per process (the app's DI graph holds it). Room opens the file lazily, off the main thread. */
class AudoraStorage private constructor(private val database: AudoraDatabase) : Closeable {
    val books: BookStore = RoomBookStore(database.books())
    val resumeAnchors: ResumeAnchorStore = RoomResumeAnchorStore(database.resumeAnchors())
    val bookmarks: BookmarkStore = RoomBookmarkStore(database.notes())
    val bookSettings: BookSettingsStore = RoomBookSettingsStore(database.notes())
    val pronunciations: PronunciationStore = RoomPronunciationStore(database.notes())
    val highlights: HighlightStore = RoomHighlightStore(database.marks())
    val voiceNotes: VoiceNoteStore = RoomVoiceNoteStore(database.marks())
    val outputs: BookOutputStore = RoomBookOutputStore(database.outputs())

    /**
     * Writes a consistent copy of the whole database to [target] (which must not exist) with `VACUUM INTO`, while
     * the app keeps reading and writing: SQLite copies one read transaction, WAL contents included. Blocking; call
     * off the main thread. Throws SQLiteException if it fails.
     */
    fun snapshotTo(target: File) {
        database.openHelper.writableDatabase.execSQL("VACUUM INTO ?", arrayOf(target.absolutePath))
    }

    /** Room's hash of the schema this build expects, as stored in the live database; null before Room created it. */
    fun identityHash(): String? = database.openHelper.readableDatabase.query(IDENTITY_HASH_QUERY).use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }

    override fun close() = database.close()

    companion object {
        const val DATABASE_NAME = "audora.db"

        /** The database schema version this build writes. */
        const val SCHEMA_VERSION = DATABASE_VERSION

        /** Where Room records the schema's identity hash (its fixed row id 42), in any Room database. */
        const val IDENTITY_HASH_QUERY = "SELECT identity_hash FROM room_master_table WHERE id = 42"

        /** [name] other than the default is for tests that reopen a database file, as after a process death. */
        fun open(context: Context, name: String = DATABASE_NAME): AudoraStorage = AudoraStorage(
            Room.databaseBuilder(context.applicationContext, AudoraDatabase::class.java, name).build(),
        )

        /** Tests only: a fresh database that lives in memory and disappears on [close]. */
        fun inMemory(context: Context): AudoraStorage = AudoraStorage(
            Room.inMemoryDatabaseBuilder(context.applicationContext, AudoraDatabase::class.java).build(),
        )
    }
}
