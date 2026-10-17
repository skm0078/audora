// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReadingLook.kt
// Purpose: How the text view looks while reading (Focus view, bold word starts; plan item 77), kept across launches.

package io.github.skm0078.audora.ui.reader

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * @property focus dims every sentence but the one being read, in larger reading type, for ADHD and dyslexia.
 * @property boldWordStarts bolds the first half of each English word so the eye finds words faster. Hindi and
 *   Bengali words stay whole: bolding half a word there would split its letters apart.
 */
data class ReadingLook(val focus: Boolean = false, val boldWordStarts: Boolean = false)

private val Context.readingLookStore: DataStore<Preferences> by preferencesDataStore(name = "reading_look")

/** Its own file, so the reader's speed and view settings stay untouched. Failures are logged and never block. */
class ReadingLookPreferences(context: Context) {
    private val store = context.applicationContext.readingLookStore

    val look: Flow<ReadingLook> = store.data
        .catch { e ->
            if (e !is IOException) throw e
            AudoraLog.w(TAG, "Could not read the reading look; using the default", e)
            emit(emptyPreferences())
        }
        .map { prefs -> ReadingLook(focus = prefs[FOCUS] ?: false, boldWordStarts = prefs[BOLD_STARTS] ?: false) }

    suspend fun save(look: ReadingLook) {
        try {
            store.edit {
                it[FOCUS] = look.focus
                it[BOLD_STARTS] = look.boldWordStarts
            }
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Could not save the reading look", e)
        }
    }

    private companion object {
        const val TAG = "Audora.ReadingLook"
        val FOCUS = booleanPreferencesKey("focus")
        val BOLD_STARTS = booleanPreferencesKey("bold_word_starts")
    }
}

/**
 * The character ranges to bold for "bold word starts": the first half of each Latin-script word, rounded up, and one
 * letter of a word of three or fewer. Words in other scripts get no range (see [ReadingLook.boldWordStarts]).
 */
fun boldWordStartRanges(text: String): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    var index = 0
    while (index < text.length) {
        if (!Character.isLetter(text[index])) {
            index++
            continue
        }
        val start = index
        while (index < text.length && Character.isLetter(text[index])) index++
        val word = text.substring(start, index)
        if (word.all(::isLatinLetter)) ranges += start until start + boldLength(word.length)
    }
    return ranges
}

private fun isLatinLetter(char: Char): Boolean = Character.UnicodeScript.of(char.code) == Character.UnicodeScript.LATIN

private fun boldLength(wordLength: Int): Int = if (wordLength <= SHORT_WORD) 1 else (wordLength + 1) / 2

/** Words this short get only their first letter bolded. */
private const val SHORT_WORD = 3
