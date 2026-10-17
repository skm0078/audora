// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/ResumeRewindConfig.kt
// Purpose: How far reading steps back when the listener resumes after a pause (ADR-033).

package io.github.skm0078.audora.core.config

/**
 * Resuming after a pause steps back by whole sentences, never by seconds: a cut mid-word is what makes a
 * rewind hard to follow, and a sentence is the unit the player already holds.
 *
 * @property restartSentenceAfterMs a pause at least this long restarts the sentence that was cut off.
 *   Shorter pauses (answering a quick question) carry on mid-sentence.
 * @property previousSentenceAfterMs a pause at least this long also replays the sentence before it, so the
 *   listener has the thread again after a longer break.
 */
data class ResumeRewindConfig(val restartSentenceAfterMs: Long = 20_000, val previousSentenceAfterMs: Long = 300_000) {
    init {
        require(restartSentenceAfterMs >= 0) { "restartSentenceAfterMs must be >= 0, was $restartSentenceAfterMs" }
        require(previousSentenceAfterMs >= restartSentenceAfterMs) {
            "previousSentenceAfterMs ($previousSentenceAfterMs) must be >= restartSentenceAfterMs " +
                "($restartSentenceAfterMs)"
        }
    }
}
