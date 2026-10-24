// File: app/src/main/kotlin/io/github/skm0078/audora/ui/jobs/JobsScreen.kt
// Purpose: One screen for every long job (audiobook, copies, studio mode) and every download, each with its progress,
//          time left and Cancel (ADR-067), reached from the library's top bar and from the jobs' notifications.

package io.github.skm0078.audora.ui.jobs

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.playback.jobs.LongJob
import io.github.skm0078.audora.playback.jobs.LongJobs
import io.github.skm0078.audora.speech.download.DownloadJob
import io.github.skm0078.audora.speech.download.DownloadJobs
import io.github.skm0078.audora.speech.download.DownloadKind
import io.github.skm0078.audora.speech.voices.VoiceLibrary
import io.github.skm0078.audora.ui.theme.Dimens
import io.github.skm0078.audora.ui.tutor.langName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The live jobs and downloads; voice names are read from the catalog once, off the main thread. */
@Composable
fun JobsRoute(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val jobs by LongJobs.jobs.collectAsStateWithLifecycle()
    val downloads by remember { DownloadJobs.observe(context) }.collectAsStateWithLifecycle(emptyList())
    val voiceNames by produceState(emptyMap<String, String>()) {
        value = withContext(Dispatchers.IO) {
            VoiceLibrary.get(context).catalog.voices.associate { it.id to it.spec.label }
        }
    }
    JobsScreen(
        jobs = shownJobs(jobs, book = null),
        downloads = downloads,
        voiceNames = voiceNames,
        onCancelDownload = { DownloadJobs.cancel(context, it) },
        onBack = onBack,
    )
}

/** How many jobs and downloads are working now, for the badge on the library's Jobs button. */
@Composable
fun rememberRunningJobCount(): Int {
    val context = LocalContext.current.applicationContext
    val jobs by LongJobs.jobs.collectAsStateWithLifecycle()
    val downloads by remember { DownloadJobs.observe(context) }.collectAsStateWithLifecycle(emptyList())
    return jobs.values.count { it.running } + downloads.size
}

/**
 * Jobs first (they were started from a book and take longest), then downloads. A finished job stays, with how it
 * ended, until it is dismissed, as on the library's cards. Nothing here is a live region except how a job ended
 * (its card announces that once): progress read aloud would talk over the book.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobsScreen(
    jobs: List<LongJob>,
    downloads: List<DownloadJob>,
    voiceNames: Map<String, String>,
    onCancelDownload: (DownloadJob) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.jobs_title), Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            Modifier.padding(innerPadding),
            contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceS),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        ) {
            if (jobs.isEmpty() && downloads.isEmpty()) {
                item { Text(stringResource(R.string.jobs_empty), style = MaterialTheme.typography.bodyLarge) }
            }
            if (jobs.isNotEmpty()) {
                item { SectionTitle(R.string.jobs_section_books) }
                items(jobs, key = { "job-${it.kind}" }) { LongJobCard(it) }
            }
            if (downloads.isNotEmpty()) {
                item { SectionTitle(R.string.jobs_section_downloads) }
                items(downloads, key = { "download-${it.id}" }) { download ->
                    DownloadCard(download, downloadName(download, voiceNames)) { onCancelDownload(download) }
                }
                item {
                    Text(
                        stringResource(R.string.jobs_download_kept),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(label: Int) {
    Text(
        stringResource(label),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Dimens.SpaceM).semantics { heading() },
    )
}

/** "Voice: Bruno", "34 MB of 120 MB", a bar, the time left, and Cancel. */
@Composable
private fun DownloadCard(download: DownloadJob, name: String, onCancel: () -> Unit) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Dimens.SpaceL), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            when {
                !download.running -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = Dimens.SpaceXs))
                    Text(stringResource(R.string.jobs_download_waiting), style = MaterialTheme.typography.bodyMedium)
                }

                download.total > 0L -> {
                    LinearProgressIndicator(
                        progress = { (download.bytes.toFloat() / download.total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.SpaceXs),
                    )
                    val received = Formatter.formatShortFileSize(context, download.bytes)
                    val total = Formatter.formatShortFileSize(context, download.total)
                    Text(
                        stringResource(R.string.jobs_download_progress, received, total),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    DownloadTimeLeft(download.bytes, download.total)
                }

                // Running, its size not known yet: the first bytes are on their way.
                else -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = Dimens.SpaceXs))
            }
            // TalkBack moving between buttons hears which download this one stops, not a bare "Cancel".
            val cancel = stringResource(R.string.jobs_download_cancel)
            val described = stringResource(R.string.storage_action_on, cancel, name)
            TextButton(
                onClick = onCancel,
                modifier = Modifier.heightIn(min = Dimens.TouchTarget).semantics { contentDescription = described },
            ) { Text(cancel) }
        }
    }
}

/** What a download fetches, in the listener's words: the voice's name, the language, or the model. */
@Composable
internal fun downloadName(download: DownloadJob, voiceNames: Map<String, String>): String {
    val label = labelOf(download.kind)
    val itemName = when (download.kind) {
        DownloadKind.VOICE -> download.item?.let(voiceNames::get)
        else -> Lang.entries.firstOrNull { it.bcp47Tag == download.item }?.let { stringResource(langName(it)) }
    }
    val named = label.named
    return if (named != null && itemName != null) stringResource(named, itemName) else stringResource(label.any)
}

/** A kind's name with its item ("Voice: Bruno"), if it has items, and without ("A voice"). */
private class DownloadLabel(@StringRes val named: Int?, @StringRes val any: Int)

private fun labelOf(kind: DownloadKind): DownloadLabel = when (kind) {
    DownloadKind.VOICE -> DownloadLabel(R.string.jobs_download_voice, R.string.jobs_download_voice_any)
    DownloadKind.TUTOR -> DownloadLabel(R.string.jobs_download_tutor, R.string.jobs_download_tutor_any)
    DownloadKind.DICTIONARY -> DownloadLabel(R.string.jobs_download_dictionary, R.string.jobs_download_dictionary_any)
    DownloadKind.TEXT_RECOGNITION -> DownloadLabel(null, R.string.storage_ocr)
    DownloadKind.PUNCTUATION -> DownloadLabel(null, R.string.storage_punctuation)
    DownloadKind.VOICE_COMMANDS -> DownloadLabel(null, R.string.storage_voice_commands)
    DownloadKind.ACTED_VOICE -> DownloadLabel(null, R.string.storage_acted_voice)
}

/** Opens the Jobs screen once a job's notification asked for it ([requested]), and clears the request. */
@Composable
fun OpenJobsEffect(requested: Boolean, onHandled: () -> Unit, open: () -> Unit) {
    LaunchedEffect(requested) {
        if (requested) {
            onHandled()
            open()
        }
    }
}
