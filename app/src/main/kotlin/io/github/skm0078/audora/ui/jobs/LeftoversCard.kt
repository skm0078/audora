// File: app/src/main/kotlin/io/github/skm0078/audora/ui/jobs/LeftoversCard.kt
// Purpose: The Jobs screen's Unfinished work card: what stopped jobs left, how long it is kept, what the last
//          clean-up deleted, and Clean up now (ADR 2026-10-29-jobs-card-and-leftovers).

package io.github.skm0078.audora.ui.jobs

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.files.LeftoverKind
import io.github.skm0078.audora.core.files.LeftoverTally
import io.github.skm0078.audora.playback.jobs.LeftoverState

/** Shown once there is something to say: leftovers waiting, or a clean-up that deleted something. */
internal val LeftoverState.shown: Boolean get() = !waiting.isEmpty || lastCleanedAtMs != null

/**
 * The rule in one sentence, so nothing is deleted by surprise; the last clean-up's line is announced when it changes
 * (Clean up now's result).
 */
@Composable
internal fun LeftoversCard(state: LeftoverState, onCleanUp: () -> Unit) {
    val context = LocalContext.current
    val size = Formatter.formatShortFileSize(context, state.waiting.bytes)
    val waiting = !state.waiting.isEmpty
    JobCard(
        icon = R.drawable.ic_delete,
        tone = JobTone.STOPPED,
        title = if (waiting) {
            stringResource(R.string.jobs_leftovers_waiting, size)
        } else {
            stringResource(R.string.jobs_leftovers_nothing)
        },
        status = null,
        detail = pluralStringResource(R.plurals.jobs_leftovers_rule, state.keepDays, state.keepDays),
        bar = JobBar.None,
        notes = listOfNotNull(lastCleanedLine(state)),
        announce = true,
    ) {
        if (waiting) {
            JobConfirmedButton(
                label = stringResource(R.string.jobs_leftovers_clean),
                question = stringResource(R.string.jobs_leftovers_clean_question, size),
                onConfirm = onCleanUp,
            )
        }
    }
}

/** "Last cleaned 29 Oct: 1.2 GB from 3 downloads, 1 audiobook."; null before the first clean-up. */
@Composable
private fun lastCleanedLine(state: LeftoverState): String? {
    val at = state.lastCleanedAtMs ?: return null
    val context = LocalContext.current
    val day = DateUtils.formatDateTime(context, at, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
    return stringResource(
        R.string.jobs_leftovers_last,
        day,
        Formatter.formatShortFileSize(context, state.lastCleaned.bytes),
        whatWent(state.lastCleaned),
    )
}

@Composable
private fun whatWent(tally: LeftoverTally): String {
    val parts = LeftoverKind.entries.mapNotNull { kind ->
        tally.count(kind).takeIf { it > 0 }?.let { pluralStringResource(pluralOf(kind), it, it) }
    }
    return parts.joinToString(stringResource(R.string.jobs_leftovers_join))
}

private fun pluralOf(kind: LeftoverKind): Int = when (kind) {
    LeftoverKind.DOWNLOAD -> R.plurals.jobs_leftovers_downloads
    LeftoverKind.AUDIOBOOK -> R.plurals.jobs_leftovers_audiobooks
    LeftoverKind.TALKING_BOOK -> R.plurals.jobs_leftovers_talking_books
}
