// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/storage/ResumeAnchorEntity.kt
// Purpose: Room row for a book's resume anchor; deleted with its book, so a re-added PDF starts fresh.

package io.github.skm0078.audora.playback.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.resume.ResumeAnchor

@Entity(
    tableName = "resume_anchors",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class ResumeAnchorEntity(
    @PrimaryKey @ColumnInfo(name = "book_id") val bookId: String,
    val page: Int,
    @ColumnInfo(name = "char_start") val charStart: Int,
    val fingerprint: Long,
    @ColumnInfo(name = "tail_fingerprint") val tailFingerprint: Long,
    @ColumnInfo(name = "pipeline_version") val pipelineVersion: Int,
    @ColumnInfo(name = "saved_at") val savedAtMillis: Long,
) {
    fun toAnchor() = ResumeAnchor(
        bookId = BookId(bookId),
        page = page,
        charStart = charStart,
        fingerprint = fingerprint,
        tailFingerprint = tailFingerprint,
        pipelineVersion = pipelineVersion,
        savedAtMillis = savedAtMillis,
    )

    companion object {
        fun of(anchor: ResumeAnchor) = ResumeAnchorEntity(
            bookId = anchor.bookId.value,
            page = anchor.page,
            charStart = anchor.charStart,
            fingerprint = anchor.fingerprint,
            tailFingerprint = anchor.tailFingerprint,
            pipelineVersion = anchor.pipelineVersion,
            savedAtMillis = anchor.savedAtMillis,
        )
    }
}
