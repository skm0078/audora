// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/LookUpUi.kt
// Purpose: The dictionary dialog (plan item 33): download it, tap a word of the sentence or type one, read entries.

package io.github.skm0078.audora.ui.reader

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.dictionary.DictionaryEntry
import io.github.skm0078.audora.core.dictionary.PartOfSpeech
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.speech.dictionary.Dictionaries
import io.github.skm0078.audora.speech.ocr.OcrModelsState
import io.github.skm0078.audora.ui.jobs.DownloadTimeLeft

/** Shown while the binding's state is set: the download offer, the words, or the entries of the word picked. */
@Composable
fun LookUpOverlay(binding: LookUpBinding) {
    val state = binding.state ?: return
    val actions = binding.actions
    val ready = binding.download is OcrModelsState.Ready
    AlertDialog(
        onDismissRequest = actions::close,
        title = { Text(state.word ?: stringResource(R.string.dictionary_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    !ready -> DownloadOffer(state.dictionaryLang, binding.download, actions)
                    state.word == null -> WordPicker(state, actions)
                    else -> Entries(state.entries)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = actions::close) { Text(stringResource(R.string.dictionary_close)) }
        },
        dismissButton = {
            if (state.word !=
                null
            ) {
                TextButton(onClick = actions::back) { Text(stringResource(R.string.dictionary_back)) }
            }
        },
    )
}

@Composable
private fun DownloadOffer(lang: Lang, download: OcrModelsState?, actions: LookUpActions) {
    val context = LocalContext.current
    val size = Formatter.formatShortFileSize(context, Dictionaries.downloadBytes(lang))
    val onPhone = Formatter.formatShortFileSize(context, Dictionaries.unpackedBytes(lang))
    val offer = when (lang) {
        Lang.EN -> R.string.dictionary_offer
        Lang.HI -> R.string.dictionary_offer_hi
        Lang.BN -> R.string.dictionary_offer_bn
    }
    Text(stringResource(offer, size, onPhone), style = MaterialTheme.typography.bodyMedium)
    when (download) {
        is OcrModelsState.Downloading -> {
            val fraction = if (download.total > 0) download.bytes.toFloat() / download.total else 0f
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            DownloadTimeLeft(download.bytes, download.total)
            TextButton(onClick = actions::cancelDownload) { Text(stringResource(R.string.dictionary_cancel)) }
        }

        OcrModelsState.Queued -> Text(stringResource(R.string.dictionary_waiting))

        is OcrModelsState.Failed -> {
            Text(stringResource(R.string.dictionary_failed, download.message), color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = actions::download) { Text(stringResource(R.string.dictionary_download)) }
        }

        else -> OutlinedButton(onClick = actions::download) { Text(stringResource(R.string.dictionary_download)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordPicker(state: LookUpState, actions: LookUpActions) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.words.forEach { word -> OutlinedButton(onClick = { actions.pick(word) }) { Text(word) } }
    }
    var typed by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = typed,
        onValueChange = { typed = it },
        label = { Text(stringResource(R.string.dictionary_type)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    TextButton(onClick = { actions.pick(typed.trim()) }, enabled = typed.isNotBlank()) {
        Text(stringResource(R.string.dictionary_look_up))
    }
}

@Composable
private fun Entries(entries: List<DictionaryEntry>?) {
    when {
        entries == null -> LinearProgressIndicator(Modifier.fillMaxWidth())

        entries.isEmpty() -> Text(stringResource(R.string.dictionary_not_found))

        else -> entries.forEach { entry ->
            Text(entry.headword, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            entry.senses.forEachIndexed { index, sense ->
                Column {
                    Text(
                        stringResource(R.string.dictionary_sense, index + 1, partOfSpeech(sense.partOfSpeech)),
                        style = MaterialTheme.typography.labelMedium,
                        fontStyle = FontStyle.Italic,
                    )
                    Text(sense.definition, style = MaterialTheme.typography.bodyMedium)
                    if (sense.synonyms.isNotEmpty()) {
                        Text(
                            stringResource(R.string.dictionary_synonyms, sense.synonyms.joinToString(", ")),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun partOfSpeech(part: PartOfSpeech): String = stringResource(
    when (part) {
        PartOfSpeech.NOUN -> R.string.dictionary_noun
        PartOfSpeech.VERB -> R.string.dictionary_verb
        PartOfSpeech.ADJECTIVE -> R.string.dictionary_adjective
        PartOfSpeech.ADVERB -> R.string.dictionary_adverb
        PartOfSpeech.PRONOUN -> R.string.dictionary_pronoun
        PartOfSpeech.POSTPOSITION -> R.string.dictionary_postposition
        PartOfSpeech.CONJUNCTION -> R.string.dictionary_conjunction
        PartOfSpeech.INTERJECTION -> R.string.dictionary_interjection
        PartOfSpeech.NUMBER -> R.string.dictionary_number
        PartOfSpeech.OTHER -> R.string.dictionary_other
    },
)
