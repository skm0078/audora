// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/TimeLeftLine.kt
// Purpose: The reader's "About 2 h 10 min left at 1.25×" line under the page bar (plan item 24).

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.theme.Dimens

/** "About 2 h 10 min left at 1.25×, 12 min in this chapter" (plan item 24). */
@Composable
internal fun TimeLeftLine(timeLeft: TimeLeftEstimate, speed: Float) {
    val book = durationText(timeLeft.bookMs)
    val text = timeLeft.chapterMs?.let { chapter ->
        stringResource(R.string.reader_time_left_chapter, book, speedLabel(speed), durationText(chapter))
    } ?: stringResource(R.string.reader_time_left, book, speedLabel(speed))
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.SpaceXl),
    )
}

@Composable
internal fun durationText(ms: Long): String {
    val (hours, minutes) = hoursMinutes(ms)
    return when {
        hours > 0 -> stringResource(R.string.duration_hours_minutes, hours, minutes)
        minutes > 0 -> stringResource(R.string.duration_minutes, minutes)
        else -> stringResource(R.string.duration_under_minute)
    }
}
