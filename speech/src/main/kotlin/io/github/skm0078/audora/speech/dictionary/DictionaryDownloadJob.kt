// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/dictionary/DictionaryDownloadJob.kt
// Purpose: One background attempt at downloading and installing the dictionary, and whether to try again (item 33).

package io.github.skm0078.audora.speech.dictionary

import io.github.skm0078.audora.core.config.VoiceDownloadConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.voices.DownloadError
import io.github.skm0078.audora.speech.voices.JobResult
import io.github.skm0078.audora.speech.voices.ResumableDownloader

/**
 * Resumable and SHA-256-checked by the voice downloader (ADR-022), with the voices' retry rules, like the punctuation
 * model's job: a stop or network trouble keeps the bytes and retries, a corrupt file or a full phone ends it.
 */
class DictionaryDownloadJob(
    private val pin: DictionaryPin,
    private val files: DictionaryFiles,
    private val downloader: ResumableDownloader,
    private val config: VoiceDownloadConfig,
    private val onInstalled: () -> Unit,
) {
    /** @param attempt WorkManager's run attempt count, 0 for the first run. */
    fun run(attempt: Int, onProgress: (Long, Long) -> Unit, keepGoing: () -> Boolean): JobResult {
        if (files.isInstalled()) return JobResult.Installed
        AudoraLog.i(TAG, "Download dictionary=${pin.id} attempt=${attempt + 1}/${config.maxAttempts}")
        files.dropOtherPartials()
        return when (val downloaded = downloader.download(pin, files.partialDownload(), onProgress, keepGoing)) {
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

            // A build's dictionary is published when that build reaches dev; before that the mirror has no file.
            error is DownloadError.Http && error.code == HTTP_NOT_FOUND -> JobResult.Failed(NOT_PUBLISHED)

            !error.retryable -> JobResult.Failed(error.message)

            attempt + 1 >= config.maxAttempts -> JobResult.Failed(
                "${error.message} (gave up after ${config.maxAttempts} tries)",
            )

            else -> JobResult.Retry(error.message)
        }
        when (result) {
            is JobResult.Failed -> AudoraLog.e(TAG, "Dictionary ${pin.id} failed: ${result.message}")
            else -> AudoraLog.w(TAG, "Dictionary ${pin.id} will retry: ${error.message}")
        }
        return result
    }

    private companion object {
        const val TAG = "Speech.DictJob"
        const val HTTP_NOT_FOUND = 404
        const val NOT_PUBLISHED = "this dictionary is not available for download yet; it comes with a later update"
    }
}
