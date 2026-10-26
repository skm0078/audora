// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/brain/BrainNotesStore.kt
// Purpose: Keeps the story brain's notes per book on disk (ADR story-brain), so a page is read by the model once, not
//          on every listen.

package io.github.skm0078.audora.playback.brain

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Feeling
import io.github.skm0078.audora.speech.sherpa.SpeakerGender
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** One stored call on a spoken sentence: [textHash] is the sentence's, so a page read differently is not misapplied. */
data class StoredNote(
    val page: Int,
    val index: Int,
    val textHash: Int,
    val speaker: String,
    val gender: SpeakerGender,
    val feeling: Feeling?,
)

/** What is known of a book: its notes and the pages already read (with or without speech on them). */
data class BookNotes(val notes: List<StoredNote>, val pages: Set<Int>)

/**
 * One tab-separated file per book under [dir], named by a hash of the book's id (never its title). The first line
 * names the model and prompt version: notes from another model or prompt are dropped, so a better brain reads the
 * book again. Lines are only ever appended; a line that does not read (a kill mid-write) is skipped. The notes hold
 * character names from the book, so the folder is the app's private storage, like the book's text.
 */
class BrainNotesStore(private val dir: File, private val version: String) {
    fun load(bookKey: String): BookNotes {
        val file = fileOf(bookKey)
        val lines = try {
            if (file.isFile) file.readLines(Charsets.UTF_8) else emptyList()
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Story brain notes unreadable; starting over", e)
            emptyList()
        }
        if (lines.firstOrNull() != header()) {
            if (lines.isNotEmpty()) file.delete()
            return BookNotes(emptyList(), emptySet())
        }
        val pages = HashSet<Int>()
        val notes = lines.drop(1).mapNotNull { line ->
            val cells = line.split('\t')
            when {
                cells.size == PAGE_CELLS && cells[0] == PAGE -> cells[1].toIntOrNull()?.let(pages::add).let { null }
                cells.size == NOTE_CELLS -> note(cells)
                else -> null
            }
        }
        return BookNotes(notes, pages)
    }

    /** Appends one read page: its notes, then the page itself, so a page counts as read only with its notes. */
    fun append(bookKey: String, page: Int, notes: List<StoredNote>) {
        val file = fileOf(bookKey)
        try {
            dir.mkdirs()
            if (!file.isFile || file.length() == 0L) file.writeText(header() + "\n", Charsets.UTF_8)
            val text = buildString {
                notes.forEach { n ->
                    val feeling = n.feeling?.name ?: NONE
                    append("${n.page}\t${n.index}\t${n.textHash}\t${clean(n.speaker)}\t${n.gender.name}\t$feeling\n")
                }
                append("$PAGE\t$page\n")
            }
            file.appendText(text, Charsets.UTF_8)
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Story brain notes not saved for page $page", e)
        }
    }

    /** Removes every book's notes (the model was deleted, or the user cleared them). */
    fun clear() {
        dir.deleteRecursively()
    }

    /** One note line: page, index, text hash, speaker, gender, feeling; null when any cell does not read. */
    private fun note(cells: List<String>): StoredNote? {
        val numbers = cells.take(NUMBER_CELLS).mapNotNull { it.toIntOrNull() }
        val (speaker, genderName, feelingName) = cells.drop(NUMBER_CELLS)
        val feeling = Feeling.entries.firstOrNull { it.name == feelingName }
        val complete = numbers.size == NUMBER_CELLS && (feelingName == NONE || feeling != null)
        return SpeakerGender.entries.firstOrNull { it.name == genderName }?.takeIf { complete }?.let { gender ->
            StoredNote(numbers[0], numbers[1], numbers[2], speaker, gender, feeling)
        }
    }

    private fun header(): String = "$MAGIC\t$version"

    private fun fileOf(bookKey: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(bookKey.toByteArray(Charsets.UTF_8))
        return File(dir, digest.take(NAME_BYTES).joinToString("") { "%02x".format(it) } + ".tsv")
    }

    private fun clean(name: String): String = name.replace(CONTROL, " ").trim()

    private companion object {
        const val TAG = "Playback.BrainNotes"
        const val MAGIC = "#audora-story-brain"
        const val PAGE = "P"
        const val NONE = "-"
        const val PAGE_CELLS = 2
        const val NOTE_CELLS = 6
        const val NUMBER_CELLS = 3
        const val NAME_BYTES = 12
        val CONTROL = Regex("""[\t\r\n]""")
    }
}
