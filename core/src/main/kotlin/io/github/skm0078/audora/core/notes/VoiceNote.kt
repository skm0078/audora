// File: core/src/main/kotlin/io/github/skm0078/audora/core/notes/VoiceNote.kt
// Purpose: A spoken note recorded at a sentence (plan item 32), anchored like a bookmark; its audio is a file.

package io.github.skm0078.audora.core.notes

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.StorageError
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.resume.ResumeAnchor
import kotlinx.coroutines.flow.Flow

/**
 * @property fileName the recording's file in the app's voice-note folder (its private files, never shared storage).
 * @property id 0 until stored.
 */
data class VoiceNote(val id: Long, val anchor: ResumeAnchor, val fileName: String, val durationMs: Long) {
    init {
        require(fileName.isNotBlank() && '/' !in fileName) { "fileName must be a plain file name" }
        require(durationMs >= 0) { "durationMs must be >= 0, was $durationMs" }
    }

    val bookId: BookId get() = anchor.bookId

    val logLabel: String get() = "voice-note=$id ${anchor.logLabel} ${durationMs}ms"
}

/** Removing a book removes its voice notes' rows (the database cascades); their files are pruned by the app. */
interface VoiceNoteStore {
    fun observe(bookId: BookId): Flow<List<VoiceNote>>

    fun observeAll(): Flow<List<VoiceNote>>

    suspend fun add(note: VoiceNote): Outcome<Long, StorageError>

    suspend fun delete(id: Long): Outcome<Unit, StorageError>
}
