// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/QuoteExport.kt
// Purpose: A book's highlights, bookmarks and voice notes as Markdown or plain text, to share or keep (plan item 30).

package io.github.skm0078.audora.playback.export

import io.github.skm0078.audora.core.notes.Bookmark
import io.github.skm0078.audora.core.notes.Highlight
import io.github.skm0078.audora.core.notes.VoiceNote

/** Everything the listener marked in one book, each list in reading order. */
data class BookMarks(
    val title: String,
    val highlights: List<Highlight>,
    val bookmarks: List<Bookmark>,
    val voiceNotes: List<VoiceNote>,
) {
    val isEmpty: Boolean get() = highlights.isEmpty() && bookmarks.isEmpty() && voiceNotes.isEmpty()
}

/** The section names and phrases an export uses, in the app's language. */
data class QuoteLabels(
    val highlights: String = "Highlights",
    val bookmarks: String = "Bookmarks",
    val voiceNotes: String = "Voice notes",
    val page: String = "Page %d",
    val note: String = "Note",
)

/**
 * Pure. Pages are shown one-based, as the reader shows them. Markdown quotes every line of a quote, so a quote with
 * line breaks stays one block; characters Markdown would read as formatting in notes are left alone (a listener's
 * note is shown as they wrote it).
 */
class QuoteExport(private val labels: QuoteLabels = QuoteLabels()) {
    fun markdown(marks: BookMarks): String = buildString {
        append("# ").append(marks.title).append("\n\n")
        if (marks.highlights.isNotEmpty()) {
            append("## ").append(labels.highlights).append("\n\n")
            marks.highlights.forEach { highlight ->
                highlight.quote.lines().forEach { append("> ").append(it).append('\n') }
                append(">\n> — ").append(page(highlight.anchor.page)).append("\n\n")
                if (highlight.note.isNotBlank()) {
                    append("**").append(labels.note).append(":** ").append(highlight.note).append("\n\n")
                }
            }
        }
        list(marks, bullet = "- ", heading = "## ")
    }.trimEnd() + "\n"

    fun text(marks: BookMarks): String = buildString {
        append(marks.title).append('\n').append(RULE.repeat(marks.title.length.coerceAtLeast(1))).append("\n\n")
        if (marks.highlights.isNotEmpty()) {
            append(labels.highlights).append("\n\n")
            marks.highlights.forEach { highlight ->
                append('“').append(highlight.quote).append("” (").append(page(highlight.anchor.page)).append(")\n")
                if (highlight.note.isNotBlank()) append(labels.note).append(": ").append(highlight.note).append('\n')
                append('\n')
            }
        }
        list(marks, bullet = "• ", heading = "")
    }.trimEnd() + "\n"

    private fun StringBuilder.list(marks: BookMarks, bullet: String, heading: String) {
        if (marks.bookmarks.isNotEmpty()) {
            append(heading).append(labels.bookmarks).append("\n\n")
            marks.bookmarks.forEach { bookmark ->
                append(bullet).append(page(bookmark.anchor.page))
                if (bookmark.note.isNotBlank()) append(": ").append(bookmark.note)
                append('\n')
            }
            append('\n')
        }
        if (marks.voiceNotes.isNotEmpty()) {
            append(heading).append(labels.voiceNotes).append("\n\n")
            marks.voiceNotes.forEach { note ->
                append(
                    bullet,
                ).append(page(note.anchor.page)).append(": ").append(duration(note.durationMs)).append('\n')
            }
            append('\n')
        }
    }

    private fun page(index: Int): String = labels.page.format(index + 1)

    private fun duration(ms: Long): String {
        val seconds = ms / MS_PER_SECOND
        return "%d:%02d".format(seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
    }

    private companion object {
        const val RULE = "="
        const val MS_PER_SECOND = 1_000L
        const val SECONDS_PER_MINUTE = 60L
    }
}
