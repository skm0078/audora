// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/PreviouslyOn.kt
// Purpose: "Previously, 9 days ago" (personal touch 3): a book opened after some days away shows the last two
//          sentences before its place, with "Hear them again" and "Go on", so the story comes back first.

package io.github.skm0078.audora.ui.reader

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.ReadingUnit

/** The recap: how many whole [daysAway], the sentences before the place, and where "Hear them again" reads [from]. */
data class PreviouslyOn(val daysAway: Int, val lines: List<String>, val from: ReadingPosition)

/**
 * The recap for a book left at [position] on a page of [units], saved at [savedAtMillis]; null when the listener
 * was away less than three days (or the clock went back), or the place is the page's first sentence. Each line is
 * cut to [maxWords] words. Only the screen shows them: they are never logged (AGENTS.md).
 */
fun previouslyOn(
    position: ReadingPosition,
    units: List<ReadingUnit>,
    savedAtMillis: Long,
    nowMillis: Long,
    maxWords: Int = MAX_WORDS_PER_LINE,
): PreviouslyOn? {
    val days = (nowMillis - savedAtMillis) / DAY_MILLIS
    // Zero-length units (a table's summary) have no words to recall.
    val before = units.filter { it.charStart < position.charStart && it.text.isNotBlank() }.takeLast(MAX_LINES)
    if (days < MIN_DAYS_AWAY || before.isEmpty()) return null
    return PreviouslyOn(
        daysAway = days.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        lines = before.map { line(it.text, maxWords) },
        from = ReadingPosition(position.bookId, position.page, before.first().charStart),
    )
}

private fun line(text: String, maxWords: Int): String {
    val words = text.trim().split(WHITESPACE)
    return if (words.size <= maxWords) words.joinToString(" ") else words.take(maxWords).joinToString(" ") + "…"
}

/**
 * The recap over the reader or the full player. Both buttons wait for the player ([ready]): until then a tap would
 * do nothing. Tapping outside closes it, and the place stays as it was.
 */
@Composable
fun PreviouslyOnCard(
    recap: PreviouslyOn?,
    ready: Boolean,
    onHearAgain: () -> Unit,
    onGoOn: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (recap == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                pluralStringResource(R.plurals.reader_previously_days, recap.daysAway, recap.daysAway),
                modifier = Modifier.semantics { heading() },
            )
        },
        text = { Text(stringResource(R.string.reader_previously_quote, recap.lines.joinToString(" "))) },
        confirmButton = {
            TextButton(onClick = onHearAgain, enabled = ready) {
                Text(stringResource(R.string.reader_previously_hear_again))
            }
        },
        dismissButton = {
            TextButton(onClick = onGoOn, enabled = ready) { Text(stringResource(R.string.reader_previously_go_on)) }
        },
    )
}

/** Away this long, the story may have faded; any sooner, "Picked up on page N" is enough. */
private const val MIN_DAYS_AWAY = 3
private const val MAX_LINES = 2

/** Two lines a glance can take in; a long sentence is cut, the voice still reads it whole. */
private const val MAX_WORDS_PER_LINE = 30
private const val DAY_MILLIS = 24L * 60 * 60 * 1000
private val WHITESPACE = Regex("""\s+""")
