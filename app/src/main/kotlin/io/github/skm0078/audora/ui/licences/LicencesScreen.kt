// File: app/src/main/kotlin/io/github/skm0078/audora/ui/licences/LicencesScreen.kt
// Purpose: Settings → "Voices & licences" (P3-06): credits, licence and source of every voice, and the software.

package io.github.skm0078.audora.ui.licences

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.speech.voices.LicenceKind
import io.github.skm0078.audora.speech.voices.VoiceCredits
import io.github.skm0078.audora.speech.voices.VoiceLicence
import io.github.skm0078.audora.ui.theme.AudoraTheme
import io.github.skm0078.audora.ui.voices.HEADER_PADDING
import io.github.skm0078.audora.ui.voices.HORIZONTAL_PADDING
import io.github.skm0078.audora.ui.voices.languageName

@Composable
fun LicencesScreen(
    state: LicencesState,
    onBack: () -> Unit,
    onOpenTexts: (VoiceLicence) -> Unit,
    onCloseTexts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openLink = rememberLinkOpener()
    Scaffold(modifier = modifier.fillMaxSize(), topBar = { LicencesTopBar(onBack) }) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
            val voices = state.voices
            when {
                state.loadFailed ->
                    Text(stringResource(R.string.licences_load_failed), color = MaterialTheme.colorScheme.error)

                voices == null -> CircularProgressIndicator()

                else -> LicencesList(voices, openLink, onOpenTexts)
            }
        }
    }
    state.open?.let { LicenceTextsDialog(it, onCloseTexts) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LicencesTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.licences_title)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
            }
        },
    )
}

@Composable
private fun LicencesList(voices: List<VoiceLicence>, openLink: (String) -> Unit, onOpenTexts: (VoiceLicence) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "intro") { Caption(R.string.licences_intro, header = false) }
        Lang.entries.forEach { lang ->
            val inLang = voices.filter { it.lang == lang }
            if (inLang.isEmpty()) return@forEach
            item(key = "header:${lang.name}") { Caption(languageName(lang)) }
            items(inLang, key = { "${it.kind.name}:${it.id.name}" }) { voice ->
                VoiceLicenceItem(voice, openLink, onOpenTexts)
                HorizontalDivider()
            }
        }
        item(key = "phone") {
            Caption(R.string.voices_phone_voice)
            Caption(R.string.licences_phone_voice, header = false)
        }
        item(key = "software") { Caption(R.string.licences_software) }
        items(SPEECH_SOFTWARE, key = { it.name }) { software ->
            ListItem(
                headlineContent = { Text(software.name) },
                supportingContent = { Text(stringResource(software.licence)) },
                trailingContent = { LinkButton(R.string.licences_source) { openLink(software.url) } },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun VoiceLicenceItem(voice: VoiceLicence, openLink: (String) -> Unit, onOpenTexts: (VoiceLicence) -> Unit) {
    ListItem(
        headlineContent = { Text(voice.label) },
        supportingContent = {
            Column {
                Text(stringResource(kindText(voice)), style = MaterialTheme.typography.labelMedium)
                Text(voice.licence, style = MaterialTheme.typography.bodyMedium)
                voice.credits?.let { Text(it.credit, style = MaterialTheme.typography.bodySmall) }
                Row {
                    voice.credits?.let { credits ->
                        LinkButton(R.string.licences_licence) { openLink(credits.licenceUrl) }
                        LinkButton(R.string.licences_source) { openLink(credits.sourceUrl) }
                    }
                    if (voice.installed && voice.kind != LicenceKind.BUILT_IN) {
                        LinkButton(R.string.licences_files) { onOpenTexts(voice) }
                    }
                }
            }
        },
    )
}

@StringRes
private fun kindText(voice: VoiceLicence): Int = when (voice.kind) {
    LicenceKind.BUILT_IN -> R.string.licences_kind_built_in
    LicenceKind.IMPORTED -> R.string.licences_kind_imported
    LicenceKind.DOWNLOAD -> if (voice.installed) R.string.licences_kind_downloaded else R.string.licences_kind_download
}

@Composable
private fun LicenceTextsDialog(open: OpenLicence, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(open.voice.label) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (open.texts.isEmpty()) Text(stringResource(R.string.licences_no_files))
                open.texts.forEach { file ->
                    Text(
                        file.name,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = HEADER_PADDING),
                    )
                    Text(file.text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.licences_close)) } },
    )
}

