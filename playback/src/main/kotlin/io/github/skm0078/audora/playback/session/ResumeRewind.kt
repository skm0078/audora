// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/session/ResumeRewind.kt
// Purpose: Decides how many sentences to step back when reading resumes after a pause (ADR-033).

package io.github.skm0078.audora.playback.session

import io.github.skm0078.audora.core.config.ResumeRewindConfig

/** Where resuming starts, relative to the sentence that was playing when the pause began. */
enum class RewindStep { NONE, RESTART_SENTENCE, PREVIOUS_SENTENCE }

/**
 * Remembers when the listener paused and, on resume, how far back to start. [clock] is monotonic
 * (elapsedRealtime) so a pause across deep sleep counts in full. Main thread only.
 */
class ResumeRewind(private val config: ResumeRewindConfig, private val clock: () -> Long) {
    private var pausedAt: Long? = null
    private var pausedAtSentenceEnd = false

    /** [atSentenceEnd]: the player stopped after a sentence (sleep timer), so nothing was cut off. */
    fun onPaused(atSentenceEnd: Boolean) {
        pausedAt = clock()
        pausedAtSentenceEnd = atSentenceEnd
    }

    /** Playback resumed: the step to take, once. A new position (seek, new book, restyle) calls [forget]. */
    fun onResumed(): RewindStep {
        val start = pausedAt ?: return RewindStep.NONE
        pausedAt = null
        val paused = clock() - start
        val step = when {
            paused >= config.previousSentenceAfterMs -> RewindStep.PREVIOUS_SENTENCE
            paused >= config.restartSentenceAfterMs -> RewindStep.RESTART_SENTENCE
            else -> RewindStep.NONE
        }
        // After a sentence end nothing was cut off, so the step is one sentence smaller.
        return if (pausedAtSentenceEnd) step.smaller() else step
    }

    fun forget() {
        pausedAt = null
    }

    private fun RewindStep.smaller(): RewindStep = when (this) {
        RewindStep.PREVIOUS_SENTENCE -> RewindStep.RESTART_SENTENCE
        RewindStep.RESTART_SENTENCE, RewindStep.NONE -> RewindStep.NONE
    }
}
