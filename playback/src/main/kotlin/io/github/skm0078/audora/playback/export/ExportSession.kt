// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/ExportSession.kt
// Purpose: Decides what an audiobook export keeps from the last one: go on, or replace its files (ADR-034).

package io.github.skm0078.audora.playback.export

import io.github.skm0078.audora.core.logging.AudoraLog
import java.security.MessageDigest

/** Asks whether an earlier export's file still exists and deletes one; the service binds them to MediaStore. */
interface SavedFiles {
    fun exists(location: String): Boolean

    /** False if the file could not be deleted (logged by the implementation). */
    fun delete(location: String): Boolean
}

/**
 * One export of one book. When it starts, the book's last record is weighed against this export's [key]:
 * - the same key and not complete (stopped by Cancel, Android's time limit or a crash): chapters whose files are
 *   still there are [kept] and only the [remaining] ones are rendered;
 * - another key (other voice, style, title or chapters, or a new app build), or a complete export saved again: the
 *   earlier files are deleted first, so saving again replaces the audiobook instead of adding "(1)" copies beside it.
 *
 * Each finished chapter is added to the record at once ([onChapterDone]), so a stop costs only the chapter in progress.
 */
class ExportSession(
    private val records: ExportRecords,
    private val files: SavedFiles,
    private val bookId: String,
    private val key: String,
    chapters: List<AudioChapter>,
    private val logContext: String = "",
) {
    private var record: ExportRecord

    /** Chapters an earlier run of this same export finished, in reading order. */
    val kept: List<ExportedChapter>

    /** The chapters still to render, in reading order. */
    val remaining: List<AudioChapter>

    init {
        val saved = records.load(bookId)
        val byNumber = chapters.associateBy { it.number }
        val keep = if (saved != null && saved.key == key && !saved.complete) {
            saved.chapters.filter { it.number in byNumber && files.exists(it.location) }
        } else {
            saved?.let(::replace)
            emptyList()
        }
        kept = keep.sortedBy { it.number }.map { ExportedChapter(byNumber.getValue(it.number), it.name, it.durationMs) }
        val keptNumbers = keep.mapTo(HashSet()) { it.number }
        remaining = chapters.filterNot { it.number in keptNumbers }
        record = ExportRecord(key, keep)
        records.save(bookId, record)
        if (keep.isNotEmpty()) AudoraLog.i(TAG, "Export goes on: ${keep.size} chapters kept, $logContext")
    }

    /** Pages of [kept] chapters, which the progress counts as done. */
    val keptPages: Int get() = kept.sumOf { it.chapter.lastPage - it.chapter.firstPage + 1 }

    fun onChapterDone(chapter: ExportedChapter) {
        val location = chapter.location ?: return // A plain-file sink (tests) leaves nothing to find again.
        record = record.with(SavedChapter(chapter.chapter.number, chapter.name, chapter.durationMs, location))
        records.save(bookId, record)
    }

    /** The whole audiobook's summary: [kept] chapters and this run's [summary]; the record is marked complete. */
    fun finish(summary: ExportSummary): ExportSummary {
        records.save(bookId, record.copy(complete = true))
        return summary.copy(chapters = (kept + summary.chapters).sortedBy { it.chapter.number })
    }

    private fun replace(saved: ExportRecord) {
        val failed = saved.chapters.count { !files.delete(it.location) }
        AudoraLog.i(TAG, "Earlier audiobook replaced: ${saved.chapters.size - failed} files deleted, $logContext")
        if (failed > 0) AudoraLog.w(TAG, "$failed earlier files could not be deleted, $logContext")
    }

    private companion object {
        const val TAG = "Export.Session"
    }
}

/**
 * An export's key from what its audio and files depend on: the rendering signature, the folder (from the title) and
 * the chapter plan. Hashed, so the record never holds the title.
 */
fun exportKey(signature: String, folder: String, chapters: List<AudioChapter>): String {
    val plan = chapters.joinToString(",") { "${it.number}:${it.firstPage}-${it.lastPage}" }
    return MessageDigest.getInstance("SHA-256")
        .digest("$signature|$folder|$plan".toByteArray())
        .joinToString("") { "%02x".format(it) }
        .take(EXPORT_KEY_HEX_CHARS)
}

private const val EXPORT_KEY_HEX_CHARS = 32
