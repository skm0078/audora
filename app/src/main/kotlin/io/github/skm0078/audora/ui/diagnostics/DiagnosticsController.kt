// File: app/src/main/kotlin/io/github/skm0078/audora/ui/diagnostics/DiagnosticsController.kt
// Purpose: UI state for Share logs and the "Reading stopped unexpectedly" prompt; file work runs off the main thread.

package io.github.skm0078.audora.ui.diagnostics

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.skm0078.audora.core.diagnostics.ReadingSession
import io.github.skm0078.audora.core.diagnostics.ReadingSessionTracker
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.diagnostics.LogSharer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Plain state holder (no Compose UI here), like the PDF check's controller. */
class DiagnosticsController(
    private val tracker: ReadingSessionTracker,
    private val logSharer: LogSharer,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /** A read the previous process was doing when it died; drives the resume prompt. */
    var unfinishedSession: ReadingSession? by mutableStateOf(null)
        private set

    var shareState: ShareLogsState by mutableStateOf(ShareLogsState.Idle)
        private set

    fun checkUnfinishedSession() {
        scope.launch { unfinishedSession = withContext(io) { tracker.unfinishedSession() } }
    }

    // TODO(P1-06): start playback at the session's ResumeAnchor; until then Resume only closes the prompt.
    fun resumeUnfinished() = resolve(resumed = true)

    fun dismissUnfinished() = resolve(resumed = false)

    /** [startShareSheet] receives the chooser intent; the caller starts it from an Activity context. */
    fun shareLogs(startShareSheet: (Intent) -> Unit) {
        if (shareState == ShareLogsState.Preparing) return
        shareState = ShareLogsState.Preparing
        scope.launch {
            when (val prepared = withContext(io) { logSharer.prepareShareIntent() }) {
                is Outcome.Success -> {
                    shareState = ShareLogsState.Idle
                    startShareSheet(prepared.value)
                }

                is Outcome.Failure -> shareState = ShareLogsState.Failed
            }
        }
    }

    /**
     * Debug builds only: marks a reading session as playing, without audio, so the prompt and the exit
     * logging can be checked on a phone before the playback service (P1-04) exists.
     */
    fun simulateReadingSession() {
        scope.launch {
            withContext(io) { tracker.onPlaybackActive(SIMULATED_BOOK_ID, SIMULATED_PAGE) }
            AudoraLog.i(TAG, "Simulated reading session started (debug)")
        }
    }

    private fun resolve(resumed: Boolean) {
        unfinishedSession = null
        scope.launch { withContext(io) { tracker.resolveUnfinished(resumed) } }
    }

    private companion object {
        const val TAG = "Diagnostics"
        const val SIMULATED_BOOK_ID = "debug-simulated"
        const val SIMULATED_PAGE = 13
    }
}

enum class ShareLogsState { Idle, Preparing, Failed }
