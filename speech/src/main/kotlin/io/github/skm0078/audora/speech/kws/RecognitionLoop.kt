// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/kws/RecognitionLoop.kt
// Purpose: Keeps the phone's speech recogniser listening for voice commands, one short session after another, and
//          acts on each "ListenLock, <command>" once (plan item 38, ADR 2026-11-02-voice-commands-system-recogniser).

package io.github.skm0078.audora.speech.kws

import io.github.skm0078.audora.core.config.VoiceCommandConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select

/** How one recognition session ended, as the loop needs to know it. */
enum class SessionEnd {
    /** Speech was heard (commands in it, if any, were already reported). */
    HEARD,

    /** Nothing was said before the recogniser gave up; normal in a quiet room. */
    NOTHING,

    /** Another client or app holds the recogniser or the microphone. */
    BUSY,

    /** The microphone permission is missing. */
    NO_PERMISSION,

    /** The English speech pack is not on the phone. */
    LANGUAGE_MISSING,

    /** No on-device recogniser on this phone. */
    NOT_AVAILABLE,

    /** Anything else the recogniser reported; retried after a pause. */
    FAILED,
}

/** The phone's recogniser, one session at a time, as [RecognitionLoop] sees it. */
interface CommandRecognizer {
    /**
     * Listens once, until the recogniser ends the session or the caller is cancelled (which must stop it at once).
     * [onReady] is called when the microphone is listening, and [onText] with every partial and final text.
     */
    suspend fun session(onReady: () -> Unit, onText: (String) -> Unit): SessionEnd
}

/**
 * Sessions back to back while no other feature holds the microphone ([VoiceCommandPause]); a hold stops the session
 * at once and listening starts again after it. Each command in the text acts once ([CommandPhrases.Found] counts the
 * ones already acted on in a growing partial result), never while the book says the app's name ([NarratorEcho]), and
 * never twice for one utterance ([CommandRepeats]). Pure Kotlin: [CommandListener] supplies the Android recogniser.
 */
internal class RecognitionLoop(
    private val recognizer: CommandRecognizer,
    private val config: VoiceCommandConfig,
    private val echo: NarratorEcho,
    private val pause: VoiceCommandPause,
    private val clock: () -> Long,
) {
    private val repeats = CommandRepeats(config.repeatGapMs)

    /** Listens until cancelled; returns only when listening cannot go on, with why. */
    suspend fun run(events: ListenEvents): Outcome<Unit, ListenFailure> {
        val listening = Listening(events)
        try {
            var failures = 0
            while (true) {
                if (pause.isPaused) listening.set(false)
                pause.awaitFree()
                if (!pause.claimMicrophone()) continue
                val end = try {
                    sessionUnlessHeld(listening, events)
                } finally {
                    pause.microphoneClosed()
                }
                stopFor(end)?.let { return Outcome.Failure(it) }
                failures = if (end == SessionEnd.FAILED || end == SessionEnd.BUSY) failures + 1 else 0
                if (failures >= config.maxFailuresInARow) {
                    AudoraLog.w(TAG, "Recogniser failed $failures times in a row ($end); stopped listening")
                    return Outcome.Failure(
                        if (end == SessionEnd.BUSY) ListenFailure.MICROPHONE_BUSY else ListenFailure.MICROPHONE_LOST,
                    )
                }
                if (failures > 0) delay(config.failureRetryMs)
            }
        } finally {
            listening.set(false)
        }
    }

    /** One session, stopped at once (as [SessionEnd.NOTHING]) when another feature asks for the microphone. */
    private suspend fun sessionUnlessHeld(listening: Listening, events: ListenEvents): SessionEnd = coroutineScope {
        var acted = 0
        val session = async {
            recognizer.session(onReady = { listening.set(true) }) { text ->
                val found = CommandPhrases.find(text)
                found.drop(acted).forEach { act(it.command, events) }
                acted = maxOf(acted, found.size)
            }
        }
        val held = async { pause.awaitHeld() }
        select {
            session.onAwait { it }
            held.onAwait { SessionEnd.NOTHING }
        }.also {
            session.cancel()
            held.cancel()
        }
    }

    /** The failure that ends listening for good, or null when another session should follow. */
    private fun stopFor(end: SessionEnd): ListenFailure? = when (end) {
        SessionEnd.NO_PERMISSION -> ListenFailure.NO_PERMISSION
        SessionEnd.LANGUAGE_MISSING -> ListenFailure.LANGUAGE_MISSING
        SessionEnd.NOT_AVAILABLE -> ListenFailure.NOT_AVAILABLE
        SessionEnd.HEARD, SessionEnd.NOTHING, SessionEnd.BUSY, SessionEnd.FAILED -> null
    }

    private fun act(heard: VoiceCommand, events: ListenEvents) {
        val now = clock()
        when {
            echo.suppresses(now) -> AudoraLog.i(TAG, "Ignored ${heard.name}: the book itself says the app's name")

            repeats.accept(heard, now) -> events.onCommand(heard).also {
                AudoraLog.i(TAG, "Voice command ${heard.name}")
            }
        }
    }

    /** "Listening" as the reader shows it: on from the first session ready, off only when listening really stops. */
    private class Listening(private val events: ListenEvents) {
        private var on = false

        fun set(now: Boolean) {
            if (now == on) return
            on = now
            AudoraLog.i(TAG, if (now) "Listening" else "Not listening")
            events.onMicrophone(now)
        }
    }

    private companion object {
        const val TAG = "Speech.Commands"
    }
}
