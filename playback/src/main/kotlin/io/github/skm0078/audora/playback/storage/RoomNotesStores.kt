// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/storage/RoomNotesStores.kt
// Purpose: Bookmark, settings, pronunciation, highlight and voice-note stores over Room; failures become typed errors.

package io.github.skm0078.audora.playback.storage

import android.database.sqlite.SQLiteException
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.StorageError
import io.github.skm0078.audora.core.notes.BookSettings
import io.github.skm0078.audora.core.notes.BookSettingsStore
import io.github.skm0078.audora.core.notes.Bookmark
import io.github.skm0078.audora.core.notes.BookmarkStore
import io.github.skm0078.audora.core.notes.Highlight
import io.github.skm0078.audora.core.notes.HighlightStore
import io.github.skm0078.audora.core.notes.PronunciationRule
import io.github.skm0078.audora.core.notes.PronunciationStore
import io.github.skm0078.audora.core.notes.VoiceNote
import io.github.skm0078.audora.core.notes.VoiceNoteStore
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomBookmarkStore(private val dao: NotesDao) : BookmarkStore {
    override fun observe(bookId: BookId): Flow<List<Bookmark>> =
        dao.observeBookmarks(bookId.value).map { rows -> rows.map { it.toBookmark() } }

    override suspend fun add(bookmark: Bookmark): Outcome<Long, StorageError> =
        write("add bookmark") { dao.insertBookmark(BookmarkEntity.of(bookmark.copy(id = 0))) }

    override suspend fun updateNote(id: Long, note: String): Outcome<Unit, StorageError> =
        write("update bookmark") { dao.updateBookmarkNote(id, note) }.expectRow("bookmark $id")

    override suspend fun delete(id: Long): Outcome<Unit, StorageError> =
        write("delete bookmark") { dao.deleteBookmark(id) }.expectRow("bookmark $id")
}

internal class RoomBookSettingsStore(private val dao: NotesDao) : BookSettingsStore {
    override suspend fun get(bookId: BookId): BookSettings? = dao.settings(bookId.value)?.toSettings()

    override suspend fun put(settings: BookSettings): Outcome<Unit, StorageError> =
        write("put book settings") { dao.upsertSettings(BookSettingsEntity.of(settings)) }

    override suspend fun clear(bookId: BookId): Outcome<Unit, StorageError> =
        write("clear book settings") { dao.deleteSettings(bookId.value) }.map { }
}

internal class RoomPronunciationStore(private val dao: NotesDao) : PronunciationStore {
    override fun observe(bookId: BookId?): Flow<List<PronunciationRule>> =
        dao.observePronunciations(bookId?.value).map { rows -> rows.mapNotNull { it.toRule() } }

    override suspend fun add(rule: PronunciationRule): Outcome<Long, StorageError> =
        write("add pronunciation") { dao.insertPronunciation(PronunciationEntity.of(rule.copy(id = 0))) }

    override suspend fun delete(id: Long): Outcome<Unit, StorageError> =
        write("delete pronunciation") { dao.deletePronunciation(id) }.expectRow("pronunciation $id")
}

internal class RoomHighlightStore(private val dao: MarksDao) : HighlightStore {
    override fun observe(bookId: BookId): Flow<List<Highlight>> =
        dao.observeHighlights(bookId.value).map { rows -> rows.mapNotNull { it.toHighlight() } }

    override fun observeAll(): Flow<List<Highlight>> =
        dao.observeAllHighlights().map { rows -> rows.mapNotNull { it.toHighlight() } }

    override suspend fun add(highlight: Highlight): Outcome<Long, StorageError> =
        write("add highlight") { dao.insertHighlight(HighlightEntity.of(highlight.copy(id = 0))) }

    override suspend fun updateNote(id: Long, note: String): Outcome<Unit, StorageError> =
        write("update highlight") { dao.updateHighlightNote(id, note) }.expectRow("highlight $id")

    override suspend fun delete(id: Long): Outcome<Unit, StorageError> =
        write("delete highlight") { dao.deleteHighlight(id) }.expectRow("highlight $id")
}

internal class RoomVoiceNoteStore(private val dao: MarksDao) : VoiceNoteStore {
    override fun observe(bookId: BookId): Flow<List<VoiceNote>> =
        dao.observeVoiceNotes(bookId.value).map { rows -> rows.map { it.toVoiceNote() } }

    override fun observeAll(): Flow<List<VoiceNote>> =
        dao.observeAllVoiceNotes().map { rows -> rows.map { it.toVoiceNote() } }

    override suspend fun add(note: VoiceNote): Outcome<Long, StorageError> =
        write("add voice note") { dao.insertVoiceNote(VoiceNoteEntity.of(note.copy(id = 0))) }

    override suspend fun delete(id: Long): Outcome<Unit, StorageError> =
        write("delete voice note") { dao.deleteVoiceNote(id) }.expectRow("voice note $id")
}

private inline fun <T> write(what: String, block: () -> T): Outcome<T, StorageError> = try {
    Outcome.Success(block())
} catch (e: SQLiteException) {
    Outcome.Failure(StorageError("$what: ${e.javaClass.simpleName}: ${e.message}"))
}

/** A write that changed no row means the row was already gone. */
private fun Outcome<Int, StorageError>.expectRow(what: String): Outcome<Unit, StorageError> = when (this) {
    is Outcome.Success -> if (value > 0) Outcome.Success(Unit) else Outcome.Failure(StorageError("$what not found"))
    is Outcome.Failure -> this
}
