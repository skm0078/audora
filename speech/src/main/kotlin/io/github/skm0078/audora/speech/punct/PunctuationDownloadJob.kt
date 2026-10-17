// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/punct/PunctuationDownloadJob.kt
// Purpose: One background attempt at downloading and installing the punctuation model, and whether to try again.

package io.github.skm0078.audora.speech.punct

import io.github.skm0078.audora.core.config.VoiceDownloadConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.voices.DownloadError
import io.github.skm0078.audora.speech.voices.JobResult
import io.github.skm0078.audora.speech.voices.ResumableDownloader

/**
 * Resumable and SHA-256-checked by the voice downloader (ADR-022), with the voices' retry rules: a stop or network
 * trouble keeps the bytes and retries, a corrupt file or a full phone ends the download.
 *
 * @param onInstalled called once the model is in place, so the next run-on uses it.
 */
class PunctuationDownloadJob(
    private val model: PunctuationModel,
    private val files: PunctuationFiles,
    private val downloader: ResumableDownloader,
    private val config: VoiceDownloadConfig,
    private val onInstalled: () -> Unit,
) {
    /** @param attempt WorkManager's run attempt count, 0 for the first run. */
    fun run(attempt: Int, onProgress: (Long, Long) -> Unit, keepGoing: () -> Boolean): JobResult {
        if (files.isInstalled()) return JobResult.Installed
        AudoraLog.i(TAG, "Download punctuation model=${model.id} attempt=${attempt + 1}/${config.maxAttempts}")
        return when (val downloaded = downloader.download(model, files.partialDownload(), onProgress, keepGoing)) {
            is Outcome.Failure -> failed(attempt, downloaded.error)

            is Outcome.Success -> when (val installed = files.install(downloaded.value)) {
                is Outcome.Failure -> JobResult.Failed(installed.error)
                is Outcome.Success -> JobResult.Installed.also { onInstalled() }
            }
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
            is JobResult.Failed -> AudoraLog.e(TAG, "Punctuation model ${model.id} failed: ${result.message}")
            else -> AudoraLog.w(TAG, "Punctuation model ${model.id} will retry: ${error.message}")
        }
        return result
    }

    private companion object {
        const val TAG = "Speech.PunctJob"
    }
}
