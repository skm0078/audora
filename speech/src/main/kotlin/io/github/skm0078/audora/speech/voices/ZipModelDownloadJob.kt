// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/voices/ZipModelDownloadJob.kt
// Purpose: One background attempt at downloading and installing a zip model, and whether to try again.

package io.github.skm0078.audora.speech.voices

import io.github.skm0078.audora.core.config.VoiceDownloadConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import java.io.File

/**
 * Resumable and SHA-256-checked by the voice downloader (ADR-022), with the voices' retry rules: a stop or network
 * trouble keeps the bytes and retries, a corrupt file or a full phone ends the download.
 *
 * @param onInstalled called once the model is in place, so the next use finds it.
 * @param gate the models' install lock: a model deleted while it downloaded is not installed after all.
 */
class ZipModelDownloadJob(
    private val model: ZipModel,
    private val files: ZipModelFiles,
    private val downloader: ResumableDownloader,
    private val config: VoiceDownloadConfig,
    private val gate: InstallGate = InstallGate(),
    private val onInstalled: () -> Unit,
) {
    /** @param attempt WorkManager's run attempt count, 0 for the first run. */
    fun run(attempt: Int, onProgress: (Long, Long) -> Unit, keepGoing: () -> Boolean): JobResult {
        if (files.isInstalled()) return JobResult.Installed
        val ticket = gate.ticket()
        AudoraLog.i(TAG, "Download model=${model.id} attempt=${attempt + 1}/${config.maxAttempts}")
        return when (val downloaded = downloader.download(model, files.partialDownload(), onProgress, keepGoing)) {
            is Outcome.Failure -> failed(attempt, downloaded.error)
            is Outcome.Success -> install(downloaded.value, ticket, keepGoing)
        }
    }

    private fun install(download: File, ticket: Long, keepGoing: () -> Boolean): JobResult {
        val installed = if (keepGoing()) gate.install(ticket) { files.install(download) } else null
        return when (installed) {
            null -> JobResult.Failed(DELETED).also { AudoraLog.i(TAG, "Model ${model.id} not installed: $DELETED") }
            is Outcome.Failure -> JobResult.Failed(installed.error)
            is Outcome.Success -> JobResult.Installed.also { onInstalled() }
        }
    }

    private fun failed(attempt: Int, error: DownloadError): JobResult {
        val result = when {
            error == DownloadError.Stopped -> JobResult.Retry(error.message)

            !error.retryable -> JobResult.Failed(error.message)

            attempt + 1 >= config.maxAttempts -> JobResult.Failed(
                "${error.message} (gave up after ${config.maxAttempts} tries)",
            )

            else -> JobResult.Retry(error.message)
        }
        when (result) {
            is JobResult.Failed -> AudoraLog.e(TAG, "Model ${model.id} failed: ${result.message}")
            else -> AudoraLog.w(TAG, "Model ${model.id} will retry: ${error.message}")
        }
        return result
    }

    private companion object {
        const val TAG = "Speech.ModelJob"
        const val DELETED = "stopped or deleted while downloading"
    }
}
