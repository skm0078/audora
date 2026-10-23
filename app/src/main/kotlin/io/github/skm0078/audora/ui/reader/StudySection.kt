// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/StudySection.kt
// Purpose: The speech sheet's study controls (plan item 31): repeats, what follows each sentence, and A–B loops.

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.AfterSentence
import io.github.skm0078.audora.core.model.ReadingModes
import io.github.skm0078.audora.playback.contract.StudyAction
import io.github.skm0078.audora.ui.theme.ChoiceOption
import io.github.skm0078.audora.ui.theme.Dimens
import io.github.skm0078.audora.ui.theme.OptionBlock
import io.github.skm0078.audora.ui.theme.OptionDivider
import io.github.skm0078.audora.ui.theme.OptionGroup
import io.github.skm0078.audora.ui.theme.SwitchOption

/**
 * Repeats and "after each sentence" are reading modes, saved like the rest; the A–B range lasts until cleared or
 * another book opens. The A–B row says what is set (nothing, A only, or both pages) in its one line.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudySection(modes: ReadingModes, controls: StudyControls, onModes: (ReadingModes) -> Unit) {
    OptionGroup(title = null) {
        OptionBlock(stringResource(R.string.study_repeats)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val counts = 1..ReadingModes.MAX_SENTENCE_REPEATS
                counts.forEachIndexed { index, count ->
                    SegmentedButton(
                        selected = modes.sentenceRepeats == count,
                        onClick = { onModes(modes.copy(sentenceRepeats = count)) },
                        shape = SegmentedButtonDefaults.itemShape(index, counts.count()),
                    ) { Text(stringResource(R.string.study_repeats_times, count)) }
                }
            }
        }
        OptionDivider()
        ChoiceOption(
            stringResource(R.string.study_after),
            AfterSentence.entries,
            modes.afterSentence,
            label = { stringResource(afterLabel(it)) },
            onPick = { onModes(modes.copy(afterSentence = it)) },
            help = stringResource(R.string.study_after_detail),
        )
    }
    StudyLoopRows(controls)
}

@Composable
private fun StudyLoopRows(controls: StudyControls) {
    val range = controls.range
    val loop = range?.loop ?: true
    val status = when {
        range == null -> stringResource(R.string.study_loop_short)
        range.end == null -> stringResource(R.string.study_loop_a_only, range.start.page + 1)
        else -> stringResource(R.string.study_loop_range, range.start.page + 1, range.end.page + 1)
    }
    OptionGroup(title = null) {
        OptionBlock(
            stringResource(R.string.study_loop_title),
            subtitle = status,
            help = stringResource(R.string.study_loop_hint),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { controls.onStudy(StudyAction.MARK_START, loop) },
                    enabled = controls.canMark,
                ) { Text(stringResource(R.string.study_mark_a)) }
                OutlinedButton(
                    onClick = { controls.onStudy(StudyAction.MARK_END, loop) },
                    enabled = controls.canMark,
                ) { Text(stringResource(R.string.study_mark_b)) }
                TextButton(onClick = { controls.onStudy(StudyAction.CLEAR, loop) }, enabled = range != null) {
                    Text(stringResource(R.string.study_clear))
                }
            }
        }
        OptionDivider()
        // Nothing to loop until A is marked; the A and B taps carry the switch's value with them.
        SwitchOption(
            stringResource(R.string.study_loop),
            loop,
            { controls.onStudy(StudyAction.SET_LOOP, it) },
            help = stringResource(R.string.study_loop_detail),
            enabled = range != null,
        )
    }
}

private fun afterLabel(after: AfterSentence): Int = when (after) {
    AfterSentence.CONTINUE -> R.string.study_after_continue
    AfterSentence.GAP -> R.string.study_after_gap
    AfterSentence.STOP -> R.string.study_after_stop
}
