// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/kws/VoiceCommandModels.kt
// Purpose: The voice-command model's Android entry point (plan item 38): download, cancel, delete, state, a spotter.

package io.github.skm0078.audora.speech.kws

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.skm0078.audora.core.config.VoiceCommandConfig
import io.github.skm0078.audora.core.config.VoiceDownloadConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.ocr.OcrModelsState
import io.github.skm0078.audora.speech.voices.InstallGate
import io.github.skm0078.audora.speech.voices.JobResult
import io.github.skm0078.audora.speech.voices.ResumableDownloader
import io.github.skm0078.audora.speech.voices.UrlConnectionHttpSource
import io.github.skm0078.audora.speech.voices.ZipModelDownloadJob
import io.github.skm0078.audora.speech.voices.ZipModelFiles
import io.github.skm0078.audora.speech.voices.cancelAndAwait
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

/**
 * One per process, shared by Settings, the reader's listener and the download worker. Downloading the model is
 * only half the opt-in: the listener also needs Settings' switch and the microphone permission. Unlike the
 * punctuation model, nothing here stays loaded: each listening spell loads its own [spotter] and releases it, so a
 * reader closed is memory given back.
 *
 * Network: only [download] uses it, through WorkManager, for the URL pinned in [KeywordModel].
 */
class VoiceCommandModels private constructor(context: Context, private val downloads: VoiceDownloadConfig) {
    private val appContext = context.applicationContext
    private val model = KeywordModel.ENGLISH
    internal val files = ZipModelFiles(
        File(appContext.noBackupFilesDir, ROOT_DIR),
        model,
        downloads.maxUnpackedBytes,
        downloads.bufferBytes,
    )
    private val installedState = MutableStateFlow<Boolean?>(null)
    private val lock = Any()

    /** Installs and deletes under [lock]; a download started before a delete does not install. */
    private val gate = InstallGate(lock)

    /** A new spotter for one listening spell, which the caller releases; null with no model or a damaged one. */
    fun spotter(config: VoiceCommandConfig): CommandSpotter? = synchronized(lock) {
        if (isInstalled()) CommandSpotter.load(files.modelDir, model, config) else null
    }

    fun download() {
        val request = OneTimeWorkRequestBuilder<VoiceCommandDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, downloads.retryBackoffMs, TimeUnit.MILLISECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager().enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        AudoraLog.i(TAG, "Voice-command model download requested")
    }

    /** Stops a download; the partial file is kept for a later resume. */
    fun cancel() {
        workManager().cancelUniqueWork(WORK_NAME)
        AudoraLog.i(TAG, "Voice-command model download cancelled")
    }

    /**
     * Clears everything in any state, leftovers included. A spotter already loaded keeps its weights in memory
     * until its spell ends; the listener sees [isInstalled] false and does not start another.
     */
    suspend fun delete(): Outcome<Unit, String> = withContext(Dispatchers.IO) {
        workManager().cancelAndAwait(WORK_NAME, downloads.stopWaitMs)
        gate.delete { files.deleteAll().also { refreshLocked() } }
    }

    fun state(): Flow<OcrModelsState> {
        val work = workManager().getWorkInfosForUniqueWorkFlow(WORK_NAME).map { it.lastOrNull() }
        return combine(installedState, work) { installed, info -> stateOf(installed == true, info, model.sizeBytes) }
            .onStart { isInstalled() }
            .flowOn(Dispatchers.IO)
    }

    /** True once the model is installed; a flow for the reader, which listens only then. */
    fun installed(): Flow<Boolean> = installedState.map { it == true }.onStart { isInstalled() }.flowOn(Dispatchers.IO)

    internal fun downloadJob(): ZipModelDownloadJob = ZipModelDownloadJob(
        model = model,
        files = files,
        downloader = ResumableDownloader(UrlConnectionHttpSource(downloads, USER_AGENT), downloads),
        config = downloads,
        onInstalled = { synchronized(lock) { refreshLocked() } },
        gate = gate,
    )

    fun isInstalled(): Boolean = installedState.value ?: synchronized(lock) { refreshLocked() }

    private fun refreshLocked(): Boolean = files.isInstalled().also { installedState.value = it }

    private fun workManager(): WorkManager = WorkManager.getInstance(appContext)

    companion object {
        private const val TAG = "Speech.CommandModels"
        private const val ROOT_DIR = "voice-commands"
        private const val WORK_NAME = "voice-command-model-download"
        private const val USER_AGENT = "Audora (voice-command model download)"

        @Volatile
        private var instance: VoiceCommandModels? = null

        fun get(context: Context, downloads: VoiceDownloadConfig = VoiceDownloadConfig()): VoiceCommandModels =
            instance ?: synchronized(this) {
                instance ?: VoiceCommandModels(context, downloads).also { instance = it }
            }
    }
}

/** Runs one [ZipModelDownloadJob] attempt; every run resumes from the bytes on disk. */
class VoiceCommandDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val result = VoiceCommandModels.get(applicationContext).downloadJob().run(
            attempt = runAttemptCount,
            onProgress = { bytes, total -> setProgressAsync(workDataOf(KEY_BYTES to bytes, KEY_TOTAL to total)) },
            keepGoing = { !isStopped },
        )
        when (result) {
            JobResult.Installed -> Result.success()
            is JobResult.Retry -> Result.retry()
            is JobResult.Failed -> Result.failure(workDataOf(KEY_MESSAGE to result.message))
        }
    }

    companion object {
        const val KEY_BYTES = "bytes"
        const val KEY_TOTAL = "total"
        const val KEY_MESSAGE = "message"
    }
}

private fun stateOf(installed: Boolean, info: WorkInfo?, sizeBytes: Long): OcrModelsState = when {
    installed -> OcrModelsState.Ready(sizeBytes)

    info?.state == WorkInfo.State.ENQUEUED || info?.state == WorkInfo.State.BLOCKED -> OcrModelsState.Queued

    info?.state == WorkInfo.State.RUNNING -> OcrModelsState.Downloading(
        info.progress.getLong(VoiceCommandDownloadWorker.KEY_BYTES, 0L),
        sizeBytes,
    )

    info?.state == WorkInfo.State.FAILED -> OcrModelsState.Failed(
        info.outputData.getString(VoiceCommandDownloadWorker.KEY_MESSAGE) ?: "download failed",
    )

    else -> OcrModelsState.NotDownloaded(sizeBytes)
}

/** Bytes on disk in any state, a cancelled or failed download's leftovers included (the Storage screen). */
suspend fun VoiceCommandModels.sizeOnDisk(): Long = withContext(Dispatchers.IO) { files.sizeOnDisk() }
