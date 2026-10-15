// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/storage/BookDao.kt
// Purpose: Room queries for the library, most recently opened or added first.

package io.github.skm0078.audora.playback.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
internal interface BookDao {
    // id breaks ties so the order is stable when two books share a timestamp.
    @Query("SELECT * FROM books ORDER BY COALESCE(last_opened_at, added_at) DESC, id")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun byId(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE fingerprint = :fingerprint")
    suspend fun byFingerprint(fingerprint: String): BookEntity?

    @Query("SELECT * FROM books WHERE document_uri = :documentUri")
    suspend fun byDocumentUri(documentUri: String): BookEntity?

    /** Throws on a duplicate id, URI or fingerprint: the library checks for those first, so it is a bug. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(book: BookEntity)

    /** Rows changed: 0 when the book is gone. */
    @Update
    suspend fun update(book: BookEntity): Int

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: String): Int
}
