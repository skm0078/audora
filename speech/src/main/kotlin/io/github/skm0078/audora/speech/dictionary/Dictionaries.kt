// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/dictionary/Dictionaries.kt
// Purpose: The dictionaries' Android entry point (33, ADR-047): download, cancel, delete, state and the open ones.

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
import io.github.skm0078.audora.core.dictionary.BaseForms
import io.github.skm0078.audora.core.dictionary.Dictionary
import io.github.skm0078.audora.core.dictionary.EnglishBaseForms
import io.github.skm0078.audora.core.dictionary.FileByteSource
import io.github.skm0078.audora.core.dictionary.SortedLines
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Lang
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
 * One per process, shared by the reader and the download worker, like the punctuation model (RI-11): one dictionary
 * per language (English, Hindi, Bengali), each downloaded on its own as the opt-in, through WorkManager (AGENTS.md
 * keeps it for downloads), for the URL pinned in [DictionaryPin]. The state reuses the OCR models' type (missing,
 * queued, downloading, ready, failed). An open dictionary keeps one file handle and no table in memory.
 */
class Dictionaries private constructor(
    context: Context,
    private val config: DictionaryConfig,
    private val downloads: VoiceDownloadConfig,
) {
    private val appContext = context.applicationContext
    private val slots: Map<Lang, Slot> = Lang.entries.associateWith { Slot(it) }

    /** [lang]'s dictionary, opened on first use; null while it is not installed or cannot be opened (logged). */
    fun forLanguage(lang: Lang): Dictionary? = slots.getValue(lang).open()

    fun english(): Dictionary? = forLanguage(Lang.EN)

    fun download(lang: Lang = Lang.EN) {
        val request = OneTimeWorkRequestBuilder<DictionaryDownloadWorker>()
            .setInputData(workDataOf(DictionaryDownloadWorker.KEY_LANGUAGE to lang.bcp47Tag))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, downloads.retryBackoffMs, TimeUnit.MILLISECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager().enqueueUniqueWork(workName(lang), ExistingWorkPolicy.KEEP, request)
        AudoraLog.i(TAG, "Dictionary download requested, lang=${lang.bcp47Tag}")
    }

    /** Stops a download; the partial file is kept for a later resume. */
    fun cancel(lang: Lang = Lang.EN) {
        workManager().cancelUniqueWork(workName(lang))
        AudoraLog.i(TAG, "Dictionary download cancelled, lang=${lang.bcp47Tag}")
    }

    suspend fun delete(lang: Lang = Lang.EN): Outcome<Unit, String> = withContext(Dispatchers.IO) {
        workManager().cancelUniqueWork(workName(lang))
        slots.getValue(lang).deleteFiles()
    }

    fun state(lang: Lang = Lang.EN): Flow<OcrModelsState> {
        val slot = slots.getValue(lang)
        val work = workManager().getWorkInfosForUniqueWorkFlow(workName(lang)).map { it.lastOrNull() }
        return combine(slot.installed, work) { installed, info -> slot.stateOf(installed == true, info) }
            .onStart { slot.isInstalled() }
            .flowOn(Dispatchers.IO)
    }

    internal fun downloadJob(lang: Lang): DictionaryDownloadJob = slots.getValue(lang).let { slot ->
        DictionaryDownloadJob(
            pin = slot.pin,
            files = slot.files,
            downloader = ResumableDownloader(UrlConnectionHttpSource(downloads, USER_AGENT), downloads),
            config = downloads,
            onInstalled = { slot.refresh() },
        )
    }

    fun isInstalled(lang: Lang = Lang.EN): Boolean = slots.getValue(lang).isInstalled()

    private fun workManager(): WorkManager = WorkManager.getInstance(appContext)

    /** One language's pin, files and open dictionary. */
    private inner class Slot(private val lang: Lang) {
        val pin = DictionaryPin.of(lang)
        val files = DictionaryFiles(
            File(appContext.noBackupFilesDir, rootDir(lang)),
            pin,
            downloads.maxUnpackedBytes,
            downloads.bufferBytes,
        )
        val installed = MutableStateFlow<Boolean?>(null)
        private var source: FileByteSource? = null
        private var opened: Dictionary? = null

        fun open(): Dictionary? = synchronized(this) {
            if (opened == null && isInstalled()) {
                try {
                    val file = FileByteSource(files.text)
                    source = file
                    val forms = if (lang == Lang.EN) EnglishBaseForms() else BaseForms.AsWritten
                    opened = Dictionary(SortedLines(file, config), forms, config)
                } catch (e: IOException) {
                    AudoraLog.w(TAG, "Dictionary not opened, lang=${lang.bcp47Tag}: ${e.javaClass.simpleName}")
                }
            }
            opened
        }

        fun deleteFiles(): Outcome<Unit, String> = synchronized(this) {
            try {
                source?.close()
            } catch (e: IOException) {
                AudoraLog.w(TAG, "Dictionary file not closed: ${e.javaClass.simpleName}")
            }
            source = null
            opened = null
            files.deleteAll().also { refresh() }
        }

        fun isInstalled(): Boolean = installed.value ?: refresh()

        fun refresh(): Boolean = synchronized(this) { files.isInstalled().also { installed.value = it } }

        fun stateOf(installed: Boolean, info: WorkInfo?): OcrModelsState = when {
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
    }

    companion object {
        private const val TAG = "Speech.Dictionaries"
        private const val USER_AGENT = "Audora (dictionary download)"

        /** English keeps the folder and work name it had before Hindi and Bengali joined. */
        private fun rootDir(lang: Lang) = if (lang == Lang.EN) "dictionary" else "dictionary-${lang.bcp47Tag}"

        private fun workName(lang: Lang) =
            if (lang == Lang.EN) "dictionary-download" else "dictionary-download-${lang.bcp47Tag}"

        /** What a language's dictionary costs, shown before it is downloaded. */
        fun downloadBytes(lang: Lang = Lang.EN): Long = DictionaryPin.of(lang).sizeBytes

        fun unpackedBytes(lang: Lang = Lang.EN): Long = DictionaryPin.of(lang).unpackedBytes

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
        val tag = inputData.getString(KEY_LANGUAGE)
        val lang = Lang.entries.firstOrNull { it.bcp47Tag == tag } ?: Lang.EN
        val result = Dictionaries.get(applicationContext).downloadJob(lang).run(
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
        const val KEY_LANGUAGE = "language"
        const val KEY_BYTES = "bytes"
        const val KEY_TOTAL = "total"
        const val KEY_MESSAGE = "message"
    }
}
