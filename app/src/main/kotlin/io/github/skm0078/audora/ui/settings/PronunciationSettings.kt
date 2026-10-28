// File: app/src/main/kotlin/io/github/skm0078/audora/ui/settings/PronunciationSettings.kt
// Purpose: Settings' Pronunciation group (plan item 1): the fixes every book uses, added and removed in one dialog.

package io.github.skm0078.audora.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.notes.PronunciationRule
import io.github.skm0078.audora.core.notes.PronunciationStore
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.ui.loggingFailures
import io.github.skm0078.audora.ui.pronunciation.PronunciationFixDialog
import io.github.skm0078.audora.ui.theme.Dimens
import io.github.skm0078.audora.ui.theme.LinkOption
import io.github.skm0078.audora.ui.theme.OptionGroup
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
    OptionGroup(stringResource(R.string.settings_group_pronunciation)) {
        LinkOption(
            stringResource(R.string.settings_pronunciation_title),
            onClick = { open = true },
            subtitle = stringResource(R.string.settings_pronunciation_body),
            value = settings.rules?.size?.toString(),
        )
    }
    if (open) PronunciationDialog(settings) { open = false }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PronunciationDialog(settings: PronunciationSettings, onDismiss: () -> Unit) {
    var lang by rememberSaveable { mutableStateOf(Lang.EN) }
    PronunciationFixDialog(
        title = stringResource(R.string.settings_pronunciation_title),
        rules = settings.rules.orEmpty(),
        where = { rule -> stringResource(languageName(rule.lang)) },
        onAdd = { settings.onAdd(PronunciationRule(0, null, lang, it.written.trim(), it.spoken.trim(), it.wholeWord)) },
        onDelete = settings.onDelete,
        onDismiss = onDismiss,
        above = {
            // Wraps at large text sizes instead of pushing the last language off the dialog.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                Lang.entries.forEach { option ->
                    FilterChip(
                        selected = option == lang,
                        onClick = { lang = option },
                        label = { Text(stringResource(languageName(option))) },
                    )
                }
            }
        },
    )
}

private const val TAG = "Audora.Pronunciation"
