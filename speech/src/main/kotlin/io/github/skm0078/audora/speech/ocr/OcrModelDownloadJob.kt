// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/ocr/OcrModelDownloadJob.kt
// Purpose: One background attempt at downloading every missing OCR model, and whether to try again.

package io.github.skm0078.audora.speech.ocr

import io.github.skm0078.audora.core.config.VoiceDownloadConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.voices.DownloadError
import io.github.skm0078.audora.speech.voices.InstallGate
import io.github.skm0078.audora.speech.voices.JobResult
import io.github.skm0078.audora.speech.voices.ResumableDownloader
import java.io.File

/**
 * The OCR models download as one pack (English, Hindi and Bengali, about 38 MB): a scanned book's language is only
 * known once it is read, and the language probe needs every model (ADR-025). Models are fetched one after another,
 * each resumable and SHA-256-checked by the voice downloader (ADR-022); a model already installed is skipped, so a
 * retry continues where the last run stopped. Same retry rules as voices.
 *
 * @param onInstalled called after each model is swapped in, so OCR and the Settings row see it at once.
 * @param gate the models' install lock: models deleted while the pack downloaded are not installed after all.
 */
class OcrModelDownloadJob(
    private val catalog: TessdataCatalog,
    private val files: TessdataFiles,
    private val downloader: ResumableDownloader,
    private val config: VoiceDownloadConfig,
    private val gate: InstallGate = InstallGate(),
    private val onInstalled: () -> Unit,
) {
    /**
     * @param attempt WorkManager's run attempt count, 0 for the first run.
     * @param onProgress bytes of the whole pack on disk (installed models count in full) and the pack's size.
     */
    fun run(attempt: Int, onProgress: (Long, Long) -> Unit, keepGoing: () -> Boolean): JobResult {
        val missing = catalog.models.filterNot(files::isInstalled)
        val ticket = gate.ticket()
        AudoraLog.i(TAG, "OCR models to download ${missing.map { it.fileName }} attempt=${attempt + 1}")
        for (model in missing) {
            val failure = fetch(model, attempt, Progress(onProgress, keepGoing, ticket))
            if (failure != null) return failure
        }
        return JobResult.Installed
    }

    /** Downloads and installs one model; null when it is installed, else what the job should report. */
    private fun fetch(model: OcrModel, attempt: Int, run: Progress): JobResult? {
        val before = files.installedBytes(catalog)
        val downloaded = downloader.download(
            model,
            files.partialDownload(model),
            { bytes, _ -> run.onProgress(before + bytes, catalog.totalBytes) },
            run.keepGoing,
        )
        return when (downloaded) {
            is Outcome.Failure -> failed(model, attempt, downloaded.error)

            is Outcome.Success -> when (val installed = install(downloaded.value, model, run)) {
                null -> JobResult.Failed(DELETED).also { AudoraLog.i(TAG, "OCR models not installed: $DELETED") }

                is Outcome.Failure -> JobResult.Failed(installed.error)

                is Outcome.Success -> {
                    onInstalled()
                    null
                }
            }
        }
    }

    private fun install(download: File, model: OcrModel, run: Progress): Outcome<Unit, String>? =
        if (run.keepGoing()) gate.install(run.ticket) { files.install(download, model) } else null

    /** One run's callbacks and its install ticket ([InstallGate.ticket]). */
    private class Progress(val onProgress: (Long, Long) -> Unit, val keepGoing: () -> Boolean, val ticket: Long)

    private fun failed(model: OcrModel, attempt: Int, error: DownloadError): JobResult {
        val lastAttempt = attempt + 1 >= config.maxAttempts
        val result = when {
            error == DownloadError.Stopped -> JobResult.Retry(error.message)
            !error.retryable -> JobResult.Failed(error.message)
            lastAttempt -> JobResult.Failed("${error.message} (gave up after ${config.maxAttempts} tries)")
            else -> JobResult.Retry(error.message)
        }
        when (result) {
            is JobResult.Failed -> AudoraLog.e(TAG, "OCR model ${model.fileName} failed: ${result.message}")
            else -> AudoraLog.w(TAG, "OCR model ${model.fileName} will retry: ${error.message}")
        }
        return result
    }

    private companion object {
        const val TAG = "Speech.OcrJob"
        const val DELETED = "stopped or deleted while downloading"
    }
}
