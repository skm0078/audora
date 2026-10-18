// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/voice/CharacterCasts.kt
// Purpose: One kept cast per book for the whole process, so playback and the export never save over each other (63).

package io.github.skm0078.audora.playback.voice

import android.content.Context
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.VoiceId

/**
 * Every [CharacterCast] in the process (playback's and the audiobook export's) reads and changes a book's characters
 * through the one [BookCast] kept here for it. Before, each held its own copy of the file and saved it whole, so a
 * character first cast during an export was lost at playback's next save. The app runs in one process (no service
 * declares `android:process`), so a process-wide registry is enough; the file is still replaced atomically
 * ([CharacterFileStore]). A book's cast is a few dozen short lines, so the registry keeps every book opened since
 * the process started.
 */
class CharacterCasts(private val files: CharacterFiles?) {
    private val books = HashMap<BookId, BookCast>()

    /** [book]'s cast, loaded from its file on first use. */
    fun of(book: BookId): BookCast = synchronized(books) { books.getOrPut(book) { BookCast(book, files) } }

    companion object {
        @Volatile
        private var shared: CharacterCasts? = null

        /** The process's registry over filesDir/characters. */
        fun forContext(context: Context): CharacterCasts = shared ?: synchronized(this) {
            shared ?: CharacterCasts(CharacterFileStore.forContext(context)).also { shared = it }
        }
    }
}

/**
 * One book's characters: character key to voice. [edit] runs under the cast's lock and saves when the map changed,
 * so edits and saves from playback and the export are serialised and none is lost.
 */
class BookCast internal constructor(private val book: BookId, private val files: CharacterFiles?) {
    private val lock = Any()
    private var assigned: MutableMap<String, VoiceId>? = null

    fun <T> edit(block: (MutableMap<String, VoiceId>) -> T): T = synchronized(lock) {
        val map = assigned ?: load().also { assigned = it }
        val before = HashMap(map)
        block(map).also { if (map != before) files?.save(book, map.mapValues { it.value.name }) }
    }

    private fun load(): MutableMap<String, VoiceId> =
        files?.load(book).orEmpty().mapValuesTo(HashMap()) { VoiceId(EngineId.SHERPA, it.value) }
}
