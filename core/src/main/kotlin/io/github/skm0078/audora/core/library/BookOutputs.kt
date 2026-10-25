// File: core/src/main/kotlin/io/github/skm0078/audora/core/library/BookOutputs.kt
// Purpose: What the app made from a book (audiobook chapters, studio audio files, copies) and where each file is,
//          so the library can list, play, share and delete them (ADR-068).

package io.github.skm0078.audora.core.library

import io.github.skm0078.audora.core.result.Outcome
import kotlinx.coroutines.flow.Flow

/** What was made. */
enum class OutputKind {
    /** One chapter file of a saved audiobook (Audiobooks/<title>/). */
    AUDIOBOOK,

    /** Studio audio saved as a file (Audiobooks/<title>/Studio audio, pages a-b.m4a). */
    STUDIO_AUDIO,

    /** An accessible copy: EPUB 3 and plain text, for screen readers and other apps (Documents/ListenLock/). */
    ACCESSIBLE_COPY,

    /** A talking book: EPUB 3 with the audio synced to the text (Documents/ListenLock/). */
    TALKING_BOOK,
    ;

    val isAudio: Boolean get() = this == AUDIOBOOK || this == STUDIO_AUDIO
}

/**
 * One file the app made from a book. Files made by one run share a [setKey] (an audiobook's chapters, a copy's EPUB
 * and text) and are listed together in [part] order; a new run of the same kind replaces the set.
 *
 * @property location where the file is (a MediaStore URI); never logged, as it can hold the title.
 * @property durationMs audio length; null for a document.
 * @property voice the voice id, [rate] and [pitch] it was read with, when known (audio and talking books).
 */
data class BookOutput(
    val bookId: BookId,
    val kind: OutputKind,
    val setKey: String,
    val part: Int,
    val name: String,
    val location: String,
    val mimeType: String,
    val createdAtMillis: Long,
    val sizeBytes: Long? = null,
    val durationMs: Long? = null,
    val voice: String? = null,
    val rate: Float? = null,
    val pitch: Float? = null,
    val id: Long = 0,
)

/** One run's files, as the library shows them: "Audiobook, 12 chapters, 5 h 20 min". */
data class OutputSet(val key: String, val bookId: BookId, val kind: OutputKind, val files: List<BookOutput>) {
    init {
        require(files.isNotEmpty()) { "an output set has files" }
    }

    val createdAtMillis: Long get() = files.maxOf { it.createdAtMillis }
    val durationMs: Long? get() = files.mapNotNull { it.durationMs }.takeIf { it.isNotEmpty() }?.sum()
    val sizeBytes: Long? get() = files.mapNotNull { it.sizeBytes }.takeIf { it.isNotEmpty() }?.sum()

    companion object {
        /** [outputs] grouped by set, newest set first, each set's files in [BookOutput.part] order. */
        fun of(outputs: List<BookOutput>): List<OutputSet> = outputs
            .groupBy { it.setKey }
            .map { (key, files) -> files.sortedBy { it.part }.let { OutputSet(key, it[0].bookId, it[0].kind, it) } }
            .sortedWith(compareByDescending<OutputSet> { it.createdAtMillis }.thenBy { it.key })
    }
}

/** Writes report failures as [StorageError] instead of throwing, like [BookStore]. */
interface BookOutputStore {
    /** Everything made, from every book. Emits again after every change. */
    fun observeAll(): Flow<List<BookOutput>>

    fun observe(bookId: BookId): Flow<List<BookOutput>>

    /** Replaces [bookId]'s [kind] outputs with [outputs] (one run's set): a new audiobook replaces the old one. */
    suspend fun replace(bookId: BookId, kind: OutputKind, outputs: List<BookOutput>): Outcome<Unit, StorageError>

    /** Adds a set beside the ones there (studio audio files of other pages stay). */
    suspend fun add(outputs: List<BookOutput>): Outcome<Unit, StorageError>

    /** Forgets a set; the caller deletes its files first, or leaves them for the listener. */
    suspend fun deleteSet(setKey: String): Outcome<Unit, StorageError>
}
