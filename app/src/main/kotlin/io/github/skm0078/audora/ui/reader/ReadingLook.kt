// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReadingLook.kt
// Purpose: How the text view looks while reading (Focus view, bold word starts, follow-along, highlight, page
//          colours, font, size, spacing and reflow; plan items 77, 13, 14, 18, 15), kept across launches.

package io.github.skm0078.audora.ui.reader

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
 * @property followAlong keeps the spoken sentence in the middle of the text view (plan item 13); off, the text stays
 *   where the listener scrolled it.
 * @property highlight how the spoken sentence is marked.
 * @property wordHighlight also marks the word being spoken (plan item 14), where the sentence's timing is known.
 * @property tone the page's colours (plan item 18): as printed, sepia or dark, in the page view and the text view.
 * @property font the text view's typeface (plan item 15).
 * @property textScale the text view's type size, times the phone's own font size, within [TEXT_SCALES].
 * @property spacing how far apart the text view's lines are.
 * @property reflow runs a paragraph's sentences together as running text; off, each sentence is its own row (easier
 *   to step through one sentence at a time with TalkBack).
 */
data class ReadingLook(
    val focus: Boolean = false,
    val boldWordStarts: Boolean = false,
    val followAlong: Boolean = true,
    val highlight: HighlightStyle = HighlightStyle.FILL,
    val wordHighlight: Boolean = true,
    val tone: PageTone = PageTone.ORIGINAL,
    val font: ReadingFont = ReadingFont.SERIF,
    val textScale: Float = DEFAULT_TEXT_SCALE,
    val spacing: LineSpacing = LineSpacing.NORMAL,
    val reflow: Boolean = true,
) {
    companion object {
        const val DEFAULT_TEXT_SCALE = 1f

        /** Smallest to largest text view size, on top of the phone's font size; 200 % phones stay readable. */
        val TEXT_SCALES = 0.85f..1.6f
    }
}

/** How the spoken sentence stands out: a tinted band, an underline, or bold type alone. */
enum class HighlightStyle { FILL, UNDERLINE, BOLD }

/**
 * The text view's typeface: the book-like serif, sans for screens, or one of two faces made for readers who confuse
 * letters (CF-5): Atkinson Hyperlegible, whose look-alike letters (I l 1, b d p q) are drawn apart, and OpenDyslexic,
 * whose weighted bottoms keep letters from flipping. Saved by name, so the order here can change.
 */
enum class ReadingFont { SERIF, SANS, ATKINSON_HYPERLEGIBLE, OPEN_DYSLEXIC }

/** Line spacing, as a factor on the reading style's own (taller than usual for Indic marks). */
enum class LineSpacing(val factor: Float) {
    COMPACT(COMPACT_SPACING),
    NORMAL(1f),
    RELAXED(RELAXED_SPACING),
}

private const val COMPACT_SPACING = 0.85f
private const val RELAXED_SPACING = 1.25f

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
        .map { prefs ->
            val defaults = ReadingLook()
            ReadingLook(
                focus = prefs[FOCUS] ?: defaults.focus,
                boldWordStarts = prefs[BOLD_STARTS] ?: defaults.boldWordStarts,
                followAlong = prefs[FOLLOW] ?: defaults.followAlong,
                highlight = HighlightStyle.entries.firstOrNull { it.name == prefs[HIGHLIGHT] } ?: defaults.highlight,
                wordHighlight = prefs[WORD_HIGHLIGHT] ?: defaults.wordHighlight,
                tone = PageTone.entries.firstOrNull { it.name == prefs[TONE] } ?: defaults.tone,
                font = ReadingFont.entries.firstOrNull { it.name == prefs[FONT] } ?: defaults.font,
                textScale = prefs[TEXT_SCALE]?.coerceIn(ReadingLook.TEXT_SCALES) ?: defaults.textScale,
                spacing = LineSpacing.entries.firstOrNull { it.name == prefs[SPACING] } ?: defaults.spacing,
                reflow = prefs[REFLOW] ?: defaults.reflow,
            )
        }

    suspend fun save(look: ReadingLook) {
        try {
            store.edit {
                it[FOCUS] = look.focus
                it[BOLD_STARTS] = look.boldWordStarts
                it[FOLLOW] = look.followAlong
                it[HIGHLIGHT] = look.highlight.name
                it[WORD_HIGHLIGHT] = look.wordHighlight
                it[TONE] = look.tone.name
                it[FONT] = look.font.name
                it[TEXT_SCALE] = look.textScale
                it[SPACING] = look.spacing.name
                it[REFLOW] = look.reflow
            }
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Could not save the reading look", e)
        }
    }

    private companion object {
        const val TAG = "Audora.ReadingLook"
        val FOCUS = booleanPreferencesKey("focus")
        val BOLD_STARTS = booleanPreferencesKey("bold_word_starts")
        val FOLLOW = booleanPreferencesKey("follow_along")
        val HIGHLIGHT = stringPreferencesKey("highlight")
        val WORD_HIGHLIGHT = booleanPreferencesKey("word_highlight")
        val TONE = stringPreferencesKey("page_tone")
        val FONT = stringPreferencesKey("font")
        val TEXT_SCALE = floatPreferencesKey("text_scale")
        val SPACING = stringPreferencesKey("line_spacing")
        val REFLOW = booleanPreferencesKey("reflow")
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
