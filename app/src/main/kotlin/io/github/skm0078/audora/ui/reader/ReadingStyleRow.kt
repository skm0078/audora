// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReadingStyleRow.kt
// Purpose: The speech sheet's "Read to suit the book" switch and what the open book was told to be (ADR-056).

package io.github.skm0078.audora.ui.reader

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.ContentIntent
import io.github.skm0078.audora.ui.theme.SwitchOption

/**
 * On, the book's intent sets its pace and a story's lines get their feeling; the line under it says what the book
 * reads as, so the listener knows why a poem sounds slower than a news report. [intent] is null until the first pages
 * were read (or with no book in the player), which the line says too. The full story waits behind its info button.
 */
@Composable
fun ReadingStyleRow(on: Boolean, intent: ContentIntent?, bookInPlayer: Boolean, onChange: (Boolean) -> Unit) {
    val now = when {
        !on || !bookInPlayer -> null
        intent == null -> stringResource(R.string.reading_style_telling)
        else -> stringResource(R.string.reading_style_as, stringResource(intentLabel(intent)))
    }
    SwitchOption(
        stringResource(R.string.reading_style_title),
        on,
        onChange,
        subtitle = now,
        help = stringResource(R.string.reading_style_detail),
    )
}

@StringRes
internal fun intentLabel(intent: ContentIntent): Int = when (intent) {
    ContentIntent.STORY -> R.string.reading_style_story
    ContentIntent.CHILDREN -> R.string.reading_style_children
    ContentIntent.POETRY -> R.string.reading_style_poetry
    ContentIntent.SCRIPTURE -> R.string.reading_style_scripture
    ContentIntent.NEWS -> R.string.reading_style_news
    ContentIntent.REPORT -> R.string.reading_style_report
    ContentIntent.STUDY -> R.string.reading_style_study
    ContentIntent.GENERAL -> R.string.reading_style_general
}
