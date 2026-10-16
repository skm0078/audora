// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/voices/VoiceDownloadJob.kt
// Purpose: One background attempt at downloading and installing a catalog voice, and whether to try again.

package io.github.skm0078.audora.speech.voices

import io.github.skm0078.audora.core.config.VoiceDownloadConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome

/** What the WorkManager worker should report. */
sealed interface JobResult {
    data object Installed : JobResult

    /** Try again later; the partial file is kept, so the next attempt resumes. */
    data class Retry(val reason: String) : JobResult

    data class Failed(val message: String) : JobResult
}

/**
 * The worker's logic without WorkManager, so it is JVM-tested. A stop (cancel, or Android ending the job after its
 * time limit) and network trouble keep the bytes received; a corrupt file or a full phone ends the download.
 *
 * @param onInstalled called after a voice is swapped in, so the voice list and the engine see it.
 */
class VoiceDownloadJob(
    private val catalog: VoiceCatalog,
    private val files: VoiceFiles,
    private val downloader: ResumableDownloader,
    private val installer: VoiceArchiveInstaller,
    private val config: VoiceDownloadConfig,
    private val onInstalled: () -> Unit,
) {
    /** @param attempt WorkManager's run attempt count, 0 for the first run. */
    fun run(id: String, attempt: Int, onProgress: (Long, Long) -> Unit, keepGoing: () -> Boolean): JobResult {
        val voice = catalog.find(id) ?: return JobResult.Failed("$id is not in this app's voice list")
        AudoraLog.i(TAG, "Download voice=$id attempt=${attempt + 1}/${config.maxAttempts}")
        return when (val downloaded = downloader.download(voice, files.partialDownload(voice), onProgress, keepGoing)) {
            is Outcome.Success -> when (val installed = installer.install(downloaded.value, voice)) {
                is Outcome.Success -> JobResult.Installed.also { onInstalled() }
                is Outcome.Failure -> JobResult.Failed(installed.error)
            }

            is Outcome.Failure -> failed(id, attempt, downloaded.error)
        }
    }

    private fun failed(id: String, attempt: Int, error: DownloadError): JobResult {
        val lastAttempt = attempt + 1 >= config.maxAttempts
        val result = when {
            error == DownloadError.Stopped -> JobResult.Retry(error.message)
            !error.retryable -> JobResult.Failed(error.message)
            lastAttempt -> JobResult.Failed("${error.message} (gave up after ${config.maxAttempts} tries)")
            else -> JobResult.Retry(error.message)
        }
        when (result) {
            is JobResult.Failed -> AudoraLog.e(TAG, "Download voice=$id failed: ${result.message}")
            else -> AudoraLog.w(TAG, "Download voice=$id will retry: ${error.message}")
        }
        return result
    }

    private companion object {
        const val TAG = "Speech.VoiceJob"
    }
}
