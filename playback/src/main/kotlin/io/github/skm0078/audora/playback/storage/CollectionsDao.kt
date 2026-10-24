// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/storage/CollectionsDao.kt
// Purpose: Room queries for the listener's collections and their books (ADR-069).

package io.github.skm0078.audora.playback.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface CollectionsDao {
    @Query("SELECT * FROM collections ORDER BY name COLLATE NOCASE, created_at")
    fun observe(): Flow<List<CollectionEntity>>

    /** Every link at once: one query for the whole library instead of one per book row. */
    @Query("SELECT * FROM book_collections")
    fun observeMembers(): Flow<List<BookCollectionEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(collection: CollectionEntity)

    /** Rows changed; 0 when the collection is gone. */
    @Query("UPDATE collections SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String): Int

    /** Its links go with it (the foreign key cascades); the books stay. */
    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun delete(id: String)

    /** Adding a book twice keeps the first time it was added. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addMember(link: BookCollectionEntity)

    @Query("DELETE FROM book_collections WHERE book_id = :bookId AND collection_id = :collectionId")
    suspend fun removeMember(bookId: String, collectionId: String)
}
