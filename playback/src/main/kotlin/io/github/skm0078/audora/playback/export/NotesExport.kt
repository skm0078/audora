// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/NotesExport.kt
// Purpose: Saves a book's highlights and notes to Documents/Audora as Markdown and plain text (plan item 30).

package io.github.skm0078.audora.playback.export

import android.content.Context
import io.github.skm0078.audora.core.config.AudiobookConfig
import io.github.skm0078.audora.core.config.EpubConfig
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.notes.BookmarkStore
import io.github.skm0078.audora.core.notes.HighlightStore
import io.github.skm0078.audora.core.notes.VoiceNoteStore
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.flatMap
import io.github.skm0078.audora.core.result.map
import io.github.skm0078.audora.playback.R
import io.github.skm0078.audora.playback.epub.CopyError
import io.github.skm0078.audora.playback.epub.SharedDocuments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** The stores a notes export reads. */
class MarkStores(val highlights: HighlightStore, val bookmarks: BookmarkStore, val voiceNotes: VoiceNoteStore)

object NotesExport {
    private const val TAG = "Export.Notes"
    private const val MARKDOWN_MIME = "text/markdown"
    private const val TEXT_MIME = "text/plain"

    /** Gathers [id]'s highlights, bookmarks and voice notes, then [save]s them; NothingToCopy when there are none. */
    suspend fun export(
        context: Context,
        id: BookId,
        title: String,
        stores: MarkStores,
    ): Outcome<List<String>, CopyError> {
        val marks = BookMarks(
            title = title,
            highlights = stores.highlights.observe(id).first(),
            bookmarks = stores.bookmarks.observe(id).first(),
            voiceNotes = stores.voiceNotes.observe(id).first(),
        )
        return if (marks.isEmpty) Outcome.Failure(CopyError.NothingToCopy) else save(context, marks)
    }

    /**
     * Writes `<title> - notes.md` and `.txt` beside the accessible copies; returns their names (safe to show). The
     * listener's quotes and notes are written, never logged. Main-safe.
     */
    suspend fun save(
        context: Context,
        marks: BookMarks,
        config: EpubConfig = EpubConfig(),
    ): Outcome<List<String>, CopyError> = withContext(Dispatchers.IO) {
        val stem = context.getString(
            R.string.notes_file,
            AudiobookFolder.folderName(marks.title, AudiobookConfig(folderNameMaxChars = config.fileNameMaxChars))
                .ifEmpty { context.getString(R.string.export_untitled) },
        )
        val export = QuoteExport(labels(context))
        val files = SharedDocuments(context.contentResolver, config.folder)
        val markdown = "$stem.md"
        val text = "$stem.txt"
        files.save(markdown, MARKDOWN_MIME) { it.write(export.markdown(marks).toByteArray()) }
            .flatMap { savedMarkdown ->
                files.save(text, TEXT_MIME) {
                    it.write(export.text(marks).toByteArray())
                }.map { listOf(savedMarkdown, it) }
            }
            .also {
                AudoraLog.i(
                    TAG,
                    "Notes export ${if (it is Outcome.Success) "saved" else "failed"}: " +
                        "${marks.highlights.size} highlights, ${marks.bookmarks.size} bookmarks, " +
                        "${marks.voiceNotes.size} voice notes",
                )
            }
    }

    private fun labels(context: Context) = QuoteLabels(
        highlights = context.getString(R.string.notes_highlights),
        bookmarks = context.getString(R.string.notes_bookmarks),
        voiceNotes = context.getString(R.string.notes_voice_notes),
        page = context.getString(R.string.notes_page),
        note = context.getString(R.string.notes_note),
    )
}
