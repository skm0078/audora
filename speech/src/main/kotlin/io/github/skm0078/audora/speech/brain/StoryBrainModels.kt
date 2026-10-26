// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/brain/StoryBrainModels.kt
// Purpose: The story brain's Android entry point (ADR 2026-10-25-story-brain): whether this phone can run it, download, cancel, delete,
//          state, and the loaded model.

package io.github.skm0078.audora.speech.brain

import android.app.ActivityManager
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
import io.github.skm0078.audora.core.config.StoryBrainConfig
import io.github.skm0078.audora.core.config.VoiceDownloadConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.ocr.OcrModelsState
import io.github.skm0078.audora.speech.voices.InstallGate
import io.github.skm0078.audora.speech.voices.JobResult
import io.github.skm0078.audora.speech.voices.ResumableDownloader
import io.github.skm0078.audora.speech.voices.UrlConnectionHttpSource
import io.github.skm0078.audora.speech.voices.cancelAndAwait
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * One per process, shared by Settings, the playback service's story brain and the download worker. Downloading is
 * the opt-in: with no model, reading is exactly as before. A phone with too little memory, or a build without the
 * brain's library (the emulator's), sees [OcrModelsState.Unavailable] and is never offered the download.
 *
 * Network: only [download] uses it, through WorkManager, for the URLs pinned in [StoryBrainModel].
 */
class StoryBrainModels private constructor(
    context: Context,
    val config: StoryBrainConfig,
    private val downloads: VoiceDownloadConfig,
) {
    private val appContext = context.applicationContext
    val model = StoryBrainModel.QWEN35_4B
    internal val files = StoryBrainFiles(File(appContext.noBackupFilesDir, ROOT_DIR), model, downloads.bufferBytes)
    private val installedState = MutableStateFlow<Boolean?>(null)
    private val lock = Any()

    /** Installs and deletes under [lock]; a download started before a delete does not install. */
    private val gate = InstallGate(lock)

    /** Whether this phone has the memory for the model and this build has its library. */
    val supported: Boolean by lazy {
        val memory = ActivityManager.MemoryInfo().also {
            appContext.getSystemService(ActivityManager::class.java)?.getMemoryInfo(it)
        }.totalMem
        val enough = memory >= config.minMemoryBytes
        if (!enough) AudoraLog.i(TAG, "Story brain not offered: ${memory / BYTES_PER_MB} MB of memory")
        enough && StoryBrainNative.available()
    }

    /**
     * Loads the installed model for one reader; null when none is installed, the phone is not [supported], or it
     * does not load (logged). The caller closes it: 2.7 GB stays mapped while it lives.
     */
    fun load(): BrainModel? {
        if (!supported || !isInstalled()) return null
        return StoryBrainNative.load(files.modelFile, config.contextTokens, config.threads).also { loaded ->
            if (loaded == null) AudoraLog.e(TAG, "Story brain ${model.id} did not load")
        }
    }

    fun download() {
        if (!supported) return
        val request = OneTimeWorkRequestBuilder<StoryBrainDownloadWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresStorageNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, downloads.retryBackoffMs, TimeUnit.MILLISECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager().enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        AudoraLog.i(TAG, "Story brain download requested")
    }

    /** Stops a download; the parts so far are kept for a later resume. */
    fun cancel() {
        workManager().cancelUniqueWork(WORK_NAME)
        AudoraLog.i(TAG, "Story brain download cancelled")
    }

    /**
     * Clears everything in any state, leftovers included. A reader holding the model keeps its mapping until it next
     * looks (before its next page) and lets it go.
     */
    suspend fun delete(): Outcome<Unit, String> = withContext(Dispatchers.IO) {
        workManager().cancelAndAwait(WORK_NAME, downloads.stopWaitMs)
        gate.delete { files.deleteAll().also { refreshLocked() } }
    }

    /** Whether this phone can run it is first asked here, off the main thread (it loads the native library). */
    fun state(): Flow<OcrModelsState> = flow {
        if (!supported) {
            emit(OcrModelsState.Unavailable)
        } else {
            val work = workManager().getWorkInfosForUniqueWorkFlow(WORK_NAME).map { it.lastOrNull() }
            isInstalled()
            emitAll(combine(installedState, work) { installed, info -> stateOf(installed == true, info) })
        }
    }.flowOn(Dispatchers.IO)

    internal fun downloadJob(): StoryBrainDownloadJob = StoryBrainDownloadJob(
        model = model,
        files = files,
        downloader = ResumableDownloader(UrlConnectionHttpSource(downloads, USER_AGENT), downloads),
        config = downloads,
        onInstalled = { synchronized(lock) { refreshLocked() } },
        gate = gate,
    )

    fun isInstalled(): Boolean = installedState.value ?: synchronized(lock) { refreshLocked() }

    private fun refreshLocked(): Boolean = files.isInstalled().also { installedState.value = it }

    private fun stateOf(installed: Boolean, info: WorkInfo?): OcrModelsState = when {
        installed -> OcrModelsState.Ready(model.sizeBytes)

        info?.state == WorkInfo.State.ENQUEUED || info?.state == WorkInfo.State.BLOCKED -> OcrModelsState.Queued

        info?.state == WorkInfo.State.RUNNING -> OcrModelsState.Downloading(
            info.progress.getLong(StoryBrainDownloadWorker.KEY_BYTES, 0L),
            model.sizeBytes,
        )

        info?.state == WorkInfo.State.FAILED -> OcrModelsState.Failed(
            info.outputData.getString(StoryBrainDownloadWorker.KEY_MESSAGE) ?: "download failed",
        )

        else -> OcrModelsState.NotDownloaded(model.sizeBytes)
    }

    private fun workManager(): WorkManager = WorkManager.getInstance(appContext)

    companion object {
        private const val TAG = "Speech.BrainModels"
        private const val ROOT_DIR = "story-brain"
        private const val WORK_NAME = "story-brain-download"
        private const val USER_AGENT = "Audora (story brain download)"
        private const val BYTES_PER_MB = 1_000_000L

        @Volatile
        private var instance: StoryBrainModels? = null

        fun get(
            context: Context,
            config: StoryBrainConfig = StoryBrainConfig(),
            downloads: VoiceDownloadConfig = VoiceDownloadConfig(),
        ): StoryBrainModels = instance ?: synchronized(this) {
            instance ?: StoryBrainModels(context, config, downloads).also { instance = it }
        }
    }
}

/** Runs one [StoryBrainDownloadJob] attempt; every run resumes from the bytes on disk. */
class StoryBrainDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val result = StoryBrainModels.get(applicationContext).downloadJob().run(
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
suspend fun StoryBrainModels.sizeOnDisk(): Long = withContext(Dispatchers.IO) { files.sizeOnDisk() }
