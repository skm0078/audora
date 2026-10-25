// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/listen/ListenModels.kt
// Purpose: The reading tutor's speech models' Android entry point (67, ADR-050): download, cancel, delete, state.

package io.github.skm0078.audora.speech.listen

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
import io.github.skm0078.audora.core.config.TutorConfig
import io.github.skm0078.audora.core.config.VoiceDownloadConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.download.PinnedZipFiles
import io.github.skm0078.audora.speech.download.PinnedZipJob
import io.github.skm0078.audora.speech.download.awaitNoDownload
import io.github.skm0078.audora.speech.download.forItem
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
 * One per process, like the dictionaries: one opt-in speech model per language, downloaded through WorkManager
 * (AGENTS.md keeps it for downloads) from the URL pinned in [ListenPin], into the no-backup folder. The state reuses
 * the OCR models' type (missing, queued, downloading, ready, failed). [listener] opens the installed model.
 */
class ListenModels private constructor(
    context: Context,
    private val downloads: VoiceDownloadConfig,
    private val config: TutorConfig,
) {
    private val appContext = context.applicationContext
    private val files: Map<Lang, PinnedZipFiles> = Lang.entries.associateWith { lang ->
        val pin = ListenPin.of(lang)
        listenFiles(
            File(appContext.noBackupFilesDir, rootDir(lang)),
            pin,
            downloads.maxUnpackedBytes,
            downloads.bufferBytes,
        )
    }

    /** Each language's folder: the installed model, older pins and partial downloads. */
    internal val roots: Map<Lang, File> get() = files.mapValues { it.value.root }
    private val installed: Map<Lang, MutableStateFlow<Boolean?>> = Lang.entries.associateWith { MutableStateFlow(null) }

    fun isInstalled(lang: Lang): Boolean = installed.getValue(lang).value ?: refresh(lang)

    /**
     * A listener over [lang]'s installed model, or null when it is not installed or will not load (logged). The
     * caller releases it; loading takes a second or two and roughly 150-250 MB of memory while listening (ADR-050).
     *
     * sherpa-onnx 1.13.8 aborts the process on some damaged models instead of throwing, so a `loading` file marks a
     * load under way; one still there means the last load never returned, and the model is reported as failed
     * (null) without trying again, unless [retry] (the reader chose *Try again*).
     */
    fun listener(lang: Lang, retry: Boolean = false): Listener? {
        val dir = files.getValue(lang).dir
        val loading = File(dir, LOADING_MARKER)
        return when {
            !isInstalled(lang) -> null

            loading.exists() && !retry -> null.also {
                AudoraLog.w(TAG, "Speech model did not finish loading last time, lang=${lang.bcp47Tag}")
            }

            else -> {
                markLoading(loading)
                try {
                    when (ListenPin.of(lang).kind) {
                        ListenKind.STREAMING_TRANSDUCER -> SherpaStreamingListener.open(dir, config)
                        ListenKind.PHRASE_CTC -> SherpaPhraseListener.open(dir, config)
                    }?.let { ScriptFilteredListener(it, lang) }
                } finally {
                    if (!loading.delete()) AudoraLog.w(TAG, "Loading marker not deleted, lang=${lang.bcp47Tag}")
                }
            }
        }
    }

    fun download(lang: Lang) {
        val request = OneTimeWorkRequestBuilder<ListenDownloadWorker>()
            .setInputData(workDataOf(ListenDownloadWorker.KEY_LANGUAGE to lang.bcp47Tag))
            .forItem(lang.bcp47Tag)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, downloads.retryBackoffMs, TimeUnit.MILLISECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager().enqueueUniqueWork(workName(lang), ExistingWorkPolicy.KEEP, request)
        AudoraLog.i(TAG, "Speech model download requested, lang=${lang.bcp47Tag}")
    }

    /** Stops a download; the partial file is kept for a later resume. */
    fun cancel(lang: Lang) {
        workManager().cancelUniqueWork(workName(lang))
        AudoraLog.i(TAG, "Speech model download cancelled, lang=${lang.bcp47Tag}")
    }

    /** Cancels a download, waits for it to stop (up to `TutorConfig.cancelWaitMs`), then deletes the model. */
    suspend fun delete(lang: Lang): Outcome<Unit, String> = withContext(Dispatchers.IO) {
        workManager().cancelUniqueWork(workName(lang))
        if (!files.getValue(lang).awaitNoDownload(config.cancelWaitMs)) {
            AudoraLog.w(TAG, "Speech model download still stopping, lang=${lang.bcp47Tag}")
        }
        files.getValue(lang).deleteAll().also { refresh(lang) }
    }

    /** A model that will not load (damaged files): deleted, then downloaded again. */
    suspend fun reinstall(lang: Lang) {
        AudoraLog.i(TAG, "Speech model reinstall, lang=${lang.bcp47Tag}")
        if (delete(lang) is Outcome.Success) download(lang)
    }

    fun state(lang: Lang): Flow<OcrModelsState> {
        val pin = ListenPin.of(lang)
        val work = workManager().getWorkInfosForUniqueWorkFlow(workName(lang)).map { it.lastOrNull() }
        return combine(installed.getValue(lang), work) { ready, info -> listenState(pin, ready == true, info) }
            .onStart { refresh(lang) }
            .flowOn(Dispatchers.IO)
    }

    internal fun downloadJob(lang: Lang): PinnedZipJob = PinnedZipJob(
        pin = ListenPin.of(lang),
        files = files.getValue(lang),
        downloader = ResumableDownloader(UrlConnectionHttpSource(downloads, USER_AGENT), downloads),
        config = downloads,
        onInstalled = { refresh(lang) },
    )

    private fun refresh(lang: Lang): Boolean =
        files.getValue(lang).isInstalled().also { installed.getValue(lang).value = it }

    private fun workManager(): WorkManager = WorkManager.getInstance(appContext)

    companion object {
        private const val TAG = "Speech.Listen"
        private const val USER_AGENT = "Audora (speech model download)"
        private const val LOADING_MARKER = "loading"

        private fun rootDir(lang: Lang) = "listen-${lang.bcp47Tag}"

        private fun workName(lang: Lang) = "listen-download-${lang.bcp47Tag}"

        @Volatile
        private var instance: ListenModels? = null

        fun get(
            context: Context,
            downloads: VoiceDownloadConfig = VoiceDownloadConfig(),
            config: TutorConfig = TutorConfig(),
        ): ListenModels = instance ?: synchronized(this) {
            instance ?: ListenModels(context, downloads, config).also { instance = it }
        }
    }
}

