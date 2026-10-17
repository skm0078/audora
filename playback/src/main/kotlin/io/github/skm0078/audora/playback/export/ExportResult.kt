// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/ExportResult.kt
// Purpose: What an audiobook export produced, and the typed reasons it can stop early.

package io.github.skm0078.audora.playback.export

import io.github.skm0078.audora.pdf.document.PdfPageError
import io.github.skm0078.audora.speech.SpeechError

/** A finished chapter file. [name] is its file name, safe to show; [durationMs] is the audio written to it. */
data class ExportedChapter(val chapter: AudioChapter, val name: String, val durationMs: Long)

/**
 * @property skippedSentences sentences the voice failed on, each left out (as playback skips them).
 * @property skippedPages pages whose text could not be extracted.
 * @property silentChapters chapters with nothing to read (blank or image-only pages without OCR); no file.
 */
data class ExportSummary(
    val chapters: List<ExportedChapter>,
    val skippedSentences: Int,
    val skippedPages: Int,
    val silentChapters: Int,
) {
    val durationMs: Long get() = chapters.sumOf { it.durationMs }
}

/** Why an export stopped. Chapters finished before the stop stay on disk; the one in progress is deleted. */
sealed interface ExportError {
    val message: String

    /** The encoder or the destination failed (full storage, revoked folder, codec error). */
    data class Storage(val detail: String) : ExportError {
        override val message: String get() = "Could not write the audiobook: $detail"
    }

    /** Too many sentences in a row failed: the voice is broken, not one sentence. */
    data class VoiceFailed(val error: SpeechError) : ExportError {
        override val message: String get() = error.message
    }

    /** The PDF can no longer be read (moved, deleted, or the reopen failed). */
    data class BookUnreadable(val page: Int, val error: PdfPageError) : ExportError {
        override val message: String get() = "The PDF could not be read from page ${page + 1}"
    }
}

/** Progress callbacks, on the exporter's thread. Keep them cheap: post to the UI, don't do work here. */
interface ExportListener {
    fun onPageDone(chapter: AudioChapter, page: Int) = Unit

    fun onChapterDone(chapter: ExportedChapter) = Unit

    companion object {
        val NONE = object : ExportListener {}
    }
}
