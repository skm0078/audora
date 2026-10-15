// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/storage/BookEntity.kt
// Purpose: Room row for a library book; unique URI and fingerprint so a PDF can never be listed twice.

package io.github.skm0078.audora.playback.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId

@Entity(
    tableName = "books",
    indices = [
        Index(value = ["document_uri"], unique = true),
        Index(value = ["fingerprint"], unique = true),
    ],
)
internal data class BookEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "document_uri") val documentUri: String,
    val title: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    val fingerprint: String,
    @ColumnInfo(name = "page_count") val pageCount: Int?,
    @ColumnInfo(name = "added_at") val addedAtMillis: Long,
    @ColumnInfo(name = "last_opened_at") val lastOpenedAtMillis: Long?,
) {
    fun toBook() = Book(
        id = BookId(id),
        documentUri = documentUri,
        title = title,
        sizeBytes = sizeBytes,
        fingerprint = fingerprint,
        pageCount = pageCount,
        addedAtMillis = addedAtMillis,
        lastOpenedAtMillis = lastOpenedAtMillis,
    )

    companion object {
        fun of(book: Book) = BookEntity(
            id = book.id.value,
            documentUri = book.documentUri,
            title = book.title,
            sizeBytes = book.sizeBytes,
            fingerprint = book.fingerprint,
            pageCount = book.pageCount,
            addedAtMillis = book.addedAtMillis,
            lastOpenedAtMillis = book.lastOpenedAtMillis,
        )
    }
}
