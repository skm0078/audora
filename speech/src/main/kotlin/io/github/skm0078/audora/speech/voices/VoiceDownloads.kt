// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/voices/VoiceDownloads.kt
// Purpose: Starts, cancels and observes background voice downloads through WorkManager (downloads only, AGENTS.md).

package io.github.skm0078.audora.speech.voices

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.skm0078.audora.core.config.VoiceDownloadConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * One unique work per voice (`voice-download:<id>`, KEEP), so tapping Download twice never starts two transfers.
 * Expedited when the quota allows, so a download the user is watching starts at once.
 */
class VoiceDownloads(
    private val workManager: () -> WorkManager,
    private val catalog: () -> VoiceCatalog,
    private val files: VoiceFiles,
    private val config: VoiceDownloadConfig,
) {
    fun start(id: String) {
        if (catalog().find(id) == null) {
            AudoraLog.w(TAG, "Download of unknown voice=$id ignored")
            return
        }
        val request = OneTimeWorkRequestBuilder<VoiceDownloadWorker>()
            .setInputData(workDataOf(VoiceDownloadWorker.KEY_VOICE_ID to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, config.retryBackoffMs, TimeUnit.MILLISECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager().enqueueUniqueWork(workName(id), ExistingWorkPolicy.KEEP, request)
        AudoraLog.i(TAG, "Download of voice=$id requested")
    }

    /** Stops a download and throws away what it received. */
    fun cancel(id: String) {
        workManager().cancelUniqueWork(workName(id))
        catalog().find(id)?.let { files.partialDownload(it).delete() }
        AudoraLog.i(TAG, "Download of voice=$id cancelled")
    }

    /** The state of every catalog voice's latest download; voices with none are absent. */
    fun states(): Flow<Map<String, DownloadState>> {
        val voices = catalog().voices
        if (voices.isEmpty()) return flowOf(emptyMap())
        val perVoice = voices.map { voice ->
            workManager().getWorkInfosForUniqueWorkFlow(workName(voice.id))
                .map { infos -> infos.lastOrNull()?.let(::downloadState) }
        }
        return combine(perVoice) { states ->
            voices.zip(states.toList()).mapNotNull { (voice, state) -> state?.let { voice.id to it } }.toMap()
        }
    }

    private fun downloadState(info: WorkInfo): DownloadState? = when (info.state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadState.Queued

        WorkInfo.State.RUNNING -> DownloadState.Running(
            info.progress.getLong(VoiceDownloadWorker.KEY_BYTES, 0),
            info.progress.getLong(VoiceDownloadWorker.KEY_TOTAL, 0),
        )

        WorkInfo.State.FAILED -> DownloadState.Failed(
            info.outputData.getString(VoiceDownloadWorker.KEY_MESSAGE) ?: "the download failed",
        )

        WorkInfo.State.SUCCEEDED, WorkInfo.State.CANCELLED -> null
    }

    private companion object {
        const val TAG = "Speech.Downloads"

        fun workName(id: String) = "voice-download:$id"
    }
}
