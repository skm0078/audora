// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/punct/PunctuationModels.kt
// Purpose: The punctuation model's Android entry point (RI-11): download, cancel, delete, state, the loaded model.

package io.github.skm0078.audora.speech.punct

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
import io.github.skm0078.audora.core.config.PunctuationConfig
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
 * One per process, shared by Settings, the renderer (through the playback module) and the download worker, so the
 * model the worker installs is used from the next run-on. Downloading it is the opt-in: with no model, nothing
 * changes. The state reuses the OCR models' type, which says the same things (missing, queued, downloading, ready).
 *
 * Network: only [download] uses it, through WorkManager, for the URL pinned in [PunctuationModel].
 */
class PunctuationModels private constructor(
    context: Context,
    private val config: PunctuationConfig,
    private val downloads: VoiceDownloadConfig,
) {
    private val appContext = context.applicationContext
    private val model = PunctuationModel.ENGLISH
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
    private var loaded: SherpaPunctuator? = null
    private var loadFailed = false

    /** The model, loaded on first use; null while none is installed or when it would not load (logged once). */
    fun punctuator(): Punctuator? = synchronized(lock) {
        if (loaded == null && !loadFailed && isInstalled()) {
            loaded = SherpaPunctuator.load(files.modelDir, model, config.numThreads)
            loadFailed = loaded == null
        }
        loaded
    }

    fun download() {
        val request = OneTimeWorkRequestBuilder<PunctuationDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, downloads.retryBackoffMs, TimeUnit.MILLISECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager().enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        AudoraLog.i(TAG, "Punctuation model download requested")
    }

    /** Stops a download; the partial file is kept for a later resume. */
    fun cancel() {
        workManager().cancelUniqueWork(WORK_NAME)
        AudoraLog.i(TAG, "Punctuation model download cancelled")
    }

    /** Clears everything in any state, leftovers included. */
    suspend fun delete(): Outcome<Unit, String> = withContext(Dispatchers.IO) {
        workManager().cancelAndAwait(WORK_NAME, downloads.stopWaitMs)
        gate.delete {
            loaded?.release()
            loaded = null
            loadFailed = false
            files.deleteAll().also { refreshLocked() }
        }
    }

    fun state(): Flow<OcrModelsState> {
        val work = workManager().getWorkInfosForUniqueWorkFlow(WORK_NAME).map { it.lastOrNull() }
        return combine(installedState, work) { installed, info -> stateOf(installed == true, info) }
            .onStart { isInstalled() }
            .flowOn(Dispatchers.IO)
    }

    internal fun downloadJob(): ZipModelDownloadJob = ZipModelDownloadJob(
        model = model,
        files = files,
        downloader = ResumableDownloader(UrlConnectionHttpSource(downloads, USER_AGENT), downloads),
        config = downloads,
        onInstalled = {
            synchronized(lock) {
                loadFailed = false
                refreshLocked()
            }
        },
        gate = gate,
    )

    /** Whether the model is installed, without loading it; studio audio is keyed by it (ADR-039). */
    fun isInstalled(): Boolean = installedState.value ?: synchronized(lock) { refreshLocked() }

    private fun refreshLocked(): Boolean = files.isInstalled().also { installedState.value = it }

    private fun stateOf(installed: Boolean, info: WorkInfo?): OcrModelsState = when {
        installed -> OcrModelsState.Ready(model.sizeBytes)

        info?.state == WorkInfo.State.ENQUEUED || info?.state == WorkInfo.State.BLOCKED -> OcrModelsState.Queued

        info?.state == WorkInfo.State.RUNNING -> OcrModelsState.Downloading(
            info.progress.getLong(PunctuationDownloadWorker.KEY_BYTES, 0L),
            model.sizeBytes,
        )

        info?.state == WorkInfo.State.FAILED -> OcrModelsState.Failed(
            info.outputData.getString(PunctuationDownloadWorker.KEY_MESSAGE) ?: "download failed",
        )

        else -> OcrModelsState.NotDownloaded(model.sizeBytes)
    }

    private fun workManager(): WorkManager = WorkManager.getInstance(appContext)

    companion object {
        private const val TAG = "Speech.PunctModels"
        private const val ROOT_DIR = "punctuation"
        private const val WORK_NAME = "punctuation-model-download"
        private const val USER_AGENT = "Audora (punctuation model download)"

        @Volatile
        private var instance: PunctuationModels? = null

        fun get(
            context: Context,
            config: PunctuationConfig = PunctuationConfig(),
            downloads: VoiceDownloadConfig = VoiceDownloadConfig(),
        ): PunctuationModels = instance ?: synchronized(this) {
            instance ?: PunctuationModels(context, config, downloads).also { instance = it }
        }
    }
}

/** Runs one [ZipModelDownloadJob] attempt; every run resumes from the bytes on disk. */
class PunctuationDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val result = PunctuationModels.get(applicationContext).downloadJob().run(
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

/** Bytes on disk in any state, a cancelled or failed download's leftovers included (the Storage screen). */
suspend fun PunctuationModels.sizeOnDisk(): Long = withContext(Dispatchers.IO) { files.sizeOnDisk() }