/** A section title, or with [header] false a paragraph of explanation. */
@Composable
private fun Caption(@StringRes text: Int, header: Boolean = true) {
    Text(
        text = stringResource(text),
        style = if (header) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
        color = if (header) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = HORIZONTAL_PADDING, vertical = HEADER_PADDING),
    )
}

@Composable
private fun LinkButton(@StringRes label: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(stringResource(label)) }
}

/** Opens a link in the user's browser; a phone without one logs it instead of crashing. */
@Composable
private fun rememberLinkOpener(): (String) -> Unit {
    val uriHandler = LocalUriHandler.current
    return { url ->
        try {
            uriHandler.openUri(url)
        } catch (e: IllegalArgumentException) {
            AudoraLog.w(TAG, "No app can open $url", e)
        }
    }
}

/** The speech and OCR software every voice and scanned page runs on; credited once. */
private data class Software(val name: String, @StringRes val licence: Int, val url: String)

private val SPEECH_SOFTWARE = listOf(
    Software("sherpa-onnx (k2-fsa)", R.string.licences_apache, "https://github.com/k2-fsa/sherpa-onnx"),
    Software("ONNX Runtime (Microsoft)", R.string.licences_mit, "https://github.com/microsoft/onnxruntime"),
    Software("eSpeak NG", R.string.licences_gpl3, "https://github.com/espeak-ng/espeak-ng"),
    Software("Tesseract OCR and its data", R.string.licences_apache, "https://github.com/tesseract-ocr/tesseract"),
    Software("wordfreq (Robyn Speer), word lists", R.string.licences_cc_by_sa, "https://github.com/rspeer/wordfreq"),
    Software(
        "Edge-Punct-Casing punctuation model (frankyoujian), if downloaded",
        R.string.licences_apache,
        "https://github.com/frankyoujian/Edge-Punct-Casing",
    ),
    Software(
        "Open English Wordnet 2025 (Open English Wordnet team, from Princeton WordNet), dictionary, if downloaded",
        R.string.licences_cc_by_wordnet,
        "https://en-word.net/",
    ),
    Software(
        "Wiktionary (Wiktionary contributors, via Wiktextract and kaikki.org), Hindi and Bengali dictionaries, " +
            "if downloaded",
        R.string.licences_cc_by_sa_wiktionary,
        "https://kaikki.org/",
    ),
    Software("Audora", R.string.licences_gpl3, "https://github.com/skm0078/audora"),
)

private const val TAG = "UI.Licences"

@Preview(showBackground = true)
@Composable
private fun LicencesScreenPreview() {
    val voices = listOf(
        VoiceLicence(
            VoiceId(EngineId.SHERPA, "piper-bn-google"),
            "Bengali, Piper google",
            Lang.BN,
            "CC BY-SA 4.0: Google's Bengali speech data (OpenSLR 37) and CMU Indic",
            VoiceCredits(
                "Piper voice “google”, fine-tuned on Google's crowd-sourced Bengali speech (OpenSLR 37).",
                "https://creativecommons.org/licenses/by-sa/4.0/",
                "https://huggingface.co/rhasspy/piper-voices",
            ),
            LicenceKind.DOWNLOAD,
            installed = true,
        ),
    )
    AudoraTheme { LicencesScreen(LicencesState(voices = voices), onBack = {}, onOpenTexts = {}, onCloseTexts = {}) }
}
