// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/voices/VoiceDownloadWorker.kt
// Purpose: WorkManager worker that runs one VoiceDownloadJob attempt (the only network Audora uses).

package io.github.skm0078.audora.speech.voices

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Not a foreground worker: Android may stop it (about 10 minutes per run, or when the network drops), and WorkManager
 * runs it again later; every run resumes from the bytes on disk, so a stop costs nothing. No notification, no extra
 * permission.
 */
class VoiceDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString(KEY_VOICE_ID)
            ?: return@withContext Result.failure(workDataOf(KEY_MESSAGE to "no voice id"))
        val job = VoiceLibrary.get(applicationContext).downloadJob()
        val result = job.run(
            id = id,
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
        const val KEY_VOICE_ID = "voiceId"
        const val KEY_BYTES = "bytes"
        const val KEY_TOTAL = "total"
        const val KEY_MESSAGE = "message"
    }
}
