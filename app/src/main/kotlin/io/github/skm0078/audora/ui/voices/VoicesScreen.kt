// File: app/src/main/kotlin/io/github/skm0078/audora/ui/voices/VoicesScreen.kt
// Purpose: Settings → Voices (P4-01): a language pack per language first (ADR-056), then every voice to pick or delete.

package io.github.skm0078.audora.ui.voices

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.speech.voices.VoiceKind
import io.github.skm0078.audora.speech.voices.VoiceRow
import io.github.skm0078.audora.speech.voices.VoiceStatus
import io.github.skm0078.audora.ui.theme.AudoraTheme
import io.github.skm0078.audora.ui.theme.MoreOptions

@Composable
fun VoicesScreen(state: VoicesState, actions: VoicesActions, modifier: Modifier = Modifier) {
    val snackbar = remember { SnackbarHostState() }
    // Saveable: the folder picker can outlive this process; the language and name typed before it must survive.
    var importRequest by rememberSaveable { mutableStateOf<String?>(null) }
    var importLang by rememberSaveable { mutableStateOf(Lang.EN) }
    var importDialog by rememberSaveable { mutableStateOf(false) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val label = importRequest
        importRequest = null
        if (uri != null && label != null) actions.onImport(uri, importLang, label)
    }

    val messageText = state.message?.let { messageText(it) }
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            actions.onMessageShown()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { VoicesTopBar(actions::onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (state.importing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            VoicesContent(state, actions, onImport = { if (!state.importing) importDialog = true })
        }
    }
    if (importDialog) {
        ImportVoiceDialog(
            initialLang = importLang,
            onDismiss = { importDialog = false },
            onChooseFolder = { lang, label ->
                importDialog = false
                importLang = lang
                importRequest = label
                folderPicker.launch(null)
            },
        )
    }
    state.pendingDelete?.let { DeleteDialog(it, actions) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoicesTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.voices_title), Modifier.semantics { heading() }) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
            }
        },
    )
}

/**
 * One download per language first (ADR-056): most listeners need nothing else. Every voice, with samples, other
 * choices and deletes, waits under "All voices", closed; the import row only where the build offers it.
 */
@Composable
private fun VoicesContent(state: VoicesState, actions: VoicesActions, onImport: () -> Unit) {
    val rows = state.rows
    when {
        state.loadFailed -> Centered {
            Text(stringResource(R.string.voices_load_failed), color = MaterialTheme.colorScheme.error)
        }

        rows == null -> Centered { CircularProgressIndicator() }

        else -> LazyColumn {
            if (state.packs.isNotEmpty()) {
                item(key = "packs") { PacksHeader() }
                items(state.packs, key = { "pack:${it.lang.name}" }) { pack ->
                    LanguagePackRow(pack, { actions.onDownload(pack.voice) }, { actions.onCancel(pack.voice) })
                }
            }
            item(key = "all") {
                MoreOptions(
                    stringResource(R.string.voices_all_title),
                    stringResource(R.string.voices_all_summary),
                    headerPadding = HORIZONTAL_PADDING,
                ) {
                    AllVoices(rows, state, actions)
                }
            }
            if (state.canImport) {
                item(key = "import") {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.voices_import_title)) },
                        supportingContent = { Text(stringResource(R.string.voices_import_body)) },
                        trailingContent = {
                            TextButton(onClick = onImport, enabled = !state.importing) {
                                Text(stringResource(R.string.voices_import_action))
                            }
                        },
                    )
                }
            }
        }
    }
}

/** Each language's voices, the phone's voice first for Hindi and Bengali. A short list: no lazy rows needed. */
@Composable
private fun AllVoices(rows: List<VoiceRow>, state: VoicesState, actions: VoicesActions) {
    Column {
        Lang.entries.forEach { lang ->
            LanguageHeader(lang)
            if (lang != Lang.EN) {
                val phone = rows.none { it.lang == lang && it.readsLanguage }
                PhoneVoiceRow(selected = phone) { actions.onUseDefault(lang) }
            }
            rows.filter { it.lang == lang }.forEach { row ->
                key(row.key) {
                    VoiceRowItem(row, actions, state.sample)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun PacksHeader() {
    Column(Modifier.padding(horizontal = HORIZONTAL_PADDING, vertical = HEADER_PADDING)) {
        Text(
            stringResource(R.string.voice_packs_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.voice_packs_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LanguageHeader(lang: Lang) {
    Text(
        text = stringResource(languageName(lang)),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = HORIZONTAL_PADDING, vertical = HEADER_PADDING),
    )
}

@Composable
internal fun DeleteDialog(row: VoiceRow, actions: VoicesActions) {
    AlertDialog(
        onDismissRequest = actions::onCancelDelete,
        title = { Text(stringResource(R.string.voices_delete_title, row.label)) },
        text = { Text(stringResource(R.string.voices_delete_body)) },
        confirmButton = {
            TextButton(onClick = actions::onConfirmDelete) { Text(stringResource(R.string.voices_delete)) }
        },
        dismissButton = {
            TextButton(onClick = actions::onCancelDelete) { Text(stringResource(R.string.voices_cancel)) }
        },
    )
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun messageText(message: VoicesMessage): String = when (message) {
    is VoicesMessage.Imported -> stringResource(R.string.voices_imported, message.label)
    is VoicesMessage.ImportFailed -> message.reason
    is VoicesMessage.Deleted -> stringResource(R.string.voices_deleted, message.label)
    is VoicesMessage.DeleteFailed -> stringResource(R.string.voices_delete_failed, message.label)
    VoicesMessage.SampleFailed -> stringResource(R.string.voices_sample_failed)
}

@Preview(showBackground = true)
@Composable
private fun VoicesScreenPreview() {
    val rows = listOf(
        VoiceRow(
            VoiceId(EngineId.SHERPA, "piper-en-ljspeech"),
            "English (US), Piper ljspeech",
            Lang.EN,
            "Piper voice (MIT)",
            0,
            VoiceKind.BUILT_IN,
            VoiceStatus.BuiltIn,
            readsLanguage = true,
        ),
        VoiceRow(
            VoiceId(EngineId.SHERPA, "piper-en-cori"),
            "English (UK), Piper cori",
            Lang.EN,
            "Piper voice (MIT)",
            64_649_879,
            VoiceKind.CATALOG,
            VoiceStatus.Downloading(20_000_000, 64_649_879),
            readsLanguage = false,
        ),
    )
    AudoraTheme { VoicesScreen(VoicesState(rows = rows), PreviewActions) }
}

private object PreviewActions : VoicesActions {
    override fun onBack() = Unit

    override fun onDownload(row: VoiceRow) = Unit

    override fun onCancel(row: VoiceRow) = Unit

    override fun onUse(row: VoiceRow) = Unit

    override fun onUseDefault(lang: Lang) = Unit

    override fun onRequestDelete(row: VoiceRow) = Unit

    override fun onConfirmDelete() = Unit

    override fun onCancelDelete() = Unit

    override fun onImport(treeUri: Uri, lang: Lang, label: String) = Unit

    override fun onMessageShown() = Unit
}
