// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/acted/ActedVoices.kt
// Purpose: The acted Hindi voice's Android entry point (ADR-059): download, cancel, delete, state, the engine.

package io.github.skm0078.audora.speech.acted

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
import io.github.skm0078.audora.core.config.ActedConfig
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
import java.io.IOException
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
 * One per process. Downloading the voice is the opt-in, as for the punctuation model: once it is installed, studio
 * mode reads Hindi with it (and keys its audio by it, ADR-039); with none, nothing changes. Playback never renders
 * with it live.
 *
 * Network: only [download] uses it, through WorkManager, for the two URLs pinned in [ActedVoiceModel]. The parts
 * download one after the other in one job; the voice is installed once both are.
 */
class ActedVoices private constructor(
    context: Context,
    private val config: ActedConfig,
    private val downloads: VoiceDownloadConfig,
) {
    private val appContext = context.applicationContext
    internal val root = File(appContext.noBackupFilesDir, ROOT_DIR)

    // Each part in its own folder: installing a pin clears the other pins beside it.
    private val parts = ActedVoiceModel.PARTS.associateWith { part ->
        ZipModelFiles(
            File(root, part.id),
            part,
            // The parts are well over the voices' cap; each one's own size is pinned, with room for nothing more.
            maxOf(downloads.maxUnpackedBytes, part.sizeBytes),
            downloads.bufferBytes,
        )
    }
    private val totalBytes = ActedVoiceModel.PARTS.sumOf { it.sizeBytes }
    private val installedState = MutableStateFlow<Boolean?>(null)
    private val lock = Any()

    /** Installs and deletes under [lock]; a download started before a delete does not install. */
    private val gate = InstallGate(lock)

    /**
     * A new engine over the installed voice; each loads its own copy of the model on first render, so close it
     * ([ActedSpeechEngine.release]) when its run ends. Its model and tokenizer are read when it first renders.
     */
    fun engine(): ActedSpeechEngine = ActedSpeechEngine(::load, config)

    fun download() {
        val request = OneTimeWorkRequestBuilder<ActedVoiceDownloadWorker>()
            // About a gigabyte: never on mobile data.
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, downloads.retryBackoffMs, TimeUnit.MILLISECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager().enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        AudoraLog.i(TAG, "Acted Hindi voice download requested")
    }

    /** Stops a download; the partial file is kept for a later resume. */
    fun cancel() {
        workManager().cancelUniqueWork(WORK_NAME)
        AudoraLog.i(TAG, "Acted Hindi voice download cancelled")
    }

    /** Clears everything in any state, leftovers included. An engine already loaded keeps its copy until released. */
    suspend fun delete(): Outcome<Unit, String> = withContext(Dispatchers.IO) {
        workManager().cancelAndAwait(WORK_NAME, downloads.stopWaitMs)
        gate.delete {
            val clean = !root.exists() || root.deleteRecursively()
            refreshLocked()
            if (clean) Outcome.Success(Unit) else Outcome.Failure("could not delete every file of the acted voice")
        }
    }

    fun state(): Flow<OcrModelsState> {
        val work = workManager().getWorkInfosForUniqueWorkFlow(WORK_NAME).map { it.lastOrNull() }
        return combine(installedState, work) { installed, info ->
            downloadState(installed == true, info, totalBytes)
        }
            .onStart { isInstalled() }
            .flowOn(Dispatchers.IO)
    }

    /** Whether the voice is installed, without loading it; studio audio is keyed by it (ADR-039). */
    fun isInstalled(): Boolean = installedState.value ?: synchronized(lock) { refreshLocked() }

    /**
     * One attempt at every part not yet installed, in order; progress counts the whole voice. A part that fails or
     * must retry ends the attempt, and the next resumes from the bytes on disk.
     */
    internal fun runDownload(attempt: Int, onProgress: (Long, Long) -> Unit, keepGoing: () -> Boolean): JobResult {
        val downloader = ResumableDownloader(UrlConnectionHttpSource(downloads, USER_AGENT), downloads)
        var before = 0L
        for ((part, files) in parts) {
            val job = ZipModelDownloadJob(part, files, downloader, downloads, gate) {
                synchronized(lock) { refreshLocked() }
            }
            val done = before
            val result = job.run(attempt, { bytes, _ -> onProgress(done + bytes, totalBytes) }, keepGoing)
            if (result != JobResult.Installed) return result
            before += part.sizeBytes
        }
        return JobResult.Installed
    }

    /** The engine's loader: the model and tokenizer, or null with nothing installed. */
    private fun load(): Outcome<LoadedActed, String>? {
        if (!isInstalled()) return null
        return try {
            val tokenizer = appContext.assets.open(ChatterboxTokenizer.ASSET).reader(Charsets.UTF_8).use {
                ChatterboxTokenizer.read(it)
            }
            val model = ChatterboxNative.load(
                parts.getValue(ActedVoiceModel.CORE).modelDir,
                parts.getValue(ActedVoiceModel.LANGUAGE_MODEL).modelDir,
                config.threads,
            )
            Outcome.Success(LoadedActed(model, tokenizer))
        } catch (e: IOException) {
            Outcome.Failure("tokenizer unreadable: ${e.message}")
        } catch (e: IllegalStateException) {
            Outcome.Failure(e.message ?: "model would not load")
        } catch (e: UnsatisfiedLinkError) {
            Outcome.Failure("native library missing: ${e.message}")
        }
    }

    private fun refreshLocked(): Boolean = parts.values.all { it.isInstalled() }.also { installedState.value = it }

    private fun workManager(): WorkManager = WorkManager.getInstance(appContext)

    companion object {
        private const val TAG = "Speech.ActedVoices"
        private const val ROOT_DIR = "acted-voices"
        private const val WORK_NAME = "acted-voice-download"
        private const val USER_AGENT = "Audora (acted voice download)"

        @Volatile
        private var instance: ActedVoices? = null

        /** Whether this phone has the memory to render with the voice; Settings offers it only then. */
        fun fitsPhone(context: Context, config: ActedConfig = ActedConfig()): Boolean {
            val memory = ActivityManager.MemoryInfo()
            context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(memory) ?: return false
            return memory.totalMem >= config.minPhoneMemoryBytes
        }

        fun get(
            context: Context,
            config: ActedConfig = ActedConfig(),
            downloads: VoiceDownloadConfig = VoiceDownloadConfig(),
        ): ActedVoices = instance ?: synchronized(this) {
            instance ?: ActedVoices(context, config, downloads).also { instance = it }
        }
    }
}

/** Runs one download attempt over both parts; every run resumes from the bytes on disk. */
class ActedVoiceDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val result = ActedVoices.get(applicationContext).runDownload(
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
suspend fun ActedVoices.sizeOnDisk(): Long = withContext(Dispatchers.IO) {
    root.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
}

/** What Settings shows for the voice: installed, or where its download is. */
private fun downloadState(installed: Boolean, info: WorkInfo?, sizeBytes: Long): OcrModelsState = when {
    installed -> OcrModelsState.Ready(sizeBytes)

    info?.state == WorkInfo.State.ENQUEUED || info?.state == WorkInfo.State.BLOCKED -> OcrModelsState.Queued

    info?.state == WorkInfo.State.RUNNING -> OcrModelsState.Downloading(
        info.progress.getLong(ActedVoiceDownloadWorker.KEY_BYTES, 0L),
        sizeBytes,
    )

    info?.state == WorkInfo.State.FAILED -> OcrModelsState.Failed(
        info.outputData.getString(ActedVoiceDownloadWorker.KEY_MESSAGE) ?: "download failed",
    )

    else -> OcrModelsState.NotDownloaded(sizeBytes)
}
