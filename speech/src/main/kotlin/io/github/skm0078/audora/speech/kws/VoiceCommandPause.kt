// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/kws/VoiceCommandPause.kt
// Purpose: Lets another feature take the microphone (reading tutor, voice notes): voice commands pause meanwhile.

package io.github.skm0078.audora.speech.kws

import io.github.skm0078.audora.core.logging.AudoraLog
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Android gives a recording to one app's microphone reader at a time in practice, and two readers in one app would
 * hear each other's prompts. A feature that records wraps its recording in [holding] (or calls [hold] and closes the
 * [Hold] when done); [CommandListener] lets the microphone go within a chunk and listens again once no hold is left.
 * Holds nest: two features holding at once both close theirs before listening resumes. With no listener attached
 * (voice commands off, or not built) a hold returns at once.
 *
 * A hold and a listener opening the microphone cannot cross: the listener [claimMicrophone]s atomically, which fails
 * while any hold is open, and a hold waits until every claimed microphone is closed again.
 *
 * Process-wide ([shared]), like [NarratorEcho]. Any thread. The reason is logged; it names a feature, never content.
 */
class VoiceCommandPause(private val releaseWaitMs: Long = DEFAULT_RELEASE_WAIT_MS) {
    /** The open holds, and how many listeners have the microphone open (normally 0 or 1). */
    private data class State(val holds: Set<Hold> = emptySet(), val openMicrophones: Int = 0)

    private val state = MutableStateFlow(State())

    /** True while any feature holds the microphone. */
    val isPaused: Boolean get() = state.value.holds.isNotEmpty()

    /**
     * Pauses voice commands for [reason] until the returned hold is closed, and returns once every listener has let
     * the microphone go (at once when none has it; at most `releaseWaitMs`, logged when one has not, and the caller
     * goes ahead). Cancelled while waiting, it closes the hold before rethrowing, so nothing stays paused.
     */
    suspend fun hold(reason: String): Hold {
        val hold = Hold(reason)
        state.update { it.copy(holds = it.holds + hold) }
        AudoraLog.i(TAG, "Voice commands paused for $reason")
        try {
            val released = withTimeoutOrNull(releaseWaitMs) { state.first { it.openMicrophones == 0 } }
            if (released == null) AudoraLog.w(TAG, "Voice commands still had the microphone after ${releaseWaitMs}ms")
        } catch (e: CancellationException) {
            hold.close()
            throw e
        }
        return hold
    }

    /** Runs [block] with voice commands paused for [reason]; the hold is closed however [block] ends. */
    suspend fun <T> holding(reason: String, block: suspend () -> T): T = hold(reason).use { block() }

    /** One feature's pause. Closing it again does nothing. */
    inner class Hold internal constructor(private val reason: String) : AutoCloseable {
        override fun close() {
            val before = state.getAndUpdate { it.copy(holds = it.holds - this) }
            if (this in before.holds) AudoraLog.i(TAG, "Voice commands resumed after $reason")
        }
    }

    /** Suspends while any hold is open. */
    internal suspend fun awaitFree() {
        state.first { it.holds.isEmpty() }
    }

    /**
     * For a listener about to open the microphone: true, and counted as open, only while no hold is open. A true
     * claim must be ended with [microphoneClosed] whether or not the microphone then opens.
     */
    internal fun claimMicrophone(): Boolean {
        val before = state.getAndUpdate {
            if (it.holds.isEmpty()) it.copy(openMicrophones = it.openMicrophones + 1) else it
        }
        return before.holds.isEmpty()
    }

    /** For a listener: the microphone it claimed is closed (or never opened). */
    internal fun microphoneClosed() {
        state.update { it.copy(openMicrophones = (it.openMicrophones - 1).coerceAtLeast(0)) }
    }

    companion object {
        private const val TAG = "Speech.CommandPause"

        /** A chunk being read (100 ms) plus closing the recorder, with room to spare. */
        const val DEFAULT_RELEASE_WAIT_MS = 1_000L

        val shared = VoiceCommandPause()
    }
}
