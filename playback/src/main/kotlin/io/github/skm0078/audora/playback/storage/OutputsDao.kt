// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/storage/OutputsDao.kt
// Purpose: Room queries for what the app made from each book (ADR-068).

package io.github.skm0078.audora.playback.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
internal interface OutputsDao {
    @Query("SELECT * FROM book_outputs ORDER BY created_at DESC, set_key, part")
    fun observeAll(): Flow<List<BookOutputEntity>>

    @Query("SELECT * FROM book_outputs WHERE book_id = :bookId ORDER BY created_at DESC, set_key, part")
    fun observe(bookId: String): Flow<List<BookOutputEntity>>

    /** A file written again at the same place (a replaced audiobook chapter) replaces its old row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(outputs: List<BookOutputEntity>)

    @Query("DELETE FROM book_outputs WHERE book_id = :bookId AND kind = :kind")
    suspend fun deleteKind(bookId: String, kind: String)

    @Query("DELETE FROM book_outputs WHERE set_key = :setKey")
    suspend fun deleteSet(setKey: String)

    @Transaction
    suspend fun replace(bookId: String, kind: String, outputs: List<BookOutputEntity>) {
        deleteKind(bookId, kind)
        insert(outputs)
    }
}
