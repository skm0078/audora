// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/storage/HighlightEntity.kt
// Purpose: Room rows for highlights (plan item 30) and voice notes (plan item 32), anchored like bookmarks.

package io.github.skm0078.audora.playback.storage

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.notes.Highlight
import io.github.skm0078.audora.core.notes.HighlightColor
import io.github.skm0078.audora.core.notes.VoiceNote
import io.github.skm0078.audora.core.resume.ResumeAnchor

/** A sentence anchor's columns, shared by highlights and voice notes (the bookmark table predates it). */
internal data class AnchorColumns(
    @ColumnInfo(name = "book_id") val bookId: String,
    val page: Int,
    @ColumnInfo(name = "char_start") val charStart: Int,
    val fingerprint: Long,
    @ColumnInfo(name = "tail_fingerprint") val tailFingerprint: Long,
    @ColumnInfo(name = "pipeline_version") val pipelineVersion: Int,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
) {
    fun toAnchor() = ResumeAnchor(
        bookId = BookId(bookId),
        page = page,
        charStart = charStart,
        fingerprint = fingerprint,
        tailFingerprint = tailFingerprint,
        pipelineVersion = pipelineVersion,
        savedAtMillis = createdAtMillis,
    )

    companion object {
        fun of(anchor: ResumeAnchor) = with(anchor) {
            AnchorColumns(bookId.value, page, charStart, fingerprint, tailFingerprint, pipelineVersion, savedAtMillis)
        }
    }
}

@Entity(
    tableName = "highlights",
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
internal data class HighlightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @Embedded val anchor: AnchorColumns,
    @ColumnInfo(name = "end_page") val endPage: Int,
    @ColumnInfo(name = "end_char") val endChar: Int,
    val quote: String,
    val note: String,
    val color: String,
) {
    /** Null for a row whose colour this build does not know (a newer build wrote it). */
    fun toHighlight(): Highlight? = HighlightColor.entries.find { it.name == color }?.let { known ->
        Highlight(id, anchor.toAnchor(), endPage, endChar, quote, note, known)
    }

    companion object {
        fun of(highlight: Highlight) = with(highlight) {
            HighlightEntity(id, AnchorColumns.of(anchor), endPage, endChar, quote, note, color.name)
        }
    }
}

@Entity(
    tableName = "voice_notes",
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
internal data class VoiceNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @Embedded val anchor: AnchorColumns,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
) {
    fun toVoiceNote() = VoiceNote(id, anchor.toAnchor(), fileName, durationMs)

    companion object {
        fun of(note: VoiceNote) =
            VoiceNoteEntity(note.id, AnchorColumns.of(note.anchor), note.fileName, note.durationMs)
    }
}
