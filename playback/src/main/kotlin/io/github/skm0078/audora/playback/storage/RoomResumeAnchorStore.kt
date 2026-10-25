// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/storage/RoomResumeAnchorStore.kt
// Purpose: ResumeAnchorStore over Room; write failures (book removed, disk full) become typed errors.

package io.github.skm0078.audora.playback.storage

import android.database.sqlite.SQLiteException
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.StorageError
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.resume.ResumeAnchor
import io.github.skm0078.audora.core.resume.ResumeAnchorStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomResumeAnchorStore(private val dao: ResumeAnchorDao) : ResumeAnchorStore {
    override suspend fun get(bookId: BookId): ResumeAnchor? = dao.byBookId(bookId.value)?.toAnchor()

    override suspend fun latest(): ResumeAnchor? = dao.latest()?.toAnchor()

    override fun observeAll(): Flow<List<ResumeAnchor>> = dao.observeAll().map { rows -> rows.map { it.toAnchor() } }

    override suspend fun put(anchor: ResumeAnchor): Outcome<Unit, StorageError> = try {
        dao.upsert(ResumeAnchorEntity.of(anchor))
        Outcome.Success(Unit)
    } catch (e: SQLiteException) {
        Outcome.Failure(StorageError("put anchor: ${e.javaClass.simpleName}: ${e.message}"))
    }

    override suspend fun clear(bookId: BookId): Outcome<Unit, StorageError> = try {
        dao.delete(bookId.value)
        Outcome.Success(Unit)
    } catch (e: SQLiteException) {
        Outcome.Failure(StorageError("clear anchor: ${e.javaClass.simpleName}: ${e.message}"))
    }
}
