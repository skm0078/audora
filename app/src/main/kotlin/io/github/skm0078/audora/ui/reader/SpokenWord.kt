// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/SpokenWord.kt
// Purpose: Which word of the spoken sentence is being said (plan item 14, version one): how far the voice is through
//          the sentence's speech, spread evenly over its letters.

package io.github.skm0078.audora.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import io.github.skm0078.audora.playback.contract.SentenceTiming

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

/**
 * How far the voice is through the speech of the sentence at [position], from the player's [positionMs] in its clip;
 * null when [timing] belongs to another sentence (or none is known yet) and during a cue before the voice.
 */
internal fun speechFraction(timing: SentenceTiming?, position: ReadingPosition?, positionMs: Long?): Float? {
    val ms = positionMs ?: return null
    val sameSentence = timing?.takeIf {
        position != null && it.page == position.page &&
            it.charStart == position.charStart
    }
    return sameSentence?.takeIf { ms >= it.leadMs && it.speechMs > 0 }?.speechFraction(ms)
}

/**
 * The spoken sentence's word being said, polled once a frame while the voice plays (the controller runs the clock,
 * so a poll is cheap); null when [enabled] is off, nothing plays on the page, or the timing is not known yet.
 */
@Composable
internal fun rememberSpokenWord(state: ReaderState, port: PlaybackPort, enabled: Boolean): IntRange? {
    val playback = state.playback
    val unit = state.spokenUnitIndex?.let { (state.content as? PageContent.Ready)?.units?.getOrNull(it) }
    val active = enabled && unit != null && playback.wantsToPlay && !playback.buffering
    var word by remember { mutableStateOf<IntRange?>(null) }
    LaunchedEffect(active, unit, playback.sentenceTiming, playback.position) {
        word = null
        if (!active || unit == null) return@LaunchedEffect
        while (true) {
            withFrameMillis {
                val fraction = speechFraction(playback.sentenceTiming, playback.position, port.positionInSentenceMs())
                word = spokenWord(unit.text, fraction)
            }
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
