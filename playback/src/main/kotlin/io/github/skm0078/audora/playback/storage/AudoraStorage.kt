// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/storage/AudoraStorage.kt
// Purpose: Opens the app's database and hands out its stores, so no other module sees Room types.

package io.github.skm0078.audora.playback.storage

import android.content.Context
import androidx.room.Room
import io.github.skm0078.audora.core.library.BookStore
import io.github.skm0078.audora.core.resume.ResumeAnchorStore
import java.io.Closeable

/** One per process (the app's DI graph holds it). Room opens the file lazily, off the main thread. */
class AudoraStorage private constructor(private val database: AudoraDatabase) : Closeable {
    val books: BookStore = RoomBookStore(database.books())
    val resumeAnchors: ResumeAnchorStore = RoomResumeAnchorStore(database.resumeAnchors())

    override fun close() = database.close()

    companion object {
        const val DATABASE_NAME = "audora.db"

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
