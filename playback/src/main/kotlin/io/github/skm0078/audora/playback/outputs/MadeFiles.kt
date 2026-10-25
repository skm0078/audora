// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/outputs/MadeFiles.kt
// Purpose: Records what a job made from a book in the library's outputs (ADR-068): an audiobook's chapters, a studio
//          audio file, a copy's files. A failed record is logged; the files themselves are already saved.

package io.github.skm0078.audora.playback.outputs

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.BookOutput
import io.github.skm0078.audora.core.library.BookOutputStore
import io.github.skm0078.audora.core.library.OutputKind
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.export.ExportRecord
import io.github.skm0078.audora.playback.export.ExportRecords
import java.util.UUID
import kotlinx.coroutines.runBlocking

/** One file to record: what its job knows about it. */
data class MadeFile(
    val name: String,
    val location: String,
    val mimeType: String,
    val sizeBytes: Long? = null,
    val durationMs: Long? = null,
)

class MadeFiles(private val store: BookOutputStore, private val clock: () -> Long = System::currentTimeMillis) {
    /**
     * A finished audiobook: every chapter of [record] (this run's and those kept from a stopped one), replacing the
     * book's earlier audiobook, as the export replaced its files.
     */
    suspend fun audiobook(bookId: BookId, record: ExportRecord, style: SpeechStyle?) {
        val files = record.chapters.sortedBy { it.number }.map {
            MadeFile(it.name, it.location, AUDIO_MIME, durationMs = it.durationMs)
        }
        record(bookId, OutputKind.AUDIOBOOK, files, style, replace = true)
    }

    /**
     * From the export's own thread, once it finished: [bookId]'s export record (which the export just completed) as
     * its audiobook. Nothing when there is no record.
     */
    fun audiobookBlocking(bookId: BookId, records: ExportRecords, style: SpeechStyle?) {
        val record = records.load(bookId.value) ?: return
        runBlocking { audiobook(bookId, record, style) }
    }

    /** A studio audio file; earlier ones (other pages) stay. */
    suspend fun studioAudio(bookId: BookId, file: MadeFile, style: SpeechStyle?) =
        record(bookId, OutputKind.STUDIO_AUDIO, listOf(file), style, replace = false)

    /** A copy's files (an accessible copy's EPUB and text, or a talking book); earlier copies stay, as their files. */
    suspend fun copy(bookId: BookId, kind: OutputKind, files: List<MadeFile>, style: SpeechStyle?) =
        record(bookId, kind, files, style, replace = false)

    private suspend fun record(
        bookId: BookId,
        kind: OutputKind,
        files: List<MadeFile>,
        style: SpeechStyle?,
        replace: Boolean,
    ) {
        if (files.isEmpty()) return
        val setKey = UUID.randomUUID().toString()
        val now = clock()
        val outputs = files.mapIndexed { index, file ->
            BookOutput(
                bookId = bookId,
                kind = kind,
                setKey = setKey,
                part = index + 1,
                name = file.name,
                location = file.location,
                mimeType = file.mimeType,
                createdAtMillis = now,
                sizeBytes = file.sizeBytes,
                durationMs = file.durationMs,
                rate = style?.rate?.takeIf { kind.isAudio || kind == OutputKind.TALKING_BOOK },
                pitch = style?.pitch?.takeIf { kind.isAudio || kind == OutputKind.TALKING_BOOK },
            )
        }
        val saved = if (replace) store.replace(bookId, kind, outputs) else store.add(outputs)
        when (saved) {
            is Outcome.Success -> AudoraLog.i(TAG, "Recorded $kind, files=${files.size}, book=${bookId.logLabel}")
            is Outcome.Failure -> AudoraLog.w(TAG, "Not recorded $kind, book=${bookId.logLabel}: ${saved.error}")
        }
    }

    companion object {
        private const val TAG = "Library.Made"
        const val AUDIO_MIME = "audio/mp4"
    }
}
