// File: app/src/main/kotlin/io/github/skm0078/audora/ui/settings/PronunciationSettings.kt
// Purpose: Settings' Pronunciation group (plan item 1): the fixes every book uses, added and removed in one dialog.

package io.github.skm0078.audora.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.notes.PronunciationRule
import io.github.skm0078.audora.core.notes.PronunciationStore
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.ui.loggingFailures
import io.github.skm0078.audora.ui.theme.Dimens
import io.github.skm0078.audora.ui.voices.languageName
import kotlinx.coroutines.launch

/**
 * The every-book fixes (a book's own are made from its sentence menu, where the sentence gives the language).
 *
 * @property rules null while they load.
 */
class PronunciationSettings(
    val rules: List<PronunciationRule>?,
    val onAdd: (PronunciationRule) -> Unit,
    val onDelete: (PronunciationRule) -> Unit,
)

/** Reads and writes [store]'s every-book fixes; a failed write is logged and told in a toast. */
@Composable
internal fun rememberPronunciationSettings(store: PronunciationStore): PronunciationSettings {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rules by remember(store) { store.observe(bookId = null).loggingFailures(TAG, "the pronunciation fixes") }
        .collectAsStateWithLifecycle(initialValue = null)
    return PronunciationSettings(
        rules = rules,
        onAdd = { rule ->
            scope.launch { store.add(rule).reportFailure(context, "add", rule) }
        },
        onDelete = { rule ->
            scope.launch { store.delete(rule.id).reportFailure(context, "delete", rule) }
        },
    )
}

private fun Outcome<*, *>.reportFailure(context: Context, what: String, rule: PronunciationRule) {
    if (this is Outcome.Failure) {
        AudoraLog.w(TAG, "Could not $what a pronunciation fix, ${rule.logLabel}: $error")
        Toast.makeText(context, R.string.pronunciation_save_failed, Toast.LENGTH_LONG).show()
    } else {
        AudoraLog.i(TAG, "Pronunciation fix: $what, ${rule.logLabel}")
    }
}

/** One row; the dialog lists, adds and removes. */
@Composable
internal fun PronunciationGroup(settings: PronunciationSettings) {
    var open by rememberSaveable { mutableStateOf(false) }
    SettingsGroup(stringResource(R.string.settings_group_pronunciation)) {
        SettingsRow(
            stringResource(R.string.settings_pronunciation_title),
            summary = { Text(stringResource(R.string.settings_pronunciation_body)) },
            onClick = { open = true },
        )
    }
    if (open) PronunciationDialog(settings) { open = false }
}

@Composable
private fun PronunciationDialog(settings: PronunciationSettings, onDismiss: () -> Unit) {
    var lang by rememberSaveable { mutableStateOf(Lang.EN) }
    var written by rememberSaveable { mutableStateOf("") }
    var spoken by rememberSaveable { mutableStateOf("") }
    var wholeWord by rememberSaveable { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_pronunciation_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                    Lang.entries.forEach { option ->
                        FilterChip(
                            selected = option == lang,
                            onClick = { lang = option },
                            label = { Text(stringResource(languageName(option))) },
                        )
                    }
                }
                OutlinedTextField(written, { written = it }, label = { Text(stringResource(R.string.fix_written)) })
                OutlinedTextField(spoken, { spoken = it }, label = { Text(stringResource(R.string.fix_spoken)) })
                WholeWordSwitch(wholeWord) { wholeWord = it }
                TextButton(
                    onClick = {
                        settings.onAdd(PronunciationRule(0, null, lang, written.trim(), spoken.trim(), wholeWord))
                        written = ""
                        spoken = ""
                    },
                    enabled = written.isNotBlank() && spoken.isNotBlank(),
                ) { Text(stringResource(R.string.fix_add)) }
                settings.rules.orEmpty().forEach { rule -> RuleRow(rule) { settings.onDelete(rule) } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.notes_done)) } },
    )
}

@Composable
private fun WholeWordSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.fix_whole_word), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun RuleRow(rule: PronunciationRule, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.fix_rule, rule.written, rule.spoken, stringResource(languageName(rule.lang))),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onDelete) { Text(stringResource(R.string.notes_delete)) }
    }
}

private const val TAG = "Audora.Pronunciation"
