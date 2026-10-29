// File: app/src/main/kotlin/io/github/skm0078/audora/ui/made/MadePlayerSheets.kt
// Purpose: The saved-audio player's Speed sheet and Chapters sheet (ADR 2026-10-28-made-tab), built from the book
//          player's own speed control and chapter list, so the same settings sit in the same place in both.

package io.github.skm0078.audora.ui.made

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.library.OutputSet
import io.github.skm0078.audora.pdf.outline.ChapterEntry
import io.github.skm0078.audora.ui.reader.ChapterList
import io.github.skm0078.audora.ui.reader.SleepTimerSetting
import io.github.skm0078.audora.ui.reader.SpeedBlock
import io.github.skm0078.audora.ui.reader.sleepTimerChoices
import io.github.skm0078.audora.ui.reader.speedLabel
import io.github.skm0078.audora.ui.theme.AppSheet
import io.github.skm0078.audora.ui.theme.Dimens
import io.github.skm0078.audora.ui.theme.OptionBlock
import io.github.skm0078.audora.ui.theme.OptionGroup
import io.github.skm0078.audora.ui.theme.OptionSheet

/** "Speed 1.25×", as on the book player; a tap opens the speed sheet. */
@Composable
internal fun MadeSpeedButton(speed: Float, config: ReaderConfig, enabled: Boolean, onSpeed: (Float) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = true }, enabled = enabled) {
        Text(stringResource(R.string.reader_speed, speedLabel(speed)))
    }
    if (open) MadeSpeedSheet(speed, config, onSpeed) { open = false }
}

/**
 * The book player's speed control, as it is there; then why there is no voice to pick: a saved file keeps the voice
 * it was made with, and the way to another is to make it again.
 */
@Composable
internal fun MadeSpeedSheet(speed: Float, config: ReaderConfig, onSpeed: (Float) -> Unit, onDismiss: () -> Unit) {
    // The book player's sheet name too: the same settings, found by the same name.
    OptionSheet(stringResource(R.string.speech_sheet_title), onDismiss) {
        OptionGroup(title = null) { SpeedBlock(speed, config, onSpeed) }
        OptionGroup(title = null) {
            OptionBlock(
                stringResource(R.string.made_voice_title),
                subtitle = stringResource(R.string.made_voice_body),
            ) {}
        }
    }
}

/** The timer's choices here: the book player's, less End of page, and the chapter ones only for many files. */
internal fun madeSleepChoices(chapters: Int, config: ReaderConfig): List<SleepTimerSetting> =
    sleepTimerChoices(hasChapters = chapters > 1, config).filterNot { it == SleepTimerSetting.EndOfPage }

/** Each file of the set is a chapter; the list is the book player's, opened at the one playing. */
@Composable
internal fun MadeChaptersButton(set: OutputSet, state: MadePlayerState, controls: MadeAudioController?) {
    if (set.files.size < 2) return
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(painterResource(R.drawable.ic_toc), stringResource(R.string.chapters_title))
    }
    if (!open) return
    AppSheet(onDismissRequest = { open = false }) {
        Text(
            stringResource(R.string.chapters_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = Dimens.SpaceXl, vertical = Dimens.SpaceS).semantics { heading() },
        )
        val chapters = set.files.mapIndexed { index, file -> ChapterEntry(file.name, page = index, depth = 0) }
        ChapterList(chapters, current = state.index, onChapter = { chapter ->
            open = false
            controls?.chapter(chapter.page)
        })
    }
}
