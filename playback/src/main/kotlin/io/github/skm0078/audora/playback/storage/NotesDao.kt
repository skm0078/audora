// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/storage/NotesDao.kt
// Purpose: Room queries for bookmarks, per-book settings and pronunciation fixes (plan items 29, 4, 1).

package io.github.skm0078.audora.playback.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal interface NotesDao {
    @Query("SELECT * FROM bookmarks WHERE book_id = :bookId ORDER BY page, char_start, id")
    fun observeBookmarks(bookId: String): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    /** Rows changed: 0 when the bookmark is gone. */
    @Query("UPDATE bookmarks SET note = :note WHERE id = :id")
    suspend fun updateBookmarkNote(id: Long, note: String): Int

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Long): Int

    @Query("SELECT * FROM book_settings WHERE book_id = :bookId")
    suspend fun settings(bookId: String): BookSettingsEntity?

    @Upsert
    suspend fun upsertSettings(settings: BookSettingsEntity)

    @Query("DELETE FROM book_settings WHERE book_id = :bookId")
    suspend fun deleteSettings(bookId: String): Int

    @Query("SELECT * FROM pronunciations WHERE book_id IS NULL OR book_id = :bookId ORDER BY id")
    fun observePronunciations(bookId: String?): Flow<List<PronunciationEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPronunciation(rule: PronunciationEntity): Long

    @Query("DELETE FROM pronunciations WHERE id = :id")
    suspend fun deletePronunciation(id: Long): Int
}
