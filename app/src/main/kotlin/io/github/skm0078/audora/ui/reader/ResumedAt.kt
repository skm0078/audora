// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ResumedAt.kt
// Purpose: "Picked up on page 42: “It was a bright cold day…”" when a book opens where it was left, so the listener
//          sees at once that their place was kept; and the reader's other one-line toasts.

package io.github.skm0078.audora.ui.reader

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.ReadingUnit

/** Where a book was picked up: its [page] (0-based) and the first words of its sentence, when the page has text. */
data class ResumedAt(val page: Int, val words: String?)

/**
 * The sentence [position] points into on its page ([units]), cut to its first [maxWords] words. Only the screen
 * shows them: they are never logged (AGENTS.md).
 */
fun resumedAt(position: ReadingPosition, units: List<ReadingUnit>, maxWords: Int = MAX_WORDS): ResumedAt {
    val unit = units.lastOrNull { it.charStart <= position.charStart } ?: units.firstOrNull()
    val words = unit?.text?.trim()?.split(WHITESPACE)?.filter { it.isNotEmpty() }?.take(maxWords)
    return ResumedAt(position.page, words?.takeIf { it.isNotEmpty() }?.joinToString(" "))
}

/** A short toast for [resumed], then [onShown]: shown over the reader and the full player alike. */
@Composable
fun ResumedNotice(resumed: ResumedAt?, onShown: () -> Unit) {
    if (resumed == null) return
    val context = LocalContext.current
    val page = resumed.page + 1
    val text = resumed.words?.let { stringResource(R.string.reader_resumed_at, page, it) }
        ?: stringResource(R.string.reader_resumed_page, page)
    LaunchedEffect(resumed) {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        onShown()
    }
}

/** The reader's toasts, once each: a paragraph skip that found nothing, and where the book was picked up. */
@Composable
fun ReaderToasts(state: ReaderState, model: ReaderModel) {
    ParagraphSkipNotice(state.skipRefused, model::skipRefusalShown)
    ResumedNotice(state.resumedAt, model::resumedShown)
}

/** Enough to recognise the sentence, short enough for one toast line. */
private const val MAX_WORDS = 7
private val WHITESPACE = Regex("""\s+""")
