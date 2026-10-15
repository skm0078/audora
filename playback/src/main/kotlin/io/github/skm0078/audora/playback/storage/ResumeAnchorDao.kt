// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/storage/ResumeAnchorDao.kt
// Purpose: Room queries for resume anchors: one row per book, replaced on every save.

package io.github.skm0078.audora.playback.storage

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
internal interface ResumeAnchorDao {
    @Query("SELECT * FROM resume_anchors WHERE book_id = :bookId")
    suspend fun byBookId(bookId: String): ResumeAnchorEntity?

    /** Throws SQLiteConstraintException when the book is gone (foreign key). */
    @Upsert
    suspend fun upsert(anchor: ResumeAnchorEntity)
}
