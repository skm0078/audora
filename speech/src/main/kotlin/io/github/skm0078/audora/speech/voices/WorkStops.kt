// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/voices/WorkStops.kt
// Purpose: Cancels a model download and waits until its worker has really stopped, before the model is deleted.

package io.github.skm0078.audora.speech.voices

import androidx.work.WorkManager
import io.github.skm0078.audora.core.logging.AudoraLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * `cancelUniqueWork` returns before the worker stops: a worker that just finished its download could still move a
 * model into the folder being deleted, or start a new partial file there. This waits until no run of [name] is
 * left unfinished, at most [timeoutMs] (logged when it gives up; the delete then goes ahead).
 */
suspend fun WorkManager.cancelAndAwait(name: String, timeoutMs: Long) {
    cancelUniqueWork(name)
    val stopped = withTimeoutOrNull(timeoutMs) {
        getWorkInfosForUniqueWorkFlow(name).first { runs -> runs.all { it.state.isFinished } }
    }
    if (stopped == null) AudoraLog.w(TAG, "Download $name still running after ${timeoutMs}ms; deleting anyway")
}

private const val TAG = "Speech.Downloads"
