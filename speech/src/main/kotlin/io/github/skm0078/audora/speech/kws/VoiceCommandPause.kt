// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/kws/VoiceCommandPause.kt
// Purpose: Lets another feature take the microphone (reading tutor, voice notes): voice commands pause meanwhile.

package io.github.skm0078.audora.speech.kws

import io.github.skm0078.audora.core.logging.AudoraLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Android gives a recording to one app's microphone reader at a time in practice, and two readers in one app would
 * hear each other's prompts. A feature that records calls [hold] before it opens the microphone and closes the
 * [Hold] when done; [CommandListener] lets the microphone go within a chunk and listens again once no hold is left.
 * Holds nest: two features holding at once both close theirs before listening resumes. With no listener attached
 * (voice commands off, or not built) [hold] returns at once.
 *
 * Process-wide ([shared]), like [NarratorEcho]. Any thread. The reason is logged; it names a feature, never content.
 */
class VoiceCommandPause(private val releaseWaitMs: Long = DEFAULT_RELEASE_WAIT_MS) {
    private val holds = MutableStateFlow<Set<Hold>>(emptySet())
    private val microphoneOpen = MutableStateFlow(false)

    /** True while any feature holds the microphone. */
    val isPaused: Boolean get() = holds.value.isNotEmpty()

    /**
     * Pauses voice commands for [reason] until the returned hold is closed, and returns once the listener has let the
     * microphone go (at once when it has none; at most `releaseWaitMs`, logged when it has not, and the caller goes
     * ahead).
     */
    suspend fun hold(reason: String): Hold {
        val hold = Hold(reason)
        holds.update { it + hold }
        AudoraLog.i(TAG, "Voice commands paused for $reason")
        val released = withTimeoutOrNull(releaseWaitMs) { microphoneOpen.first { open -> !open } }
        if (released == null) AudoraLog.w(TAG, "Voice commands still had the microphone after ${releaseWaitMs}ms")
        return hold
    }

    /** One feature's pause. Closing it again does nothing. */
    inner class Hold internal constructor(private val reason: String) : AutoCloseable {
        override fun close() {
            if (this in holds.getAndUpdate { it - this }) AudoraLog.i(TAG, "Voice commands resumed after $reason")
        }
    }

    /** Suspends while any hold is open. */
    internal suspend fun awaitFree() {
        holds.first { it.isEmpty() }
    }

    /** For the listener: it opened ([open] true) or closed the microphone. */
    internal fun microphone(open: Boolean) {
        microphoneOpen.value = open
    }

    companion object {
        private const val TAG = "Speech.CommandPause"

        /** A chunk being read (100 ms) plus closing the recorder, with room to spare. */
        const val DEFAULT_RELEASE_WAIT_MS = 1_000L

        val shared = VoiceCommandPause()
    }
}
