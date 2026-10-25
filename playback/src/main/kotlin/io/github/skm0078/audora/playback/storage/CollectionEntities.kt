// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/storage/CollectionEntities.kt
// Purpose: Room rows for the listener's own collections and which books are in each (schema 5, ADR-068). Added
//          with the outputs so the catalogue needs no second schema change; the screens that use them come next.

package io.github.skm0078.audora.playback.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "collections")
internal data class CollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
)

/** A book in a collection; a book can be in several. Removing either side removes the link. */
@Entity(
    tableName = "book_collections",
    primaryKeys = ["book_id", "collection_id"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collection_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("collection_id")],
)
internal data class BookCollectionEntity(
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "collection_id") val collectionId: String,
    @ColumnInfo(name = "added_at") val addedAtMillis: Long,
)
