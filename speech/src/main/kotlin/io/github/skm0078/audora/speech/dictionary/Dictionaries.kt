// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/dictionary/Dictionaries.kt
// Purpose: The dictionary's Android entry point (item 33): download, cancel, delete, state and the open dictionary.

package io.github.skm0078.audora.speech.dictionary

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
import io.github.skm0078.audora.core.config.DictionaryConfig
import io.github.skm0078.audora.core.config.VoiceDownloadConfig
import io.github.skm0078.audora.core.dictionary.Dictionary
import io.github.skm0078.audora.core.dictionary.EnglishBaseForms
import io.github.skm0078.audora.core.dictionary.FileByteSource
import io.github.skm0078.audora.core.dictionary.SortedLines
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.ocr.OcrModelsState
import io.github.skm0078.audora.speech.voices.JobResult
import io.github.skm0078.audora.speech.voices.ResumableDownloader
import io.github.skm0078.audora.speech.voices.UrlConnectionHttpSource
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
 * One per process, shared by the reader and the download worker, like the punctuation model (RI-11): downloading is
 * the opt-in, through WorkManager (AGENTS.md keeps it for downloads), for the URL pinned in [DictionaryPin]. The
 * state reuses the OCR models' type (missing, queued, downloading, ready, failed). The open dictionary keeps one file
 * handle and no table in memory.
 */
class Dictionaries private constructor(
    context: Context,
    private val config: DictionaryConfig,
    private val downloads: VoiceDownloadConfig,
) {
    private val appContext = context.applicationContext
    private val pin = DictionaryPin.ENGLISH
    private val files = DictionaryFiles(
        File(appContext.noBackupFilesDir, ROOT_DIR),
        pin,
        downloads.maxUnpackedBytes,
        downloads.bufferBytes,
    )
    private val installedState = MutableStateFlow<Boolean?>(null)
    private val lock = Any()
    private var source: FileByteSource? = null
    private var opened: Dictionary? = null

    /** The English dictionary, opened on first use; null while it is not installed or cannot be opened (logged). */
    fun english(): Dictionary? = synchronized(lock) {
        if (opened == null && isInstalled()) {
            try {
                val file = FileByteSource(files.text)
                source = file
                opened = Dictionary(SortedLines(file, config), EnglishBaseForms(), config)
            } catch (e: IOException) {
                AudoraLog.w(TAG, "Dictionary could not be opened: ${e.javaClass.simpleName}")
            }
        }
        opened
    }

    fun download() {
        val request = OneTimeWorkRequestBuilder<DictionaryDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, downloads.retryBackoffMs, TimeUnit.MILLISECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager().enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        AudoraLog.i(TAG, "Dictionary download requested")
    }

    /** Stops a download; the partial file is kept for a later resume. */
    fun cancel() {
        workManager().cancelUniqueWork(WORK_NAME)
        AudoraLog.i(TAG, "Dictionary download cancelled")
    }

    suspend fun delete(): Outcome<Unit, String> = withContext(Dispatchers.IO) {
        workManager().cancelUniqueWork(WORK_NAME)
        synchronized(lock) {
            try {
                source?.close()
            } catch (e: IOException) {
                AudoraLog.w(TAG, "Dictionary file not closed: ${e.javaClass.simpleName}")
            }
            source = null
            opened = null
            files.deleteAll().also { refreshLocked() }
        }
    }

    fun state(): Flow<OcrModelsState> {
        val work = workManager().getWorkInfosForUniqueWorkFlow(WORK_NAME).map { it.lastOrNull() }
        return combine(installedState, work) { installed, info -> stateOf(installed == true, info) }
            .onStart { isInstalled() }
            .flowOn(Dispatchers.IO)
    }

    internal fun downloadJob(): DictionaryDownloadJob = DictionaryDownloadJob(
        pin = pin,
        files = files,
        downloader = ResumableDownloader(UrlConnectionHttpSource(downloads, USER_AGENT), downloads),
        config = downloads,
        onInstalled = { synchronized(lock) { refreshLocked() } },
    )

    fun isInstalled(): Boolean = installedState.value ?: synchronized(lock) { refreshLocked() }

    private fun refreshLocked(): Boolean = files.isInstalled().also { installedState.value = it }

    private fun stateOf(installed: Boolean, info: WorkInfo?): OcrModelsState = when {
        installed -> OcrModelsState.Ready(pin.unpackedBytes)

        info?.state == WorkInfo.State.ENQUEUED || info?.state == WorkInfo.State.BLOCKED -> OcrModelsState.Queued

        info?.state == WorkInfo.State.RUNNING -> OcrModelsState.Downloading(
            info.progress.getLong(DictionaryDownloadWorker.KEY_BYTES, 0L),
            pin.sizeBytes,
        )

        info?.state == WorkInfo.State.FAILED -> OcrModelsState.Failed(
            info.outputData.getString(DictionaryDownloadWorker.KEY_MESSAGE) ?: "download failed",
        )

        else -> OcrModelsState.NotDownloaded(pin.sizeBytes)
    }

    private fun workManager(): WorkManager = WorkManager.getInstance(appContext)

    companion object {
        private const val TAG = "Speech.Dictionaries"
        private const val ROOT_DIR = "dictionary"
        private const val WORK_NAME = "dictionary-download"
        private const val USER_AGENT = "Audora (dictionary download)"

        /** What the download costs, shown before it starts. */
        val downloadBytes: Long get() = DictionaryPin.ENGLISH.sizeBytes
        val unpackedBytes: Long get() = DictionaryPin.ENGLISH.unpackedBytes

        @Volatile
        private var instance: Dictionaries? = null

        fun get(
            context: Context,
            config: DictionaryConfig = DictionaryConfig(),
            downloads: VoiceDownloadConfig = VoiceDownloadConfig(),
        ): Dictionaries = instance ?: synchronized(this) {
            instance ?: Dictionaries(context, config, downloads).also { instance = it }
        }
    }
}

/** Runs one [DictionaryDownloadJob] attempt; every run resumes from the bytes on disk. */
class DictionaryDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val result = Dictionaries.get(applicationContext).downloadJob().run(
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
