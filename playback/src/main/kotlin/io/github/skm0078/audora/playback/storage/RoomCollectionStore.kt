// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/storage/RoomCollectionStore.kt
// Purpose: CollectionStore over Room; write failures become typed errors, as in RoomBookStore.

package io.github.skm0078.audora.playback.storage

import android.database.sqlite.SQLiteException
import io.github.skm0078.audora.core.library.BookCollection
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.CollectionStore
import io.github.skm0078.audora.core.library.StorageError
import io.github.skm0078.audora.core.result.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomCollectionStore(private val dao: CollectionsDao) : CollectionStore {
    override fun observe(): Flow<List<BookCollection>> = dao.observe().map { rows ->
        rows.map { BookCollection(it.id, it.name, it.createdAtMillis) }
    }

    override fun observeMembers(): Flow<Map<BookId, Set<String>>> = dao.observeMembers().map { rows ->
        rows.groupBy({ BookId(it.bookId) }, { it.collectionId }).mapValues { (_, ids) -> ids.toSet() }
    }

    override suspend fun create(collection: BookCollection): Outcome<Unit, StorageError> = write("create") {
        dao.insert(CollectionEntity(collection.id, collection.name, collection.createdAtMillis))
    }

    override suspend fun rename(id: String, name: String): Outcome<Unit, StorageError> = write("rename") {
        if (dao.rename(id, name) == 0) throw SQLiteException("no collection $id")
    }

    override suspend fun delete(id: String): Outcome<Unit, StorageError> = write("delete") { dao.delete(id) }

    override suspend fun setMember(
        book: BookId,
        id: String,
        member: Boolean,
        atMillis: Long,
    ): Outcome<Unit, StorageError> = write("member") {
        if (member) {
            dao.addMember(BookCollectionEntity(book.value, id, atMillis))
        } else {
            dao.removeMember(book.value, id)
        }
    }

    private suspend fun write(operation: String, block: suspend () -> Unit): Outcome<Unit, StorageError> = try {
        block()
        Outcome.Success(Unit)
    } catch (e: SQLiteException) {
        Outcome.Failure(StorageError("collections $operation: ${e.javaClass.simpleName}: ${e.message}"))
    }
}
