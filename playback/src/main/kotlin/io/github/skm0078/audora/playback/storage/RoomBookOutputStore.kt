// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/storage/RoomBookOutputStore.kt
// Purpose: BookOutputStore over Room; write failures become typed errors, as in RoomBookStore.

package io.github.skm0078.audora.playback.storage

import android.database.sqlite.SQLiteException
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.BookOutput
import io.github.skm0078.audora.core.library.BookOutputStore
import io.github.skm0078.audora.core.library.OutputKind
import io.github.skm0078.audora.core.library.StorageError
import io.github.skm0078.audora.core.result.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomBookOutputStore(private val dao: OutputsDao) : BookOutputStore {
    override fun observeAll(): Flow<List<BookOutput>> = dao.observeAll().map { rows ->
        rows.mapNotNull { it.toOutput() }
    }

    override fun observe(bookId: BookId): Flow<List<BookOutput>> =
        dao.observe(bookId.value).map { rows -> rows.mapNotNull { it.toOutput() } }

    override suspend fun replace(
        bookId: BookId,
        kind: OutputKind,
        outputs: List<BookOutput>,
    ): Outcome<Unit, StorageError> = write("replace") {
        require(outputs.all { it.bookId == bookId && it.kind == kind }) { "outputs of another book or kind" }
        dao.replace(bookId.value, kind.name, outputs.map(BookOutputEntity::of))
    }

    override suspend fun add(outputs: List<BookOutput>): Outcome<Unit, StorageError> =
        write("add") { dao.insert(outputs.map(BookOutputEntity::of)) }

    override suspend fun deleteSet(setKey: String): Outcome<Unit, StorageError> =
        write("delete") { dao.deleteSet(setKey) }

    private suspend fun write(operation: String, block: suspend () -> Unit): Outcome<Unit, StorageError> = try {
        block()
        Outcome.Success(Unit)
    } catch (e: SQLiteException) {
        Outcome.Failure(StorageError("outputs $operation: ${e.javaClass.simpleName}: ${e.message}"))
    }
}
