// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/VoiceCommandSessions.kt
// Purpose: When voice commands listen (plan item 38): on screen, and screen-off sessions with their service.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.commands.ListeningNotice
import io.github.skm0078.audora.speech.kws.ListenEvents
import io.github.skm0078.audora.speech.kws.ListenFailure
import io.github.skm0078.audora.speech.kws.VoiceCommand
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

/** The listener, as the sessions see it: listens until cancelled, reporting to its events. */
typealias Listen = suspend (ListenEvents) -> Outcome<Unit, ListenFailure>

/** What a screen-off session needs from the microphone foreground service. */
interface ListeningService {
    /** Grows each time listening must end from the service: Stop, a swipe, or Android refusing it. */
    val endings: StateFlow<Int>

    fun start()

    fun end(notice: ListeningNotice)

    fun silenced(silenced: Boolean)
}

/**
 * Hears commands until cancelled or the listener stops (its outcome), handing each to [onCommand] on the caller's
 * thread, in the order heard.
 */
suspend fun hear(
    listen: Listen,
    onSilenced: (Boolean) -> Unit = {},
    onCommand: (VoiceCommand) -> Unit,
): Outcome<Unit, ListenFailure> = coroutineScope {
    val heard = Channel<VoiceCommand>(Channel.UNLIMITED)
    val outcome = async {
        listen(ListenEvents(onSilenced) { heard.trySend(it) }).also {
            if (it is Outcome.Failure) AudoraLog.w(TAG, "Voice commands not listening: ${it.error}")
            heard.close()
        }
    }
    for (command in heard) onCommand(command)
    outcome.await()
}

/** Listens while [visible], returning once it is not (at once when it is not now). */
suspend fun listenWhileVisible(visible: Flow<Boolean>, listen: Listen, onCommand: (VoiceCommand) -> Unit) {
    if (!visible.first()) return
    coroutineScope {
        val listening = launch { hear(listen, onCommand = onCommand) }
        visible.first { !it }
        listening.cancel()
    }
}

/**
 * Returns once reading has stayed paused for [idleMs] (counted from the pause, ADR-051) and the reader is not
 * [visible]: a pause on screen that runs past [idleMs] ends listening as soon as the reader is hidden.
 */
@OptIn(ExperimentalCoroutinesApi::class) // transformLatest: stable in behaviour since 1.3, still marked
suspend fun awaitIdle(reading: Flow<Boolean>, visible: Flow<Boolean>, idleMs: Long) {
    reading.distinctUntilChanged().transformLatest { playing ->
        if (!playing) {
            delay(idleMs)
            visible.first { !it }
            emit(Unit)
        }
    }.first()
}

/**
 * Screen-off listening: a session starts each time the reader is shown, with [service] keeping the microphone usable
 * in the background (Android 14 starts it only while the app is visible), and goes on with the screen off. It ends
 * when Stop is tapped, when the listener stops, or by [awaitIdle]; after one ends while the reader is shown, it
 * listens on screen until hidden, and the next session starts when the reader is shown again.
 *
 * @param inBackground told true while a session runs, so the reader keeps its player connected when hidden.
 */
class ScreenOffListening(
    private val service: ListeningService,
    private val listen: Listen,
    private val visible: Flow<Boolean>,
    private val reading: Flow<Boolean>,
    private val idleMs: Long,
    private val inBackground: (Boolean) -> Unit,
) {
    /** Until cancelled. */
    suspend fun run(onCommand: (VoiceCommand) -> Unit) {
        while (true) {
            visible.first { it }
            session(onCommand)
            listenWhileVisible(visible, listen, onCommand)
        }
    }

    /** One session; the service is ended (with a notice when the user should know) however it ends. */
    suspend fun session(onCommand: (VoiceCommand) -> Unit) {
        val endingsBefore = service.endings.value
        var notice = ListeningNotice.NONE
        inBackground(true)
        service.start()
        try {
            notice = coroutineScope {
                val done = CompletableDeferred<ListeningNotice>()
                val waits = listOf(
                    launch { done.complete(listened(onCommand)) },
                    launch {
                        service.endings.first { it > endingsBefore }
                        done.complete(ListeningNotice.NONE)
                    },
                    launch {
                        awaitIdle(reading, visible, idleMs)
                        done.complete(ListeningNotice.IDLE)
                    },
                )
                done.await().also { waits.forEach { it.cancel() } }
            }
            AudoraLog.i(TAG, "Screen-off listening ended (notice $notice)")
        } finally {
            service.end(notice)
            inBackground(false)
        }
    }

    /** Listens until the listener stops; the user is told only when that happened with the reader hidden. */
    private suspend fun listened(onCommand: (VoiceCommand) -> Unit): ListeningNotice {
        val outcome = hear(listen, service::silenced, onCommand)
        val hidden = !visible.first()
        return if (outcome is Outcome.Failure && hidden) ListeningNotice.MICROPHONE_LOST else ListeningNotice.NONE
    }
}

/**
 * A controller left connected keeps the playback service bound, so the reader connects only while it is [visible],
 * except while a screen-off voice-command session runs ([inBackground]): its commands act on this player, and a
 * released one reports itself unavailable, which would drop every command and stop the idle clock's reading state.
 */
fun playerWanted(visible: Flow<Boolean>, inBackground: Flow<Boolean>): Flow<Boolean> =
    combine(visible, inBackground) { shown, listening -> shown || listening }.distinctUntilChanged()

private const val TAG = "Audora.VoiceCommands"
