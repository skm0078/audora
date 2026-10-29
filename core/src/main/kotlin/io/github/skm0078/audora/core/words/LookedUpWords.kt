// File: core/src/main/kotlin/io/github/skm0078/audora/core/words/LookedUpWords.kt
// Purpose: "Words you looked up" (personal touch 16): each word a listener looked up in a book, with its short
//          meaning and the sentence it came from, kept in one small file on the phone for the book's list and quiz.

package io.github.skm0078.audora.core.words

import io.github.skm0078.audora.core.dictionary.WordKey
import io.github.skm0078.audora.core.files.TextFiles
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Lang
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One word looked up in [bookId]: its [meaning] (the first sense), and the [sentence] on [page] it was tapped in. */
data class LookedUpWord(
    val bookId: BookId,
    val word: String,
    val meaning: String,
    val page: Int,
    val sentence: String,
    val lang: Lang,
    val atMillis: Long,
) {
    /** The same word however it was cased or spelled in Unicode: a book keeps it once. */
    val key: String get() = WordKey.of(word, Int.MAX_VALUE) ?: word

    /** A copy that fits the file: one line, the meaning and sentence cut to a glance. */
    fun trimmed(): LookedUpWord = copy(
        word = oneLine(word, MAX_WORD_CHARS),
        meaning = oneLine(meaning, MAX_MEANING_CHARS),
        sentence = oneLine(sentence, MAX_SENTENCE_CHARS),
    )

    private fun oneLine(text: String, max: Int): String {
        val flat = text.replace(LINE_BREAKS, " ").trim()
        return if (flat.length <= max) flat else flat.take(max - 1).trimEnd() + "…"
    }

    private companion object {
        const val MAX_WORD_CHARS = 64
        const val MAX_MEANING_CHARS = 160
        const val MAX_SENTENCE_CHARS = 300
        val LINE_BREAKS = Regex("""[\t\r\n]+""")
    }
}

/**
 * One line per word, its fields tab-separated with the language by name; a line that does not parse is skipped, a
 * file in another format (a newer app's backup) reads as no words and is set aside first, never overwritten. The
 * words are the book's text: logs say counts only (AGENTS.md).
 */
class LookedUpWordStore(private val file: File, private val moveAside: (File) -> Boolean = { TextFiles.setAside(it) }) {
    fun load(): List<LookedUpWord> {
        val lines = readLines()
        val foreign = lines != null && lines.isNotEmpty() && lines.first() != HEADER
        if (foreign) {
            AudoraLog.w(TAG, "Looked-up words from another format set aside")
            moveAside(file)
        }
        return if (lines == null || foreign) emptyList() else lines.drop(1).mapNotNull(::wordOf)
    }

    private fun readLines(): List<String>? = try {
        if (file.isFile) file.readLines() else emptyList()
    } catch (e: IOException) {
        AudoraLog.w(TAG, "Looked-up words unreadable, set aside: ${e.javaClass.simpleName}")
        moveAside(file)
        null
    }

    /** Replaces the file with [words]; none deletes it. False (logged) if it could not be written. */
    fun save(words: List<LookedUpWord>): Boolean = try {
        if (isForeign() && !moveAside(file)) throw IOException("unreadable looked-up words in the way")
        if (words.isEmpty()) {
            if (file.exists() && !file.delete()) throw IOException("not deleted")
        } else {
            val lines = words.map { it.trimmed() }.map { w ->
                listOf(w.bookId.value, w.word, w.meaning, w.page, w.sentence, w.lang.name, w.atMillis)
                    .joinToString(SEPARATOR)
            }
            TextFiles.writeDurably(file, (listOf(HEADER) + lines).joinToString("\n", postfix = "\n"))
        }
        true
    } catch (e: IOException) {
        AudoraLog.w(TAG, "Looked-up words not saved, ${words.size} words: ${e.javaClass.simpleName}")
        false
    }

    private fun isForeign(): Boolean = file.isFile &&
        file.bufferedReader().use { it.readLine() }.let { it != null && it != HEADER }

    /** A line's fields in file order; null unless every one parses (a page below 0 does not). */
    private fun wordOf(line: String): LookedUpWord? {
        val parts = line.split(SEPARATOR).takeIf { it.size == FIELDS && it[0].isNotBlank() && it[1].isNotBlank() }
            ?: return null
        val lang = Lang.entries.firstOrNull { it.name == parts[LANG_FIELD] }
        val at = parts[AT_FIELD].toLongOrNull()
        val page = parts[PAGE_FIELD].toIntOrNull()?.takeIf { it >= 0 }
        return if (lang == null || at == null || page == null) {
            null
        } else {
            LookedUpWord(BookId(parts[0]), parts[1], parts[2], page, parts[SENTENCE_FIELD], lang, at)
        }
    }

    companion object {
        /** The file under `filesDir`; the backup lists it by this name. */
        const val FILE_NAME = "looked-up-words.txt"
        private const val TAG = "Words.LookedUp"
        private const val HEADER = "audora-looked-up-words 1"
        private const val SEPARATOR = "\t"
        private const val FIELDS = 7
        private const val PAGE_FIELD = 3
        private const val SENTENCE_FIELD = 4
        private const val LANG_FIELD = 5
        private const val AT_FIELD = 6
    }
}

/**
 * Every book's looked-up words, newest first, loaded once and kept in [words]; each change is written through
 * [store] at once. A word looked up again in the same book moves to the top with its new sentence. Each book keeps
 * its newest [maxPerBook]. Call from a background thread: [load], [add] and [forget] touch the file.
 */
class LookedUpWords(private val store: LookedUpWordStore, private val maxPerBook: Int = MAX_PER_BOOK) {
    private val state = MutableStateFlow<List<LookedUpWord>>(emptyList())
    val words: StateFlow<List<LookedUpWord>> = state.asStateFlow()

    @Volatile
    private var loaded = false

    @Synchronized
    fun load() {
        if (loaded) return
        state.value = store.load().sortedByDescending { it.atMillis }
        loaded = true
    }

    /** Keeps [word] at the top of its book; false (logged by the store) if it was not saved. */
    @Synchronized
    fun add(word: LookedUpWord): Boolean {
        load()
        val kept = word.trimmed()
        val others = state.value.filterNot { it.bookId == kept.bookId && it.key == kept.key }
        val sameBook = others.count { it.bookId == kept.bookId }
        val dropped = if (sameBook < maxPerBook) {
            others
        } else {
            // The book's oldest goes, never another book's.
            val oldest = others.last { it.bookId == kept.bookId }
            others - oldest
        }
        return commit(listOf(kept) + dropped)
    }

    /** Forgets every word of [bookId] (the book was removed); false if that could not be saved. */
    @Synchronized
    fun forget(bookId: BookId): Boolean {
        load()
        val kept = state.value.filterNot { it.bookId == bookId }
        return kept.size == state.value.size || commit(kept)
    }

    private fun commit(next: List<LookedUpWord>): Boolean =
        store.save(next).also { saved -> if (saved) state.value = next }

    private companion object {
        /** A long book's worth: the file stays a few hundred kilobytes at most across a large library. */
        const val MAX_PER_BOOK = 200
    }
}
