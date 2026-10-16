// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/ocr/OcrModels.kt
// Purpose: The OCR model manager's Android entry point: catalog, installed models, download, cancel, delete, state.

package io.github.skm0078.audora.speech.ocr

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
import io.github.skm0078.audora.core.config.VoiceDownloadConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.voices.JobResult
import io.github.skm0078.audora.speech.voices.ResumableDownloader
import io.github.skm0078.audora.speech.voices.UrlConnectionHttpSource
import java.io.File
import java.io.IOException
import java.util.Properties
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

/** What Settings and the reader show about the OCR models. Sizes in bytes. */
sealed interface OcrModelsState {
    /** Some or all models are missing; [missingBytes] is what a download fetches. */
    data class NotDownloaded(val missingBytes: Long) : OcrModelsState

    data object Queued : OcrModelsState

    data class Downloading(val bytes: Long, val total: Long) : OcrModelsState

    data class Ready(val bytes: Long) : OcrModelsState

    data class Failed(val message: String) : OcrModelsState

    /** The APK's catalog is unreadable (a broken build); nothing can be downloaded. */
    data object Unavailable : OcrModelsState
}

/**
 * One per process, shared by the UI, the OCR engine (through the playback module) and the download worker, so a
 * model the worker installs is used by the next page at once. Construction touches no disk.
 *
 * Network: only [download] uses it, through WorkManager, and only for URLs from the catalog inside the APK.
 */
class OcrModels private constructor(context: Context, private val config: VoiceDownloadConfig) {
    private val appContext = context.applicationContext
    private val files = TessdataFiles(File(appContext.noBackupFilesDir, ROOT_DIR))
    private val installedState = MutableStateFlow<Set<Lang>?>(null)
    private val lock = Any()

    /** Null for a broken build (logged). */
    val catalog: TessdataCatalog? by lazy { readCatalog() }

    /** Tesseract's data path: the folder that holds `tessdata/`. */
    val dataRoot: File get() = files.root

    fun installed(): Set<Lang> = installedState.value ?: synchronized(lock) { refreshLocked() }

    fun download() {
        if (catalog == null) return
        val request = OneTimeWorkRequestBuilder<OcrModelDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, config.retryBackoffMs, TimeUnit.MILLISECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager().enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        AudoraLog.i(TAG, "OCR model download requested")
    }

    /** Stops a download; models already installed stay, the partial file is kept for a later resume. */
    fun cancel() {
        workManager().cancelUniqueWork(WORK_NAME)
        AudoraLog.i(TAG, "OCR model download cancelled")
    }

    suspend fun delete(): Outcome<Unit, String> = withContext(Dispatchers.IO) {
        workManager().cancelUniqueWork(WORK_NAME)
        synchronized(lock) { files.deleteAll().also { refreshLocked() } }
    }

    fun state(): Flow<OcrModelsState> {
        val catalog = catalog ?: return MutableStateFlow(OcrModelsState.Unavailable)
        val work = workManager().getWorkInfosForUniqueWorkFlow(WORK_NAME).map { it.lastOrNull() }
        return combine(installedState, work) { installed, info -> stateOf(catalog, installed.orEmpty(), info) }
            .onStart { installed() }
            .flowOn(Dispatchers.IO)
    }

    internal fun downloadJob(): OcrModelDownloadJob? = catalog?.let { catalog ->
        OcrModelDownloadJob(
            catalog = catalog,
            files = files,
            downloader = ResumableDownloader(UrlConnectionHttpSource(config, USER_AGENT), config),
            config = config,
            onInstalled = { synchronized(lock) { refreshLocked() } },
        )
    }

    private fun stateOf(catalog: TessdataCatalog, installed: Set<Lang>, info: WorkInfo?): OcrModelsState {
        val missing = catalog.models.filter { it.lang !in installed }.sumOf { it.sizeBytes }
        return when (info?.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> OcrModelsState.Queued

            WorkInfo.State.RUNNING -> OcrModelsState.Downloading(
                info.progress.getLong(OcrModelDownloadWorker.KEY_BYTES, catalog.totalBytes - missing),
                catalog.totalBytes,
            )

            WorkInfo.State.FAILED -> if (missing == 0L) {
                OcrModelsState.Ready(catalog.totalBytes)
            } else {
                OcrModelsState.Failed(
                    info.outputData.getString(OcrModelDownloadWorker.KEY_MESSAGE) ?: "download failed",
                )
            }

            else -> if (missing ==
                0L
            ) {
                OcrModelsState.Ready(catalog.totalBytes)
            } else {
                OcrModelsState.NotDownloaded(missing)
            }
        }
    }

    private fun refreshLocked(): Set<Lang> =
        (catalog?.let(files::installed) ?: emptySet()).also { installedState.value = it }

    private fun workManager(): WorkManager = WorkManager.getInstance(appContext)

    private fun readCatalog(): TessdataCatalog? {
        val parsed = try {
            appContext.assets.open(TessdataCatalog.ASSET_PATH).reader(Charsets.UTF_8).use { reader ->
                TessdataCatalogParser.parse(Properties().apply { load(reader) })
            }
        } catch (e: IOException) {
            Outcome.Failure("unreadable: ${e.message}")
        }
        return when (parsed) {
            is Outcome.Success -> parsed.value.also {
                AudoraLog.i(TAG, "OCR models ${it.revision}: ${it.models.map { m -> m.lang }}")
            }

            is Outcome.Failure -> null.also { AudoraLog.e(TAG, "OCR model catalog broken: ${parsed.error}") }
        }
    }

    companion object {
        private const val TAG = "Speech.OcrModels"
        private const val ROOT_DIR = "ocr"
        private const val WORK_NAME = "ocr-models-download"
        private const val USER_AGENT = "Audora (OCR model download)"

        @Volatile
        private var instance: OcrModels? = null

        fun get(context: Context, config: VoiceDownloadConfig = VoiceDownloadConfig()): OcrModels =
            instance ?: synchronized(this) {
                instance ?: OcrModels(context, config).also { instance = it }
            }
    }
}

/** Runs one [OcrModelDownloadJob] attempt; like the voice worker, every run resumes from the bytes on disk. */
class OcrModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val job = OcrModels.get(applicationContext).downloadJob()
            ?: return@withContext Result.failure(workDataOf(KEY_MESSAGE to "this build has no OCR model list"))
        val result = job.run(
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
