// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/storage/BookmarkEntity.kt
// Purpose: Room row for a bookmark (plan item 29): the anchor's offsets and hashes plus the listener's note.

package io.github.skm0078.audora.playback.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.notes.Bookmark
import io.github.skm0078.audora.core.resume.ResumeAnchor

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["book_id"])],
)
internal data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @ColumnInfo(name = "book_id") val bookId: String,
    val page: Int,
    @ColumnInfo(name = "char_start") val charStart: Int,
    val fingerprint: Long,
    @ColumnInfo(name = "tail_fingerprint") val tailFingerprint: Long,
    @ColumnInfo(name = "pipeline_version") val pipelineVersion: Int,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    val note: String,
) {
    fun toBookmark() = Bookmark(
        id = id,
        anchor = ResumeAnchor(
            bookId = BookId(bookId),
            page = page,
            charStart = charStart,
            fingerprint = fingerprint,
            tailFingerprint = tailFingerprint,
            pipelineVersion = pipelineVersion,
            savedAtMillis = createdAtMillis,
        ),
        note = note,
    )

    companion object {
        fun of(bookmark: Bookmark) = with(bookmark.anchor) {
            BookmarkEntity(
                id = bookmark.id,
                bookId = bookId.value,
                page = page,
                charStart = charStart,
                fingerprint = fingerprint,
                tailFingerprint = tailFingerprint,
                pipelineVersion = pipelineVersion,
                createdAtMillis = savedAtMillis,
                note = bookmark.note,
            )
        }
    }
}
