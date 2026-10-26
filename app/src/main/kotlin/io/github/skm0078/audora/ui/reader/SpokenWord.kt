// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/SpokenWord.kt
// Purpose: Which word of the spoken sentence is being said (plan item 14): timed from the voice's audio when the
//          renderer read the word starts from it (CF-10), else spread evenly over its letters (version one).

package io.github.skm0078.audora.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.playback.contract.SentenceTiming
import kotlinx.coroutines.delay

/**
 * The characters of the word being said in [text] when the voice is [fraction] (0..1) of the way through the
 * sentence's speech; null when [fraction] is unknown or the text has no letters.
 *
 * Version one gives every non-space character the same share of the time, so a long word holds the highlight
 * longer; it drifts by about ±150 ms where the voice pauses at punctuation. Words are what spaces separate, which
 * holds for English, Hindi and Bengali alike and never splits a conjunct.
 */
internal fun spokenWord(text: String, fraction: Float?): IntRange? {
    val letters = text.count { !it.isWhitespace() }
    val share = fraction?.takeIf { letters > 0 && !it.isNaN() } ?: return null
    val index = nthLetterIndex(text, (share.coerceIn(0f, 1f) * letters).toInt().coerceAtMost(letters - 1))
    var start = index
    while (start > 0 && !text[start - 1].isWhitespace()) start--
    var end = index
    while (end < text.lastIndex && !text[end + 1].isWhitespace()) end++
    return start..end
}

/** The characters of [text]'s [index]th (0-based) whitespace-separated word; null past its last word. */
internal fun nthWord(text: String, index: Int): IntRange? = WORD.findAll(text).elementAtOrNull(index)?.range

/**
 * The word of [unit] being said at the player's [positionMs] in its clip: from the word starts the renderer read off
 * the voice (CF-10) when [timing] has them for this sentence's words, else [spokenWord] by letters. Null when the
 * timing belongs to another sentence or the cue still plays.
 */
internal fun spokenWordOf(timing: SentenceTiming?, unit: ReadingUnit?, positionMs: Long?): IntRange? {
    val fraction = speechFraction(timing, unit, positionMs)
    val text = unit?.text
    if (fraction == null || text == null || positionMs == null) return null
    val word = timing?.takeIf { it.wordStartsMs.size == WORD.findAll(text).count() }?.wordAt(positionMs)
    return if (word != null) nthWord(text, word) else spokenWord(text, fraction)
}

/**
 * How far the voice is through the speech of [unit], from the player's [positionMs] in its clip; null when [timing]
 * belongs to another sentence (or none is known yet) and during a cue before the voice. The unit's index is matched
 * too: a continued table's summary has no length (charEnd == charStart) and shares its first row's charStart.
 */
internal fun speechFraction(timing: SentenceTiming?, unit: ReadingUnit?, positionMs: Long?): Float? {
    val ms = positionMs ?: return null
    val sameSentence = timing?.takeIf {
        unit != null && it.page == unit.page && it.charStart == unit.charStart && it.unitIndex == unit.index
    }
    return sameSentence?.takeIf { ms >= it.leadMs && it.speechMs > 0 }?.speechFraction(ms)
}

/**
 * The spoken sentence's word being said, polled every [WORD_POLL_MS] while the voice plays (the controller runs the
 * clock, so a poll is cheap); null when [enabled] is off, nothing plays on the page, or the timing is not known yet.
 * Not once a frame (ADR-072): a 120-144 Hz screen woke the main thread 144 times a second for a word that changes
 * about four times a second, and the estimate itself drifts by ±150 ms.
 */
@Composable
internal fun rememberSpokenWord(state: ReaderState, port: PlaybackPort, enabled: Boolean): State<IntRange?> {
    val playback = state.playback
    val unit = state.spokenUnitIndex?.let { (state.content as? PageContent.Ready)?.units?.getOrNull(it) }
    val active = enabled && unit != null && playback.wantsToPlay && !playback.buffering
    // Returned as State, so only the composables that read it redraw on each word, not the whole screen.
    val word = remember { mutableStateOf<IntRange?>(null) }
    LaunchedEffect(active, unit, playback.sentenceTiming, playback.position) {
        word.value = null
        if (!active || unit == null) return@LaunchedEffect
        while (true) {
            word.value = spokenWordOf(playback.sentenceTiming, unit, port.positionInSentenceMs())
            delay(WORD_POLL_MS)
        }
    }
    return word
}

/** Index in [text] of its [n]th (0-based) non-space character; [n] is below the count of such characters. */
private fun nthLetterIndex(text: String, n: Int): Int {
    var seen = 0
    text.forEachIndexed { i, c ->
        if (!c.isWhitespace()) {
            if (seen == n) return i
            seen++
        }
    }
    error("text has only $seen non-space characters, asked for #$n")
}

/** A word, as [spokenWord] and the renderer's word starts count them: what [Char.isWhitespace] separates. */
private val WORD = Regex("[^\\p{javaWhitespace}\\p{Z}]+")

/** About 30 checks a second: well inside a word's ~250 ms, a fifth of a 144 Hz screen's frames. */
private const val WORD_POLL_MS = 33L
