// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/storage/BookOutputEntity.kt
// Purpose: Room row for one file the app made from a book (schema 5, ADR-068); forgotten with its book, the file kept.

package io.github.skm0078.audora.playback.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.BookOutput
import io.github.skm0078.audora.core.library.OutputKind

@Entity(
    tableName = "book_outputs",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("book_id"), Index("set_key"), Index(value = ["location"], unique = true)],
)
internal data class BookOutputEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @ColumnInfo(name = "book_id") val bookId: String,
    val kind: String,
    @ColumnInfo(name = "set_key") val setKey: String,
    val part: Int,
    val name: String,
    val location: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long?,
    @ColumnInfo(name = "duration_ms") val durationMs: Long?,
    val voice: String?,
    val rate: Float?,
    val pitch: Float?,
) {
    /** Null for a kind this build does not know (a row written by a newer version, restored from its backup). */
    fun toOutput(): BookOutput? = OutputKind.entries.firstOrNull { it.name == kind }?.let { known ->
        BookOutput(
            bookId = BookId(bookId),
            kind = known,
            setKey = setKey,
            part = part,
            name = name,
            location = location,
            mimeType = mimeType,
            createdAtMillis = createdAtMillis,
            sizeBytes = sizeBytes,
            durationMs = durationMs,
            voice = voice,
            rate = rate,
            pitch = pitch,
            id = id,
        )
    }

    companion object {
        fun of(output: BookOutput) = BookOutputEntity(
            id = output.id,
            bookId = output.bookId.value,
            kind = output.kind.name,
            setKey = output.setKey,
            part = output.part,
            name = output.name,
            location = output.location,
            mimeType = output.mimeType,
            createdAtMillis = output.createdAtMillis,
            sizeBytes = output.sizeBytes,
            durationMs = output.durationMs,
            voice = output.voice,
            rate = output.rate,
            pitch = output.pitch,
        )
    }
}
