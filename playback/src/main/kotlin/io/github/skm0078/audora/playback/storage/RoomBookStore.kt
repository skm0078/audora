// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/storage/RoomBookStore.kt
// Purpose: BookStore over Room; write failures (disk full, constraint, corruption) become typed errors.

package io.github.skm0078.audora.playback.storage

import android.database.sqlite.SQLiteException
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.BookStore
import io.github.skm0078.audora.core.library.StorageError
import io.github.skm0078.audora.core.result.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomBookStore(private val dao: BookDao) : BookStore {
    override fun observeAll(): Flow<List<Book>> = dao.observeAll().map { rows -> rows.map(BookEntity::toBook) }

    override suspend fun byId(id: BookId): Book? = dao.byId(id.value)?.toBook()

    override suspend fun byFingerprint(fingerprint: String): Book? = dao.byFingerprint(fingerprint)?.toBook()

    override suspend fun byDocumentUri(documentUri: String): Book? = dao.byDocumentUri(documentUri)?.toBook()

    override suspend fun insert(book: Book): Outcome<Unit, StorageError> = write("insert") {
        dao.insert(BookEntity.of(book))
        null
    }

    override suspend fun update(book: Book): Outcome<Unit, StorageError> = write("update") {
        if (dao.update(BookEntity.of(book)) == 0) "no row for book ${book.id.logLabel}" else null
    }

    override suspend fun delete(id: BookId): Outcome<Unit, StorageError> = write("delete") {
        dao.delete(id.value)
        null
    }

    /** [block] returns null on success or why the write did nothing. */
    private suspend fun write(operation: String, block: suspend () -> String?): Outcome<Unit, StorageError> = try {
        val problem = block()
        if (problem == null) Outcome.Success(Unit) else Outcome.Failure(StorageError("$operation: $problem"))
    } catch (e: SQLiteException) {
        Outcome.Failure(StorageError("$operation: ${e.javaClass.simpleName}: ${e.message}"))
    }
}
