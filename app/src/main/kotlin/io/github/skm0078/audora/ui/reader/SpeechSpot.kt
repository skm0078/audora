// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/SpeechSpot.kt
// Purpose: The Voice and speed sheet's finer groups, opened in place one at a time instead of as sheets over the
//          sheet (ADR 2026-10-29-voice-and-speed-one-level): each header says what is set in it.

package io.github.skm0078.audora.ui.reader

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.AfterSentence
import io.github.skm0078.audora.core.model.FocusSoundOptions
import io.github.skm0078.audora.core.model.ReadingModes
import io.github.skm0078.audora.core.model.VoiceShaping
import io.github.skm0078.audora.ui.theme.InPlaceGroup
import io.github.skm0078.audora.ui.theme.OptionGroup
import io.github.skm0078.audora.ui.theme.OptionSubtitle
import io.github.skm0078.audora.ui.theme.OptionTitle
import io.github.skm0078.audora.ui.theme.optionRowColors

/**
 * Where the Voice and speed sheet opens: its top ([TOP]), the voice list, or one of the finer groups, open. The
 * player's shortcuts open it at [VOICE], [READING] and [FOCUS]; [icon] marks it where search offers it.
 */
enum class SpeechSpot(
    @StringRes val title: Int,
    val expands: Boolean,
    @DrawableRes val icon: Int = R.drawable.ic_tune,
) {
    TOP(R.string.speech_sheet_title, expands = false),
    VOICE(R.string.speech_voices, expands = false, R.drawable.ic_mic),
    READING(R.string.reading_modes_title, expands = true, R.drawable.ic_article),
    SOUND(R.string.speech_sound, expands = true),
    CAST(R.string.speech_cast, expands = true),
    STUDY(R.string.study_title, expands = true),
    FOCUS(R.string.focus_title, expands = true, R.drawable.ic_headphones),
    ;

    companion object {
        /** The groups that open in place, in the sheet's order. */
        val groups: List<SpeechSpot> = entries.filter { it.expands }
    }
}

/**
 * A place in the sheet asked for from outside the player (a search result, Settings), opened once: [onTaken] clears
 * the request so a rotation or a return to the player does not open the sheet again.
 */
class SpeechRequest(val spot: SpeechSpot?, val onTaken: () -> Unit) {
    companion object {
        val NONE = SpeechRequest(null) {}
    }
}

/**
 * A group's header row: its name, what is set in it ([summary]), and an arrow that turns when it opens. TalkBack hears
 * "expanded" or "collapsed". [content] is composed only while open; opening it scrolls it into view
 * ([InPlaceGroup]).
 */
@Composable
internal fun SpeechGroup(
    spot: SpeechSpot,
    summary: String,
    open: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val title = stringResource(spot.title)
    val state = stringResource(if (open) R.string.more_options_expanded else R.string.more_options_collapsed)
    InPlaceGroup(
        open,
        onOpenChange = { onToggle() },
        header = { toggle -> SpeechGroupHeader(title, summary, open, state, toggle) },
        modifier = modifier,
    ) { content() }
}

@Composable
private fun SpeechGroupHeader(title: String, summary: String, open: Boolean, state: String, onToggle: () -> Unit) {
    OptionGroup(title = null) {
        ListItem(
            headlineContent = { OptionTitle(title, enabled = true) },
            supportingContent = { OptionSubtitle(summary, isError = false) },
            trailingContent = {
                Icon(
                    painterResource(R.drawable.ic_expand_more),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(if (open) HALF_TURN else 0f),
                )
            },
            colors = optionRowColors(),
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = state },
        )
    }
}

/** The Sound group's switches that are on, by name; none is "All off". */
internal fun soundSummary(shaping: VoiceShaping, clausePauses: Boolean): List<Int> = listOfNotNull(
    R.string.speech_clause_pauses.takeIf { clausePauses },
    R.string.speech_even_loudness.takeIf { shaping.evenLoudness },
    R.string.speech_trim_pauses.takeIf { shaping.trimPauses },
    R.string.speech_smart_speed.takeIf { shaping.smartSpeed },
)

/** The roles that have a voice of their own. */
internal fun castSummary(shaping: VoiceShaping): List<Int> = listOfNotNull(
    R.string.speech_cast_dialogue.takeIf { shaping.dialogueVoices.isNotEmpty() },
    R.string.speech_cast_headings.takeIf { shaping.headingVoices.isNotEmpty() },
    R.string.speech_cast_characters.takeIf { shaping.characterVoices },
)

/** Study's header: the repeats and what happens after each sentence, when either differs from plain reading. */
@Composable
internal fun studySummary(modes: ReadingModes): String = listOfNotNull(
    modes.sentenceRepeats.takeIf { it > 1 }?.let { stringResource(R.string.study_repeats_times, it) },
    when (modes.afterSentence) {
        AfterSentence.CONTINUE -> null
        AfterSentence.GAP -> stringResource(R.string.study_after_gap)
        AfterSentence.STOP -> stringResource(R.string.study_after_stop)
    },
).ifEmpty { listOf(stringResource(R.string.speech_group_all_off)) }.joinToString(SUMMARY_GAP)

/** Names joined, or "All off" for none. */
@Composable
internal fun namesOrAllOff(names: List<Int>): String =
    names.map { stringResource(it) }.ifEmpty { listOf(stringResource(R.string.speech_group_all_off)) }
        .joinToString(SUMMARY_GAP)

/** Focus sound's header: the sound playing under the voice, or Off. */
@Composable
internal fun focusSummary(focus: FocusSoundOptions): String = stringResource(focusSoundLabel(focus.sound))

private const val HALF_TURN = 180f
private const val SUMMARY_GAP = " · "