/** A model's state from whether it is installed and its download work's latest info. */
private fun listenState(pin: ListenPin, ready: Boolean, info: WorkInfo?): OcrModelsState = when {
    ready -> OcrModelsState.Ready(pin.unpackedBytes)

    info?.state == WorkInfo.State.ENQUEUED || info?.state == WorkInfo.State.BLOCKED -> OcrModelsState.Queued

    info?.state == WorkInfo.State.RUNNING -> OcrModelsState.Downloading(
        info.progress.getLong(ListenDownloadWorker.KEY_BYTES, 0L),
        pin.sizeBytes,
    )

    info?.state == WorkInfo.State.FAILED -> OcrModelsState.Failed(
        info.outputData.getString(ListenDownloadWorker.KEY_MESSAGE) ?: "download failed",
    )

    else -> OcrModelsState.NotDownloaded(pin.sizeBytes)
}

/** Runs one [PinnedZipJob] attempt for a speech model; every run resumes from the bytes on disk. */
class ListenDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val tag = inputData.getString(KEY_LANGUAGE)
        val lang = Lang.entries.firstOrNull { it.bcp47Tag == tag }
        val result = if (lang == null) {
            AudoraLog.e(TAG, "Speech model download for an unknown language tag")
            JobResult.Failed("unknown language")
        } else {
            ListenModels.get(applicationContext).downloadJob(lang).run(
                attempt = runAttemptCount,
                onProgress = { bytes, total -> setProgressAsync(workDataOf(KEY_BYTES to bytes, KEY_TOTAL to total)) },
                keepGoing = { !isStopped },
            )
        }
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
        private const val TAG = "Speech.Listen"
    }
}

/** Writes the `loading` marker; a failure is logged and the model still loads (only the abort guard is lost). */
private fun markLoading(marker: File) {
    try {
        if (!marker.exists() && !marker.createNewFile()) AudoraLog.w(MARKER_TAG, "Loading marker not written")
    } catch (e: IOException) {
        AudoraLog.w(MARKER_TAG, "Loading marker not written: ${e.javaClass.simpleName}")
    }
}

private const val MARKER_TAG = "Speech.Listen"
