// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/storage/BookSettingsEntity.kt
// Purpose: Room row for one book's own speed, pace and voices (plan item 4); deleted with its book.

package io.github.skm0078.audora.playback.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.Pace
import io.github.skm0078.audora.core.notes.BookSettings

@Entity(
    tableName = "book_settings",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class BookSettingsEntity(
    @PrimaryKey @ColumnInfo(name = "book_id") val bookId: String,
    val rate: Float?,
    val pace: String?,
    val voices: String,
) {
    fun toSettings() = BookSettings(
        bookId = BookId(bookId),
        rate = rate?.takeIf { it > 0f && it.isFinite() },
        pace = Pace.entries.firstOrNull { it.name == pace },
        voices = BookSettings.decodeVoices(voices),
    )

    companion object {
        fun of(settings: BookSettings) = BookSettingsEntity(
            bookId = settings.bookId.value,
            rate = settings.rate,
            pace = settings.pace?.name,
            voices = BookSettings.encodeVoices(settings.voices),
        )
    }
}
