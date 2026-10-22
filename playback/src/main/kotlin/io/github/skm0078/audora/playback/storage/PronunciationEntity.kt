// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/storage/PronunciationEntity.kt
// Purpose: Room row for a pronunciation fix (plan item 1); a book's own rules go with the book.

package io.github.skm0078.audora.playback.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.notes.PronunciationRule

@Entity(
    tableName = "pronunciations",
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
internal data class PronunciationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @ColumnInfo(name = "book_id") val bookId: String?,
    val lang: String,
    val written: String,
    val spoken: String,
    @ColumnInfo(name = "whole_word") val wholeWord: Boolean,
) {
    /** Null for a row this version cannot read (a language it does not know). */
    fun toRule(): PronunciationRule? = Lang.entries.firstOrNull { it.name == lang }
        ?.takeIf { written.isNotBlank() }
        ?.let { PronunciationRule(id, bookId?.let(::BookId), it, written, spoken, wholeWord) }

    companion object {
        fun of(rule: PronunciationRule) = PronunciationEntity(
            id = rule.id,
            bookId = rule.bookId?.value,
            lang = rule.lang.name,
            written = rule.written,
            spoken = rule.spoken,
            wholeWord = rule.wholeWord,
        )
    }
}
