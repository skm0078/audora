// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/StudySection.kt
// Purpose: The speech sheet's study controls (plan item 31): repeats, what follows each sentence, and A–B loops.

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.AfterSentence
import io.github.skm0078.audora.core.model.ReadingModes
import io.github.skm0078.audora.playback.contract.StudyAction

/**
 * Repeats and "after each sentence" are reading modes, saved like the rest; the A–B range lasts until cleared or
 * another book opens. Plain Material controls (restyled with the UI pass), like the reading modes above.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudySection(modes: ReadingModes, controls: StudyControls, onModes: (ReadingModes) -> Unit) {
    Text(stringResource(R.string.study_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.study_repeats), style = MaterialTheme.typography.bodyLarge)
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
    Text(stringResource(R.string.study_after), style = MaterialTheme.typography.bodyLarge)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        AfterSentence.entries.forEachIndexed { index, after ->
            SegmentedButton(
                selected = modes.afterSentence == after,
                onClick = { onModes(modes.copy(afterSentence = after)) },
                shape = SegmentedButtonDefaults.itemShape(index, AfterSentence.entries.size),
            ) { Text(stringResource(afterLabel(after))) }
        }
    }
    Detail(stringResource(R.string.study_after_detail))
    StudyLoopRows(controls)
}

@Composable
private fun StudyLoopRows(controls: StudyControls) {
    val range = controls.range
    val loop = range?.loop ?: true
    Text(stringResource(R.string.study_loop_title), style = MaterialTheme.typography.bodyLarge)
    Detail(
        when {
            range == null -> stringResource(R.string.study_loop_hint)
            range.end == null -> stringResource(R.string.study_loop_a_only, range.start.page + 1)
            else -> stringResource(R.string.study_loop_range, range.start.page + 1, range.end.page + 1)
        },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { controls.onStudy(StudyAction.MARK_START, loop) }, enabled = controls.canMark) {
            Text(stringResource(R.string.study_mark_a))
        }
        OutlinedButton(onClick = { controls.onStudy(StudyAction.MARK_END, loop) }, enabled = controls.canMark) {
            Text(stringResource(R.string.study_mark_b))
        }
        TextButton(onClick = { controls.onStudy(StudyAction.CLEAR, loop) }, enabled = range != null) {
            Text(stringResource(R.string.study_clear))
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.study_loop), style = MaterialTheme.typography.bodyLarge)
            Detail(stringResource(R.string.study_loop_detail))
        }
        // Nothing to loop until A is marked; the A and B taps carry the switch's value with them.
        Switch(
            checked = loop,
            onCheckedChange = { controls.onStudy(StudyAction.SET_LOOP, it) },
            enabled = range != null,
        )
    }
}

@Composable
private fun Detail(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun afterLabel(after: AfterSentence): Int = when (after) {
    AfterSentence.CONTINUE -> R.string.study_after_continue
    AfterSentence.GAP -> R.string.study_after_gap
    AfterSentence.STOP -> R.string.study_after_stop
}
