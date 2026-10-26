// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/brain/StoryBrainDownloadJob.kt
// Purpose: One background attempt at downloading the story brain's parts, joining and installing them (ADR
//          story-brain).

package io.github.skm0078.audora.speech.brain

import io.github.skm0078.audora.core.config.VoiceDownloadConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.voices.DownloadError
import io.github.skm0078.audora.speech.voices.InstallGate
import io.github.skm0078.audora.speech.voices.JobResult
import io.github.skm0078.audora.speech.voices.ResumableDownloader

/**
 * Parts download one after another, each resumable and SHA-256-checked by the voice downloader (ADR-022), with the
 * voices' retry rules: a stop or network trouble keeps the bytes and retries, a corrupt part or a full phone ends the
 * download. Progress counts the whole model, joined parts included.
 *
 * @param gate the brain's install lock: a model deleted while it downloaded is not installed after all.
 */
class StoryBrainDownloadJob(
    private val model: StoryBrainModel,
    private val files: StoryBrainFiles,
    private val downloader: ResumableDownloader,
    private val config: VoiceDownloadConfig,
    private val gate: InstallGate = InstallGate(),
    private val onInstalled: () -> Unit,
) {
    /** @param attempt WorkManager's run attempt count, 0 for the first run. */
    fun run(attempt: Int, onProgress: (Long, Long) -> Unit, keepGoing: () -> Boolean): JobResult {
        if (files.isInstalled()) return JobResult.Installed
        val ticket = gate.ticket()
        AudoraLog.i(TAG, "Download story brain=${model.id} attempt=${attempt + 1}/${config.maxAttempts}")
        var result: JobResult? = null
        while (result == null) {
            val part = files.nextPart()
            result = if (part == null) install(ticket, keepGoing) else fetch(part, attempt, onProgress, keepGoing)
        }
        return result
    }

    /** Downloads and joins one part; null to go on with the next. */
    private fun fetch(
        part: StoryBrainModel.Part,
        attempt: Int,
        onProgress: (Long, Long) -> Unit,
        keepGoing: () -> Boolean,
    ): JobResult? {
        val before = files.joined.length()
        val progress = { bytes: Long, _: Long -> onProgress(before + bytes, model.sizeBytes) }
        val got = downloader.download(part, files.partialDownload(part), progress, keepGoing)
        val joined = (got as? Outcome.Success)?.let { files.join(it.value) }
        return when {
            got is Outcome.Failure -> failed(attempt, got.error)
            joined is Outcome.Failure -> JobResult.Failed(joined.error)
            !keepGoing() -> JobResult.Retry(DownloadError.Stopped.message)
            else -> null.also { AudoraLog.i(TAG, "Story brain ${part.id} joined") }
        }
    }

    private fun install(ticket: Long, keepGoing: () -> Boolean): JobResult {
        val installed = if (keepGoing()) gate.install(ticket) { files.install() } else null
        return when (installed) {
            null -> JobResult.Failed(DELETED).also { AudoraLog.i(TAG, "Story brain not installed: $DELETED") }
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
            is JobResult.Failed -> AudoraLog.e(TAG, "Story brain failed: ${result.message}")
            else -> AudoraLog.w(TAG, "Story brain will retry: ${error.message}")
        }
        return result
    }

    private companion object {
        const val TAG = "Speech.BrainJob"
        const val DELETED = "stopped or deleted while downloading"
    }
}
