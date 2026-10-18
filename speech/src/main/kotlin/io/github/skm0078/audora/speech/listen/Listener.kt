// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/listen/Listener.kt
// Purpose: What the reading tutor needs from a speech recogniser: microphone samples in, heard stretches out (67).

package io.github.skm0078.audora.speech.listen

/** A stretch of speech: [text] as recognised so far; [final] once a pause has ended it and it will not change. */
data class Heard(val text: String, val final: Boolean)

/**
 * Takes 16 kHz mono samples in [-1, 1] and reports what it heard. A word-by-word listener reports a growing stretch
 * on every change and a final one at each pause; a phrase-by-phrase one reports only final stretches. One thread at a
 * time; [close] frees the native model. What is heard is the reader's speech: never logged.
 */
interface Listener : AutoCloseable {
    /** Feeds [samples]; returns what changed (often nothing). */
    fun accept(samples: FloatArray): List<Heard>

    /** The microphone stopped: finishes the stretch under way, if any. */
    fun finish(): List<Heard>

    override fun close()
}
