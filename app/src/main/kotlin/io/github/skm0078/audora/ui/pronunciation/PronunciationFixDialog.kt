// File: app/src/main/kotlin/io/github/skm0078/audora/ui/pronunciation/PronunciationFixDialog.kt
// Purpose: The one dialog for adding and removing pronunciation fixes, opened from a sentence's menu (a book's fixes)
//          and from Settings (every book's). Each caller adds only what differs: what sits above the fields, extra
//          switches, and how a fix says where it applies.

package io.github.skm0078.audora.ui.pronunciation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.notes.PronunciationRule
import io.github.skm0078.audora.ui.theme.Dimens
import io.github.skm0078.audora.ui.theme.SwitchLine

/**
 * Two fields, Whole word only, then [options], Add fix, and the fixes in use, each with its own Delete. The typed words
 * clear after Add and start over when [draftKey] changes (another sentence); they survive rotation.
 *
 * @param where how a fix in the list says where it applies, shown in its brackets.
 * @param above what the fix is for: the sentence, or a language choice.
 */
@Composable
internal fun PronunciationFixDialog(
    title: String,
    rules: List<PronunciationRule>,
    where: @Composable (PronunciationRule) -> String,
    onAdd: (FixDraft) -> Unit,
    onDelete: (PronunciationRule) -> Unit,
    onDismiss: () -> Unit,
    draftKey: Any? = null,
    above: @Composable ColumnScope.() -> Unit = {},
    options: @Composable ColumnScope.() -> Unit = {},
) {
    var written by rememberSaveable(draftKey) { mutableStateOf("") }
    var spoken by rememberSaveable(draftKey) { mutableStateOf("") }
    var wholeWord by rememberSaveable(draftKey) { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            // Scrolls: at large text sizes a few fixes are enough to push Add off the dialog.
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
            ) {
                above()
                OutlinedTextField(written, { written = it }, label = { Text(stringResource(R.string.fix_written)) })
                OutlinedTextField(spoken, { spoken = it }, label = { Text(stringResource(R.string.fix_spoken)) })
                SwitchLine(stringResource(R.string.fix_whole_word), wholeWord, { wholeWord = it })
                options()
                TextButton(
                    onClick = {
                        onAdd(FixDraft(written, spoken, wholeWord))
                        written = ""
                        spoken = ""
                    },
                    enabled = written.isNotBlank() && spoken.isNotBlank(),
                    modifier = Modifier.heightIn(min = Dimens.TouchTarget),
                ) { Text(stringResource(R.string.fix_add)) }
                rules.forEach { rule -> FixRow(rule, where(rule)) { onDelete(rule) } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.notes_done)) } },
    )
}

/** "colonel → kernel (English)" and Delete, which TalkBack names after the fix so a list of them is told apart. */
@Composable
private fun FixRow(rule: PronunciationRule, where: String, onDelete: () -> Unit) {
    val deleteLabel = stringResource(R.string.a11y_fix_delete, rule.written, rule.spoken)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.fix_rule, rule.written, rule.spoken, where),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(
            onClick = onDelete,
            modifier = Modifier.heightIn(min = Dimens.TouchTarget).semantics { contentDescription = deleteLabel },
        ) { Text(stringResource(R.string.notes_delete)) }
    }
}
