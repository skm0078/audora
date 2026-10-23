// File: app/src/main/kotlin/io/github/skm0078/audora/ui/voices/VoiceRowItem.kt
// Purpose: One voice in the Voices list (status, licence, actions), the phone-voice row and the import dialog.
@file:Suppress("TooManyFunctions") // Small private composables for one row's parts.

package io.github.skm0078.audora.ui.voices

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.speech.voices.VoiceKind
import io.github.skm0078.audora.speech.voices.VoiceRow
import io.github.skm0078.audora.speech.voices.VoiceStatus

/**
 * The radio picks the reading voice; it is enabled only for voices that can speak now and were not measured too slow
 * on this phone (P4-02). A too-slow voice that already reads its language stays selected.
 */
@Composable
internal fun VoiceRowItem(row: VoiceRow, actions: VoicesActions, sample: VoiceSample? = null) {
    val canSpeak = row.canSpeak
    val usable = canSpeak && (!row.tooSlow || row.readsLanguage)
    ListItem(
        leadingContent = {
            RadioButton(selected = row.readsLanguage, onClick = { actions.onUse(row) }, enabled = usable)
        },
        headlineContent = { Text(row.label) },
        supportingContent = {
            Column {
                if (row.recommended) {
                    Text(
                        stringResource(R.string.voices_recommended),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                StatusLine(row)
                SpeedLine(row)
                Text(
                    row.licence,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (canSpeak) SampleButton(row, sample?.takeIf { it.id == row.id.name }, actions)
                RowAction(row, actions)
            }
        },
        modifier = Modifier.clickable(enabled = usable && !row.readsLanguage) { actions.onUse(row) },
    )
}

private val VoiceRow.canSpeak: Boolean
    get() = status is VoiceStatus.BuiltIn || status is VoiceStatus.Installed || status is VoiceStatus.UpdateAvailable

/** Play a sample; while the voice loads a spinner shows, while it plays a stop button. Either tap stops it. */
@Composable
private fun SampleButton(row: VoiceRow, sample: VoiceSample?, actions: VoicesActions) {
    val label = stringResource(
        if (sample == null) R.string.voices_sample_play else R.string.voices_sample_stop,
        row.label,
    )
    IconButton(onClick = { actions.onSample(row) }) {
        when {
            sample == null -> Icon(painterResource(R.drawable.ic_play), contentDescription = label)

            sample.playing -> Icon(painterResource(R.drawable.ic_stop), contentDescription = label)

            else -> Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(SAMPLE_SPINNER), strokeWidth = SAMPLE_SPINNER_STROKE)
                Icon(painterResource(R.drawable.ic_stop), contentDescription = label, Modifier.size(SAMPLE_STOP_ICON))
            }
        }
    }
}

@Composable
private fun StatusLine(row: VoiceRow) {
    when (val status = row.status) {
        is VoiceStatus.Downloading -> Column {
            Text(stringResource(R.string.voices_status_downloading, megabytes(status.bytes), megabytes(status.total)))
            if (status.total > 0) {
                LinearProgressIndicator(
                    progress = { status.bytes.toFloat() / status.total },
                    modifier = Modifier.fillMaxWidth().padding(top = PROGRESS_TOP),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = PROGRESS_TOP))
            }
        }

        // A download's outcomes are announced as they appear; its progress ticks are not, so TalkBack is not flooded.
        is VoiceStatus.Failed -> Text(
            stringResource(R.string.voices_status_failed),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )

        else -> Text(statusText(row), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

/** Measured render speed, once the voice has read something on this phone. */
@Composable
private fun SpeedLine(row: VoiceRow) {
    val speed = row.speed ?: return
    // The screen's locale, read so a language change recomposes this line.
    val locale = LocalConfiguration.current.locales[0]
    if (row.tooSlow) {
        Text(
            stringResource(R.string.voices_speed_slow, String.format(locale, ONE_DECIMAL, speed.rtf)),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    } else if (speed.rtf > 0) {
        Text(
            stringResource(R.string.voices_speed_ok, String.format(locale, ONE_DECIMAL, 1 / speed.rtf)),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun statusText(row: VoiceRow): String = when (row.status) {
    VoiceStatus.BuiltIn -> stringResource(R.string.voices_status_built_in)

    VoiceStatus.NotDownloaded -> stringResource(R.string.voices_status_available, megabytes(row.sizeBytes))

    VoiceStatus.Queued -> stringResource(R.string.voices_status_queued)

    VoiceStatus.UpdateAvailable -> stringResource(R.string.voices_status_update)

    else -> if (row.kind == VoiceKind.CUSTOM) {
        stringResource(R.string.voices_status_imported, megabytes(row.sizeBytes))
    } else {
        stringResource(R.string.voices_status_installed, megabytes(row.sizeBytes))
    }
}

@Composable
private fun RowAction(row: VoiceRow, actions: VoicesActions) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (row.status) {
            VoiceStatus.NotDownloaded, is VoiceStatus.Failed -> ActionButton(R.string.voices_download) {
                actions.onDownload(row)
            }

            VoiceStatus.UpdateAvailable -> ActionButton(R.string.voices_update) { actions.onDownload(row) }

            VoiceStatus.Queued, is VoiceStatus.Downloading -> ActionButton(R.string.voices_cancel) {
                actions.onCancel(row)
            }

            else -> Unit
        }
        val deletable = row.status is VoiceStatus.Installed || row.status is VoiceStatus.UpdateAvailable
        if (deletable) ActionButton(R.string.voices_delete) { actions.onRequestDelete(row) }
    }
}

@Composable
private fun ActionButton(@StringRes label: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(stringResource(label)) }
}

/** Hindi and Bengali read with the phone's own voice unless a neural voice is picked (Build Spec §3). */
@Composable
internal fun PhoneVoiceRow(selected: Boolean, onSelect: () -> Unit) {
    ListItem(
        leadingContent = { RadioButton(selected = selected, onClick = onSelect) },
        headlineContent = { Text(stringResource(R.string.voices_phone_voice)) },
        supportingContent = { Text(stringResource(R.string.voices_phone_voice_body)) },
        modifier = Modifier.clickable(enabled = !selected, onClick = onSelect),
    )
}

/** Asks which language the imported voice speaks and what to call it, then opens the folder picker. */
@Composable
internal fun ImportVoiceDialog(initialLang: Lang, onDismiss: () -> Unit, onChooseFolder: (Lang, String) -> Unit) {
    var lang by rememberSaveable { mutableStateOf(initialLang) }
    var label by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.voices_import_title)) },
        text = {
            Column {
                Text(stringResource(R.string.voices_import_language))
                Lang.entries.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().selectable(selected = option == lang) { lang = option },
                    ) {
                        RadioButton(selected = option == lang, onClick = { lang = option })
                        Text(stringResource(languageName(option)))
                    }
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(MAX_LABEL_INPUT) },
                    label = { Text(stringResource(R.string.voices_import_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.voices_import_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = PROGRESS_TOP),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onChooseFolder(lang, label.trim()) }) {
                Text(stringResource(R.string.voices_import_choose_folder))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.voices_cancel)) } },
    )
}

@StringRes
internal fun languageName(lang: Lang): Int = when (lang) {
    Lang.EN -> R.string.voices_lang_en
    Lang.HI -> R.string.voices_lang_hi
    Lang.BN -> R.string.voices_lang_bn
}

/** Whole megabytes, at least 1 for anything non-empty, so a small voice never shows "0 MB". */
internal fun megabytes(bytes: Long): Long = if (bytes <= 0) 0 else maxOf(1, (bytes + HALF_MB) / BYTES_PER_MB)

internal val HORIZONTAL_PADDING = 16.dp
internal val HEADER_PADDING = 8.dp
private val PROGRESS_TOP = 4.dp
private val SAMPLE_SPINNER = 28.dp
private val SAMPLE_SPINNER_STROKE = 2.dp
private val SAMPLE_STOP_ICON = 14.dp
private const val MAX_LABEL_INPUT = 60
private const val ONE_DECIMAL = "%.1f"
private const val BYTES_PER_MB = 1024L * 1024
private const val HALF_MB = BYTES_PER_MB / 2
