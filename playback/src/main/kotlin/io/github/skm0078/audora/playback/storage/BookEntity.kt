// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/storage/BookEntity.kt
// Purpose: Room row for a library book; unique URI and fingerprint so a PDF can never be listed twice. Its details
//          (author, language, format, cover, what the listener edited) came with schema 5 (ADR-068).

package io.github.skm0078.audora.playback.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookField
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.CoverChoice
import io.github.skm0078.audora.core.library.DocumentFormat

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
    val author: String? = null,
    val language: String? = null,
    /** A [DocumentFormat] name; null until the book is filled in, or for a name this build does not know. */
    val format: String? = null,
    /** [CoverChoice.encode]; null is the first page. */
    val cover: String? = null,
    /** The [BookField] names the listener edited, comma-separated. */
    @ColumnInfo(name = "edited_fields", defaultValue = "") val editedFields: String = "",
    @ColumnInfo(name = "details_version", defaultValue = "0") val detailsVersion: Int = 0,
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
        author = author,
        language = language,
        format = DocumentFormat.entries.firstOrNull { it.name == format },
        cover = CoverChoice.decode(cover),
        edited = editedFields.split(SEPARATOR).mapNotNullTo(HashSet()) { name ->
            BookField.entries.firstOrNull { it.name == name }
        },
        detailsVersion = detailsVersion,
    )

    companion object {
        private const val SEPARATOR = ","

        fun of(book: Book) = BookEntity(
            id = book.id.value,
            documentUri = book.documentUri,
            title = book.title,
            sizeBytes = book.sizeBytes,
            fingerprint = book.fingerprint,
            pageCount = book.pageCount,
            addedAtMillis = book.addedAtMillis,
            lastOpenedAtMillis = book.lastOpenedAtMillis,
            author = book.author,
            language = book.language,
            format = book.format?.name,
            cover = CoverChoice.encode(book.cover),
            editedFields = book.edited.map { it.name }.sorted().joinToString(SEPARATOR),
            detailsVersion = book.detailsVersion,
        )
    }
}
