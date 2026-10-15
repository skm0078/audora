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

    // book_id breaks ties so the answer is stable when two saves share a millisecond.
    @Query("SELECT * FROM resume_anchors ORDER BY saved_at DESC, book_id LIMIT 1")
    suspend fun latest(): ResumeAnchorEntity?

    /** Throws SQLiteConstraintException when the book is gone (foreign key). */
    @Upsert
    suspend fun upsert(anchor: ResumeAnchorEntity)
}
