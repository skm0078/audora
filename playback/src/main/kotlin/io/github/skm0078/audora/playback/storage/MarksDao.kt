// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/storage/MarksDao.kt
// Purpose: Room queries for highlights (plan item 30) and voice notes (plan item 32).

package io.github.skm0078.audora.playback.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface MarksDao {
    @Query("SELECT * FROM highlights WHERE book_id = :bookId ORDER BY page, char_start, id")
    fun observeHighlights(bookId: String): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights ORDER BY book_id, page, char_start, id")
    fun observeAllHighlights(): Flow<List<HighlightEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHighlight(highlight: HighlightEntity): Long

    @Query("UPDATE highlights SET note = :note WHERE id = :id")
    suspend fun updateHighlightNote(id: Long, note: String): Int

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun deleteHighlight(id: Long): Int

    @Query("SELECT * FROM voice_notes WHERE book_id = :bookId ORDER BY page, char_start, id")
    fun observeVoiceNotes(bookId: String): Flow<List<VoiceNoteEntity>>

    @Query("SELECT * FROM voice_notes ORDER BY book_id, page, char_start, id")
    fun observeAllVoiceNotes(): Flow<List<VoiceNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertVoiceNote(note: VoiceNoteEntity): Long

    @Query("DELETE FROM voice_notes WHERE id = :id")
    suspend fun deleteVoiceNote(id: Long): Int
}
