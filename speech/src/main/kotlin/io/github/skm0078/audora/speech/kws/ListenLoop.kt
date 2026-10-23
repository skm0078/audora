// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/kws/ListenLoop.kt
// Purpose: The listener's loop (plan item 38): microphone spells between holds, the energy gate, commands heard.

package io.github.skm0078.audora.speech.kws

import io.github.skm0078.audora.core.config.VoiceCommandConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import kotlinx.coroutines.yield

/** Why listening could not start or had to stop; each is a state Settings or the reader can explain. */
enum class ListenFailure { NO_PERMISSION, NO_MODEL, MICROPHONE_BUSY, MICROPHONE_LOST }

/** A started microphone, read by one loop and closed once. */
interface CommandMicrophone : AutoCloseable {
    /** Reads up to `pcm.size` samples, blocking for about a chunk: the count read, or negative on an error. */
    fun read(pcm: ShortArray): Int

    /** True while Android hands this app silence instead of sound (a call, the system's microphone toggle). */
    fun silenced(): Boolean
}

/** The spotter, as the loop sees it: [accept] feeds a chunk and returns the command that ended in it, if any. */
fun interface KeywordDecoder {
    fun accept(samples: FloatArray): VoiceCommand?
}

/** Where the loop reports: each command acted on, and Android silencing or un-silencing the capture. */
class ListenEvents(val onSilenced: (Boolean) -> Unit = {}, val onCommand: (VoiceCommand) -> Unit)

/**
 * Opens the microphone whenever no feature holds it ([VoiceCommandPause]), claiming it atomically first so a hold
 * never meets a microphone opened behind its back, and lets it go within a chunk when one does. A microphone that
 * will not open during a hold is tried again after it. Pure Kotlin: [CommandListener] supplies the Android parts.
 */
internal class ListenLoop(
    private val decoder: KeywordDecoder,
    private val openMicrophone: () -> CommandMicrophone?,
    private val config: VoiceCommandConfig,
    private val echo: NarratorEcho,
    private val pause: VoiceCommandPause,
    private val clock: () -> Long,
) {
    private val repeats = CommandRepeats(config.repeatGapMs)

    /** Listens until cancelled; returns only when the microphone cannot be opened or stops working. */
    suspend fun run(events: ListenEvents): Outcome<Unit, ListenFailure> {
        var outcome: Outcome<Unit, ListenFailure>? = null
        while (outcome == null) {
            pause.awaitFree()
            if (pause.claimMicrophone()) outcome = spell(events)
        }
        return outcome
    }

    /** One claimed spell: null when the microphone was let go for a hold, else why listening ended. */
    private suspend fun spell(events: ListenEvents): Outcome<Unit, ListenFailure>? {
        try {
            val microphone = openMicrophone()
            return when {
                microphone != null -> microphone.use { readLoop(it, events) }
                pause.isPaused -> null.also { AudoraLog.i(TAG, "Microphone busy during a pause; trying again after") }
                else -> Outcome.Failure(ListenFailure.MICROPHONE_BUSY)
            }
        } finally {
            // Also when close() threw: the claim must end, or every later hold would wait its full time.
            pause.microphoneClosed()
        }
    }

    /** Reads until a hold comes (null) or the microphone fails. */
    private suspend fun readLoop(microphone: CommandMicrophone, events: ListenEvents): Outcome<Unit, ListenFailure>? {
        val pcm = ShortArray(config.chunkSamples)
        val gate = EnergyGate(config)
        val silence = SilenceWatch(microphone, events)
        var read = 0
        while (read >= 0 && !pause.isPaused) {
            yield()
            read = microphone.read(pcm)
            val silenced = silence.check(clock())
            if (read > 0 && !silenced) gate.pass(floats(pcm, read)).forEach { act(decoder.accept(it), events) }
        }
        silence.end()
        if (read >= 0) AudoraLog.i(TAG, "Microphone let go for a pause")
        if (read < 0) AudoraLog.w(TAG, "Microphone read failed with code $read; stopped listening")
        return if (read < 0) Outcome.Failure(ListenFailure.MICROPHONE_LOST) else null
    }

    private fun act(heard: VoiceCommand?, events: ListenEvents) {
        val now = clock()
        when {
            heard == null -> Unit

            echo.suppresses(now) -> AudoraLog.i(TAG, "Ignored ${heard.name}: the book itself says the app's name")

            repeats.accept(heard, now) -> events.onCommand(heard).also {
                AudoraLog.i(TAG, "Voice command ${heard.name}")
            }
        }
    }

    /** Asks Android at most every `silenceCheckMs` whether the capture is silenced, and reports each change. */
    private inner class SilenceWatch(private val microphone: CommandMicrophone, private val events: ListenEvents) {
        private var silenced = false
        private var checkedAtMs = Long.MIN_VALUE / 2

        fun check(nowMs: Long): Boolean {
            if (nowMs - checkedAtMs >= config.silenceCheckMs) {
                checkedAtMs = nowMs
                val now = microphone.silenced()
                if (now != silenced) report(now)
            }
            return silenced
        }

        fun end() {
            if (silenced) report(false)
        }

        private fun report(now: Boolean) {
            silenced = now
            AudoraLog.i(TAG, if (now) "Android silenced the microphone" else "Microphone no longer silenced")
            events.onSilenced(now)
        }
    }

    private companion object {
        const val TAG = "Speech.Commands"
        const val PCM_FULL_SCALE = 32_768f

        /** A fresh array each chunk: the gate may keep it as pre-roll. */
        fun floats(pcm: ShortArray, count: Int): FloatArray = FloatArray(count) { pcm[it] / PCM_FULL_SCALE }
    }
}
